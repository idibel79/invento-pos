# Invento POS

Caisse web pour les produits gérés dans Invento. Backend Java 21 / Spring Boot 3 (`backend/`),
front React + TypeScript à venir (`frontend/`, étape 2). Les règles de code et les décisions
d'architecture sont dans [CLAUDE.md](CLAUDE.md).

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
Flyway `V1`. Les écrans de vente l'utiliseront à l'étape 2.

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

- **Supabase** sert uniquement de PostgreSQL. Les tables sont dans le schéma `pos`, que l'API
  de données de Supabase n'expose pas. Utiliser la connexion directe ou le pooler en mode
  *session* (port 5432), pas le mode *transaction*. URL JDBC :
  `jdbc:postgresql://<hôte>:5432/postgres?sslmode=require`.
- **Cloud Run** : le job `deploy` de la CI s'active quand les variables du dépôt `GCP_PROJECT_ID`,
  `GCP_REGION`, `GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_SERVICE_ACCOUNT`, `POS_DB_URL` et
  `POS_DB_USER` sont renseignées, et que les secrets `pos-db-password` et `pos-jwt-secret`
  existent dans Secret Manager, ainsi qu'un dépôt Artifact Registry `invento-pos`.

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
