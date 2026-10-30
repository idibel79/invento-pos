# Déploiement de test : Supabase + Cloud Run

Mise en place unique, à faire une fois. Ensuite, chaque merge sur `main` publie l'image sur GHCR,
la recopie dans Artifact Registry et la déploie sur Cloud Run (job `deploy` de la CI).

Vue d'ensemble :

- **Supabase** : un simple PostgreSQL (schéma `pos`, rôle dédié).
- **GCP** : un projet, un dépôt Artifact Registry, deux secrets dans Secret Manager, deux comptes
  de service (`github-deploy` pour la CI, `invento-pos-run` pour l'application) et une
  Workload Identity Federation : la CI s'authentifie sans clé JSON.
- **GitHub** : six variables du dépôt, **aucun secret** (les mots de passe restent dans
  Secret Manager). L'image GHCR reste privée : la CI s'y connecte avec son `GITHUB_TOKEN`.

Région conseillée : **Paris** des deux côtés (Cloud Run `europe-west9`, Supabase `eu-west-3`),
pour que chaque requête SQL ne traverse pas l'Europe.

## 1. Supabase

1. Créer un projet Supabase, région *West EU (Paris)*. Noter le mot de passe de la base.
2. *SQL Editor* : créer un rôle dédié à l'application, propriétaire du schéma `pos` (Flyway y
   crée les tables au premier démarrage). Générer un mot de passe fort, sans caractère spécial
   gênant dans une URL (ex. `openssl rand -hex 24`).

   ```sql
   create role invento_pos with login password 'MOT_DE_PASSE_GENERE';
   create schema pos authorization invento_pos;
   ```

3. Bouton *Connect* > *Session pooler* : noter l'hôte (`aws-0-eu-west-3.pooler.supabase.com`
   ou proche) et la référence du projet (`<ref>`, dans l'utilisateur `postgres.<ref>`).
   - Pas la *Direct connection* : elle est en IPv6, que Cloud Run ne joint pas.
   - Pas le *Transaction pooler* (port 6543) : il casse les requêtes préparées JDBC.

Valeurs obtenues :

| | Valeur |
|---|---|
| `POS_DB_URL` | `jdbc:postgresql://<hôte du session pooler>:5432/postgres?sslmode=require` |
| `POS_DB_USER` | `invento_pos.<ref>` |
| mot de passe | celui du rôle `invento_pos` (va dans Secret Manager, pas dans GitHub) |

## 2. Google Cloud

Le plus simple est **Cloud Shell** (icône `>_` en haut de la console GCP) : `gcloud` y est déjà
connecté. Il faut un compte de facturation (le palier gratuit de Cloud Run suffit pour des tests,
le service s'arrête quand personne ne l'utilise).

### 2.1 Projet et API

```bash
export PROJECT_ID=invento-pos-test        # identifiant unique, à adapter
export REGION=europe-west9
export REPO=idibel79/invento-pos

gcloud projects create "$PROJECT_ID"
gcloud billing accounts list               # repérer l'ACCOUNT_ID
gcloud billing projects link "$PROJECT_ID" --billing-account=ACCOUNT_ID
gcloud config set project "$PROJECT_ID"

gcloud services enable run.googleapis.com artifactregistry.googleapis.com \
  secretmanager.googleapis.com iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com
```

### 2.2 Artifact Registry

Cloud Run ne tire pas les images depuis GHCR : la CI les recopie ici.

```bash
gcloud artifacts repositories create invento-pos \
  --repository-format=docker --location="$REGION"

# Facultatif : ne garder que les 5 dernières images (le stockage au-delà de 0,5 Go est payant).
cat > nettoyage.json <<'EOF'
[
  {"name": "garder-5", "action": {"type": "Keep"}, "mostRecentVersions": {"keepCount": 5}},
  {"name": "supprimer-le-reste", "action": {"type": "Delete"}, "condition": {"tagState": "any"}}
]
EOF
gcloud artifacts repositories set-cleanup-policies invento-pos \
  --location="$REGION" --policy=nettoyage.json --no-dry-run
```

### 2.3 Secrets

`read -rs` évite que les valeurs restent dans l'historique du shell.

```bash
read -rsp "Mot de passe du rôle invento_pos : " DB_PW; echo
printf '%s' "$DB_PW" | gcloud secrets create pos-db-password --data-file=-
unset DB_PW

# Clé de signature des sessions : générée, jamais affichée.
openssl rand -base64 48 | tr -d '\n' | gcloud secrets create pos-jwt-secret --data-file=-
```

### 2.4 Compte d'exécution de l'application

Il ne peut que lire ces deux secrets.

```bash
gcloud iam service-accounts create invento-pos-run --display-name="Invento POS (exécution)"
RUN_SA="invento-pos-run@$PROJECT_ID.iam.gserviceaccount.com"

for s in pos-db-password pos-jwt-secret; do
  gcloud secrets add-iam-policy-binding "$s" \
    --member="serviceAccount:$RUN_SA" --role=roles/secretmanager.secretAccessor
done
```

### 2.5 Compte de déploiement (utilisé par la CI)

```bash
gcloud iam service-accounts create github-deploy --display-name="GitHub Actions (déploiement)"
DEPLOY_SA="github-deploy@$PROJECT_ID.iam.gserviceaccount.com"

# Déployer Cloud Run et rendre le service public (--allow-unauthenticated).
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$DEPLOY_SA" --role=roles/run.admin
# Pousser les images dans le dépôt invento-pos uniquement.
gcloud artifacts repositories add-iam-policy-binding invento-pos --location="$REGION" \
  --member="serviceAccount:$DEPLOY_SA" --role=roles/artifactregistry.writer
# Lancer le service sous le compte d'exécution.
gcloud iam service-accounts add-iam-policy-binding "$RUN_SA" \
  --member="serviceAccount:$DEPLOY_SA" --role=roles/iam.serviceAccountUser
```

### 2.6 Workload Identity Federation

GitHub présente un jeton OIDC, Google l'échange contre une identité `github-deploy`. Seuls les
workflows du dépôt `idibel79/invento-pos` sur la branche `main` sont acceptés.

```bash
gcloud iam workload-identity-pools create github \
  --location=global --display-name="GitHub Actions"

gcloud iam workload-identity-pools providers create-oidc invento-pos \
  --location=global --workload-identity-pool=github \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
  --attribute-condition="assertion.repository == '$REPO' && assertion.ref == 'refs/heads/main'"

PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')
gcloud iam service-accounts add-iam-policy-binding "$DEPLOY_SA" \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository/$REPO"
```

Afficher les valeurs à reporter dans GitHub :

```bash
echo "GCP_PROJECT_ID=$PROJECT_ID"
echo "GCP_REGION=$REGION"
echo "GCP_WORKLOAD_IDENTITY_PROVIDER=projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/providers/invento-pos"
echo "GCP_SERVICE_ACCOUNT=$DEPLOY_SA"
```

## 3. GitHub

*Settings* > *Secrets and variables* > *Actions* > onglet **Variables** > *New repository
variable*, six variables (ce ne sont pas des secrets : aucun mot de passe dedans) :

| Variable | Exemple |
|---|---|
| `GCP_PROJECT_ID` | `invento-pos-test` |
| `GCP_REGION` | `europe-west9` |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | `projects/123456789/locations/global/workloadIdentityPools/github/providers/invento-pos` |
| `GCP_SERVICE_ACCOUNT` | `github-deploy@invento-pos-test.iam.gserviceaccount.com` |
| `POS_DB_URL` | `jdbc:postgresql://aws-0-eu-west-3.pooler.supabase.com:5432/postgres?sslmode=require` |
| `POS_DB_USER` | `invento_pos.<ref>` |

L'environnement GitHub `test-en-ligne` est créé automatiquement au premier déploiement.

## 4. Premier déploiement

1. Le prochain merge sur `main` lance le job *Déploiement Cloud Run*. (Une exécution déjà
   terminée ne voit pas les nouvelles variables : il faut un nouveau push sur `main`.)
2. URL du service :

   ```bash
   gcloud run services describe invento-pos --region="$REGION" --format='value(status.url)'
   ```

3. **Compte admin** : au premier démarrage, l'application affiche l'identifiant, le mot de passe
   initial et le code de secours dans la console, donc dans Cloud Logging :

   ```bash
   gcloud logging read 'resource.type="cloud_run_revision" AND textPayload:"Code de secours"' \
     --limit=5 --format='value(textPayload)'
   gcloud logging read 'resource.type="cloud_run_revision" AND textPayload:"Mot de passe initial"' \
     --limit=5 --format='value(textPayload)'
   ```

   Ce code reste dans les logs (30 jours) : après la première connexion, changer le mot de passe
   puis **régénérer le code de secours** depuis l'admin, et noter le nouveau hors ligne.

## Bon à savoir

- **Mise en veille** : sans trafic, Cloud Run arrête l'instance ; la première requête après une
  pause attend le démarrage de Spring Boot (quelques secondes). Hors requête, le CPU est bridé :
  la tâche qui annule les ventes en attente de plus de 5 minutes tourne donc pendant les requêtes
  et au redémarrage, pas en continu. Pour des tests, c'est sans conséquence ; si on veut un
  comportement exact, ajouter `--no-cpu-throttling --min-instances=1` (payant en continu).
- **Connexions** : 2 instances au plus × 5 connexions = 10, sous la limite du pooler Supabase
  gratuit. Monter `--max-instances` seulement avec un plan Supabase plus grand.
- **Réinitialiser le mot de passe admin** en ligne, sans code de secours : lancer l'image en job
  Cloud Run avec l'argument `--reset-admin`, puis lire le mot de passe dans les logs du job.

  ```bash
  gcloud run jobs deploy invento-pos-reset-admin --region="$REGION" \
    --image="$(gcloud run services describe invento-pos --region="$REGION" --format='value(spec.template.spec.containers[0].image)')" \
    --service-account="$RUN_SA" --args=--reset-admin \
    --set-env-vars="POS_DB_URL=...,POS_DB_USER=..." \
    --set-secrets="POS_DB_PASSWORD=pos-db-password:latest,POS_JWT_SECRET=pos-jwt-secret:latest" \
    --execute-now --wait
  ```
