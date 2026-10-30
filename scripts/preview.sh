#!/usr/bin/env bash
# Prévisualisation locale d'une PR : PostgreSQL (Docker), API Spring Boot avec données de démo et
# front Vite (rechargement à chaud), puis ouverture du navigateur. Ctrl+C arrête tout.
#
#   scripts/preview.sh                 # code actuel du dossier
#   scripts/preview.sh 21              # PR n° 21 (récupérée depuis GitHub, HEAD détaché)
#   scripts/preview.sh ma-branche      # branche distante
#   scripts/preview.sh 21 --garder-donnees   # ne pas repartir d'une base de démo neuve
#
# Sous Windows : scripts\preview.ps1 (mêmes options).
set -euo pipefail

BASE=pos_preview
API=http://localhost:8080

# Tout est dans des fonctions : bash lit un script au fil de l'eau, et le checkout de la PR peut
# modifier ce fichier pendant qu'il s'exécute.
main() {
    local cible="" garder=false
    for arg in "$@"; do
        case "$arg" in
            --garder-donnees) garder=true ;;
            -h | --help) sed -n '2,10p' "$0"; exit 0 ;;
            *) cible="$arg" ;;
        esac
    done

    cd "$(dirname "$0")/.."
    verifier_prerequis
    [[ -n "$cible" ]] && recuperer "$cible"
    if curl -s -o /dev/null "$API"; then
        echo "Le port 8080 est déjà utilisé (une autre API tourne ?). Arrêtez-la puis relancez." >&2
        exit 1
    fi

    echo "==> PostgreSQL (docker compose, service db)"
    docker compose up -d --wait db
    preparer_base "$garder"
    installer_front

    mkdir -p .preview
    echo "==> API Spring Boot (profils dev + demo), journal : .preview/backend.log"
    set -m # l'API dans son propre groupe de processus, pour l'arrêter avec ses enfants
    (cd backend && POS_DB_URL="jdbc:postgresql://localhost:5432/$BASE" SPRING_PROFILES_ACTIVE=dev,demo \
        exec ./mvnw -q spring-boot:run) > .preview/backend.log 2>&1 &
    BACKEND_PID=$!
    set +m
    trap arreter EXIT INT TERM
    attendre_api

    afficher_comptes
    echo "==> Front Vite sur http://localhost:5173 (Ctrl+C pour tout arrêter)"
    cd frontend
    npm run dev -- --open
}

verifier_prerequis() {
    local manque=0
    for outil in git docker java node npm curl; do
        if ! command -v "$outil" > /dev/null; then echo "Prérequis manquant : $outil" >&2; manque=1; fi
    done
    [[ $manque -eq 0 ]] || exit 1
    if ! docker info > /dev/null 2>&1; then
        echo "Docker ne répond pas : démarrez Docker Desktop (ou le service docker)." >&2
        exit 1
    fi
    local version
    version=$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)
    if [[ -z "$version" || "$version" -lt 21 ]]; then
        echo "Java 21 ou plus est requis (trouvé : ${version:-inconnu})." >&2
        exit 1
    fi
}

recuperer() {
    local cible=$1
    if ! git diff --quiet || ! git diff --cached --quiet; then
        echo "Modifications locales non commitées : commitez-les ou mettez-les de côté (git stash)." >&2
        exit 1
    fi
    if [[ "$cible" =~ ^[0-9]+$ ]]; then
        echo "==> Récupération de la PR #$cible"
        git fetch origin "pull/$cible/head"
    else
        echo "==> Récupération de la branche $cible"
        git fetch origin "$cible"
    fi
    git checkout --detach FETCH_HEAD
    echo "    Pour revenir ensuite : git checkout main"
}

preparer_base() {
    local psql=(docker compose exec -T db psql -U pos -d pos -v ON_ERROR_STOP=1 -q)
    if [[ $1 == true ]]; then
        echo "==> Base $BASE conservée"
        echo "SELECT 'CREATE DATABASE $BASE' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$BASE')\\gexec" \
            | "${psql[@]}"
    else
        echo "==> Base $BASE recréée (données de démo neuves)"
        "${psql[@]}" -c "DROP DATABASE IF EXISTS $BASE WITH (FORCE)" -c "CREATE DATABASE $BASE"
    fi
}

# npm ci seulement quand package-lock.json a changé depuis la dernière installation.
installer_front() {
    local repere=frontend/node_modules/.preview-lock
    if [[ -f $repere ]] && cmp -s frontend/package-lock.json "$repere"; then return; fi
    echo "==> Dépendances du front (npm ci)"
    (cd frontend && npm ci --no-audit --no-fund)
    cp frontend/package-lock.json "$repere"
}

attendre_api() {
    echo -n "==> Démarrage de l'API (la 1re fois, Maven télécharge ses dépendances)"
    for _ in $(seq 1 300); do
        if curl -sf -o /dev/null "$API/actuator/health/readiness"; then echo " prête."; return; fi
        if ! kill -0 "$BACKEND_PID" 2> /dev/null; then
            echo " échec. Fin du journal :" >&2
            tail -n 40 .preview/backend.log >&2
            exit 1
        fi
        echo -n "."
        sleep 2
    done
    echo " délai dépassé, voir .preview/backend.log" >&2
    exit 1
}

afficher_comptes() {
    cat << 'TEXTE'

  Comptes de démo (mot de passe demo1234 pour tous) :
    admin        administrateur, Caisse 1 et Caisse 2, PIN 0000
    responsable  Caisse 1 et Caisse 2, PIN 1234
    caissier     Caisse 1
  8 produits (DEMO-001 à DEMO-008), codes-barres 6118000010019, ...026, ... ;
  DEMO-007 presque épuisé (2), DEMO-008 en rupture.

TEXTE
}

arreter() {
    trap - EXIT INT TERM
    echo
    echo "==> Arrêt de l'API (la base reste démarrée : docker compose stop db pour l'arrêter)"
    kill -- "-$BACKEND_PID" 2> /dev/null || true
    wait "$BACKEND_PID" 2> /dev/null || true
}

main "$@"
exit
