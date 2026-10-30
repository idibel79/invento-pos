# Invento POS

Caisse web pour les produits gérés dans Invento. Backend Java 21 / Spring Boot 3 (`backend/`),
front React + TypeScript / Vite (`frontend/`), servi par Spring Boot dans l'image Docker. Les règles de code et les décisions
d'architecture sont dans [CLAUDE.md](CLAUDE.md).

## Tester une PR dans son navigateur (prévisualisation locale)

Prérequis, une seule fois :
- **Docker Desktop** (lancé), pour PostgreSQL ;
- **JDK 21** (`java -version` doit afficher 21 ou plus) ; Maven n'est pas nécessaire (`mvnw`) ;
- **Node.js 22** et npm ;
- **Git**, avec ce dépôt cloné.

Puis, depuis la racine du dépôt, avec le numéro de la PR :

```powershell
.\scripts\preview.ps1 21          # Windows (PowerShell)
```

```bash
scripts/preview.sh 21              # Linux, macOS, WSL
```

Si Windows refuse d'exécuter le script : `powershell -ExecutionPolicy Bypass -File scripts\preview.ps1 21`.

Le script récupère la PR (HEAD détaché, `git checkout main` pour revenir ; il refuse de démarrer
s'il y a des modifications non commitées), lance PostgreSQL (`docker compose up -d db`), recrée une
base `pos_preview` neuve, démarre l'API avec le profil `demo` puis le front Vite et ouvre
http://localhost:5173. Le front se recharge à chaud ; l'API se relance en relançant le script.
Ctrl+C arrête l'API et le front (la base reste démarrée : `docker compose stop db`). Le journal de
l'API est dans `.preview/backend.log`.

Sans argument, le script prévisualise le code actuel du dossier ; un nom de branche marche aussi.
`-GarderDonnees` (PowerShell) ou `--garder-donnees` (bash) conserve la base de la fois précédente.

Données de démo (profil `demo`, jamais en production) :

| Compte | Rôle | Caisses | PIN |
|---|---|---|---|
| `admin` | administrateur | Caisse 1, Caisse 2 | `0000` |
| `responsable` | responsable | Caisse 1, Caisse 2 | `1234` |
| `caissier` | caissier | Caisse 1 | — |

Mot de passe `demo1234` pour tous, sans changement demandé à la connexion. Huit produits
`DEMO-001` à `DEMO-008` (codes-barres `6118000010019`, `…026`, `…033` … `…088`), dont un presque
épuisé (`DEMO-007`, 2 en stock) et un en rupture (`DEMO-008`).

## Démarrer en développement

Prérequis : JDK 21 et Docker.

```bash
docker compose up -d db                       # PostgreSQL 16 sur localhost:5432
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

- API : http://localhost:8080/api/v1, documentation : http://localhost:8080/swagger-ui.html
- Au premier démarrage, la console affiche **une seule fois** le mot de passe initial de l'admin
  (identifiant `admin`, à changer à la 1re connexion) et son **code de secours**.
- Tests complets (unitaires + intégration sur un vrai PostgreSQL via Testcontainers, Spotless,
  couverture JaCoCo) : `./mvnw verify`. Formatage : `./mvnw spotless:apply`.

## Ce que fait l'étape 1

| Domaine | Endpoints (`/api/v1`) | Accès |
|---|---|---|
| Authentification | `POST /auth/connexion`, `/auth/rafraichir`, `/auth/deconnexion`, `/auth/mot-de-passe`, `/auth/secours`, `/auth/code-secours`, `GET /auth/moi` | public / connecté / admin |
| Catalogue | `GET /produits` (recherche, zone, catégorie, en stock / rupture, pagination), `GET /produits/{id}`, `GET /produits/code-barre/{code}`, `POST`/`PUT /produits`, `GET`/`POST /categories`, `/zones`, `GET /tvas` | lecture : tous, écriture : admin |
| Import Excel | `POST /imports/catalogue/analyse` puis `POST /imports/catalogue` (multipart `fichier`, option `inventaire`) | admin |
| Utilisateurs | `GET`/`POST /utilisateurs`, `PUT /utilisateurs/{id}`, `POST /{id}/mot-de-passe`, `POST /{id}/deverrouiller`, `PUT /{id}/pin` | admin |
| Caisses | `GET /caisses`, `POST`/`PUT /caisses` | lecture : tous, écriture : admin |
| Paramètres | `GET`/`PUT /parametres` (plafonds de remise, boutique, fuseau horaire) | lecture : tous, écriture : admin |
| Journal | `GET /journal` | admin |

Le modèle de données complet du cadrage (ventes, lignes, paiements, promotions, sessions de
caisse, mouvements de stock, mise en attente avec stock réservé) est créé par la migration
Flyway `V1`.

## Ce que fait l'étape 2 : l'écran de vente

| Domaine | Endpoints (`/api/v1`) | Accès |
|---|---|---|
| Session de caisse | `GET /sessions/courante` (204 si aucune), `POST /sessions` (ouverture avec fond de caisse), `POST /sessions/{id}/cloture` (montant compté, écart) | caissier de la session |
| Vente | `POST /ventes/calcul` (totaux, remises, TVA calculés par le serveur), `POST /ventes` (encaissement en espèces) | connecté, session ouverte |
| Mise en attente | `POST /ventes/attente`, `GET /ventes/attente`, `POST /ventes/{id}/reprise`, `POST /ventes/{id}/abandon` | même caisse |
| Autorisation de remise | `GET /autorisations/autorisateurs`, `POST /autorisations/remise` (PIN d'un responsable ou de l'admin) | connecté |

- `POST /ventes` et `POST /ventes/attente` exigent un en-tête `Idempotency-Key` : un double envoi
  renvoie la même vente au lieu d'en créer une seconde.
- Le stock est décrémenté par une mise à jour conditionnelle (`stock - stock_reserve >= quantité`)
  dans la même transaction que la vente, ses lignes, son paiement et les mouvements de stock.
- Une vente en attente réserve son stock 5 minutes au plus (reprise sur la même caisse
  uniquement) ; une tâche planifiée (ShedLock, une seule instance à la fois) l'annule ensuite,
  et la clôture de la caisse annule celles qui restent.
- Une remise au-delà du plafond du caissier demande le PIN d'un responsable (ou de l'admin au-delà
  du plafond responsable) et un motif, enregistrés au journal. 5 PIN faux verrouillent le compte.

## Écrans d'administration

Bouton « Administration » de l'admin (écran d'ouverture de caisse et écran de vente, le ticket en
cours est conservé) : caisses (création, activation), utilisateurs (création, rôle, caisses
attribuées, désactivation, mot de passe temporaire, PIN, déverrouillage), import Excel du catalogue
(analyse puis import, choix de l'inventaire pour un export Invento) et nouveau code de secours.
Ils s'appuient uniquement sur les endpoints ci-dessus.

### Front

```bash
cd frontend
npm ci
npm run dev        # http://localhost:5173, l'API est relayée vers localhost:8080
npm run lint       # ESLint + Prettier
npm test           # Vitest
npm run build      # build de production (dist/)
npm run e2e        # Playwright, contre l'app complète sur localhost:8080 (POS_URL sinon)
```

Le client TypeScript (`src/api/schema.d.ts`) est généré depuis le contrat OpenAPI :
`./mvnw verify` réécrit `frontend/openapi.json`, puis `npm run api` régénère le client. La CI
échoue si l'un des deux n'est pas à jour.

### Import Sobrus et Invento

- Les deux formats sont reconnus par leurs en-têtes (casse et accents ignorés) ; l'encodage cassé
  de Sobrus (`MÃ©dicament`) est réparé comme dans Invento.
- Catégorie séparée de son taux de marge, TVA convertie en taux, prix texte en décimal, zone vide
  = « Non assigné », stock vide = 0, stocks négatifs conservés (produits invendables).
- Export Invento : l'inventaire le plus récent est importé par défaut (l'analyse liste les
  inventaires pour en choisir un autre) ; stock = quantité réelle si l'article a été compté.
- Un produit est repéré par son PID : créé s'il est nouveau, mis à jour sinon. Chaque variation
  de stock laisse un mouvement `IMPORT`.

## Administrateur : mot de passe oublié

1. **Code de secours** (affiché à l'installation, ou régénéré par l'admin connecté via
   `POST /auth/code-secours`) : `POST /api/v1/auth/secours` avec le code et un nouveau mot de
   passe. Le code est à usage unique ; un nouveau code est renvoyé.
2. **Commande sur le serveur** : `docker compose run --rm app --reset-admin` (ou
   `java -jar invento-pos.jar --reset-admin`) affiche un mot de passe temporaire, à changer à la
   connexion.

Les deux actions sont inscrites au journal. Le code de secours affiché à l'installation passe par
la console, donc par les logs du serveur (Cloud Run) : en ligne, régénérez-en un depuis l'admin
après la première connexion.

## Installation sur le site (Raspberry Pi ou PC)

```bash
cp .env.example .env      # renseigner POS_DB_PASSWORD et POS_JWT_SECRET (openssl rand -base64 48)
docker compose up -d      # image ghcr.io/idibel79/invento-pos (amd64 et arm64) + PostgreSQL
```

Sans HTTPS sur le réseau local, mettre `POS_COOKIE_SECURE=false` dans `.env`.

**Migration des données** depuis la base de test en ligne :

```bash
pg_dump --format=custom --no-owner --schema=pos "$URL_BASE_EN_LIGNE" > pos.dump
docker compose up -d db
docker compose exec -T db pg_restore --no-owner -U pos -d pos < pos.dump
docker compose up -d app
```

## Tests en ligne : Supabase + Cloud Run

Pas à pas complet (projet GCP, Workload Identity Federation, secrets, variables GitHub) :
[docs/deploiement-cloud-run.md](docs/deploiement-cloud-run.md).

- **Supabase** sert uniquement de PostgreSQL. Les tables sont dans le schéma `pos`, que l'API
  de données de Supabase n'expose pas. Depuis Cloud Run, utiliser le pooler en mode *session*
  (port 5432, IPv4) : la connexion directe est en IPv6 et le mode *transaction* casse les
  requêtes préparées. URL JDBC : `jdbc:postgresql://<hôte>:5432/postgres?sslmode=require`.
- **Cloud Run** : le job `deploy` de la CI s'active quand les variables du dépôt `GCP_PROJECT_ID`,
  `GCP_REGION`, `GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_SERVICE_ACCOUNT`, `POS_DB_URL` et
  `POS_DB_USER` sont renseignées, et que les secrets `pos-db-password` et `pos-jwt-secret`
  existent dans Secret Manager, ainsi qu'un dépôt Artifact Registry `invento-pos` et le compte
  d'exécution `invento-pos-run`.

## Variables d'environnement

| Variable | Rôle | Défaut |
|---|---|---|
| `POS_DB_URL`, `POS_DB_USER`, `POS_DB_PASSWORD` | Connexion PostgreSQL | `localhost:5432/pos`, `pos` |
| `POS_DB_SCHEMA` | Schéma des tables | `pos` |
| `POS_JWT_SECRET` | Clé des sessions (32 caractères min.), obligatoire en `prod` | — |
| `POS_ADMIN_INITIAL_PASSWORD` | Mot de passe initial de l'admin (sinon généré) | — |
| `POS_COOKIE_SECURE` | Cookie de session limité à HTTPS | `true` (`false` en `dev`) |
| `POS_CORS_ORIGINS` | Origines autorisées (front servi ailleurs) | aucune (`localhost:5173` en `dev`) |
| `PORT` | Port HTTP (Cloud Run) | `8080` |
