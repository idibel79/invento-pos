# Prévisualisation locale d'une PR sous Windows : PostgreSQL (Docker), API Spring Boot avec données
# de démo et front Vite (rechargement à chaud), puis ouverture du navigateur. Ctrl+C arrête tout.
#
#   .\scripts\preview.ps1                    # code actuel du dossier
#   .\scripts\preview.ps1 21                 # PR n° 21 (récupérée depuis GitHub, HEAD détaché)
#   .\scripts\preview.ps1 ma-branche         # branche distante
#   .\scripts\preview.ps1 21 -GarderDonnees  # ne pas repartir d'une base de démo neuve
#
# Si Windows refuse d'exécuter le script :
#   powershell -ExecutionPolicy Bypass -File scripts\preview.ps1 21
param(
    [string]$Cible = "",
    [switch]$GarderDonnees
)

$ErrorActionPreference = "Stop"
$Base = "pos_preview"
$Api = "http://localhost:8080"

function Arreter([string]$Message) {
    Write-Host $Message -ForegroundColor Red
    exit 1
}

# Lance une commande externe et s'arrête si elle échoue.
function Executer([scriptblock]$Commande) {
    & $Commande
    if ($LASTEXITCODE -ne 0) { Arreter "Échec : $Commande" }
}

function Test-Url([string]$Url) {
    try {
        Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2 | Out-Null
        return $true
    } catch {
        # Pas de réponse, ou une erreur HTTP (503 tant que l'API n'est pas prête).
        return $false
    }
}

function Test-Port([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $client.Connect("127.0.0.1", $Port); return $true } catch { return $false } finally { $client.Dispose() }
}

Set-Location (Join-Path $PSScriptRoot "..")

# ---------------------------------------------------------------- Prérequis
foreach ($outil in "git", "docker", "java", "node", "npm") {
    if (-not (Get-Command $outil -ErrorAction SilentlyContinue)) { Arreter "Prérequis manquant : $outil" }
}
cmd /c "docker info >nul 2>&1"
if ($LASTEXITCODE -ne 0) { Arreter "Docker ne répond pas : démarrez Docker Desktop." }
$versionJava = (cmd /c "java -version 2>&1" | Out-String)
if ($versionJava -notmatch 'version "(\d+)' -or [int]$Matches[1] -lt 21) {
    Arreter "Java 21 ou plus est requis. Trouvé : $versionJava"
}

# ---------------------------------------------------------------- Code de la PR
if ($Cible) {
    cmd /c "git diff --quiet && git diff --cached --quiet"
    if ($LASTEXITCODE -ne 0) {
        Arreter "Modifications locales non commitées : commitez-les ou mettez-les de côté (git stash)."
    }
    if ($Cible -match '^\d+$') {
        Write-Host "==> Récupération de la PR #$Cible"
        Executer { git fetch origin "pull/$Cible/head" }
    } else {
        Write-Host "==> Récupération de la branche $Cible"
        Executer { git fetch origin $Cible }
    }
    Executer { git checkout --detach FETCH_HEAD }
    Write-Host "    Pour revenir ensuite : git checkout main"
}

if (Test-Port 8080) {
    Arreter "Le port 8080 est déjà utilisé (une autre API tourne ?). Arrêtez-la puis relancez."
}

# ---------------------------------------------------------------- Base de données
Write-Host "==> PostgreSQL (docker compose, service db)"
Executer { docker compose up -d --wait db }
if ($GarderDonnees) {
    Write-Host "==> Base $Base conservée"
    $sql = "SELECT 'CREATE DATABASE $Base' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$Base')\gexec"
    Executer { $sql | docker compose exec -T db psql -U pos -d pos -v ON_ERROR_STOP=1 -q }
} else {
    Write-Host "==> Base $Base recréée (données de démo neuves)"
    Executer { docker compose exec -T db psql -U pos -d pos -v ON_ERROR_STOP=1 -q `
        -c "DROP DATABASE IF EXISTS $Base WITH (FORCE)" -c "CREATE DATABASE $Base" }
}

# ---------------------------------------------------------------- Dépendances du front
# npm ci seulement quand package-lock.json a changé depuis la dernière installation.
$repere = "frontend\node_modules\.preview-lock"
$empreinte = (Get-FileHash frontend\package-lock.json).Hash
if (-not (Test-Path $repere) -or (Get-Content $repere) -ne $empreinte) {
    Write-Host "==> Dépendances du front (npm ci)"
    Push-Location frontend
    try { Executer { npm ci --no-audit --no-fund } } finally { Pop-Location }
    Set-Content $repere $empreinte
}

# ---------------------------------------------------------------- API puis front
New-Item -ItemType Directory -Force .preview | Out-Null
Write-Host "==> API Spring Boot (profils dev + demo), journal : .preview\backend.log"
$env:POS_DB_URL = "jdbc:postgresql://localhost:5432/$Base"
$env:SPRING_PROFILES_ACTIVE = "dev,demo"
$backend = Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "mvnw.cmd -q spring-boot:run > ..\.preview\backend.log 2>&1" `
    -WorkingDirectory backend -WindowStyle Hidden -PassThru
Remove-Item Env:POS_DB_URL, Env:SPRING_PROFILES_ACTIVE

try {
    Write-Host -NoNewline "==> Démarrage de l'API (la 1re fois, Maven télécharge ses dépendances)"
    $pret = $false
    for ($i = 0; $i -lt 300 -and -not $pret; $i++) {
        if (Test-Url "$Api/actuator/health/readiness") { $pret = $true; break }
        if ($backend.HasExited) {
            Write-Host " échec. Fin du journal :"
            Get-Content .preview\backend.log -Tail 40
            exit 1
        }
        Write-Host -NoNewline "."
        Start-Sleep -Seconds 2
    }
    if (-not $pret) { Arreter " délai dépassé, voir .preview\backend.log" }
    Write-Host " prête."

    Write-Host @"

  Comptes de démo (mot de passe demo1234 pour tous) :
    admin        administrateur, Caisse 1 et Caisse 2, PIN 0000
    responsable  Caisse 1 et Caisse 2, PIN 1234
    caissier     Caisse 1
  8 produits (DEMO-001 à DEMO-008), codes-barres 6118000010019, ...026, ... ;
  DEMO-007 presque épuisé (2), DEMO-008 en rupture.

"@
    Write-Host "==> Front Vite sur http://localhost:5173 (Ctrl+C pour tout arrêter)"
    Push-Location frontend
    try { npm run dev -- --open } finally { Pop-Location }
} finally {
    Write-Host "==> Arrêt de l'API (la base reste démarrée : docker compose stop db pour l'arrêter)"
    # /T : arrête aussi Maven et la JVM de l'application lancés par cmd.exe.
    cmd /c "taskkill /PID $($backend.Id) /T /F >nul 2>&1"
}
