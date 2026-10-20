# infra/gcp/deploy.ps1
# Usage: .\infra\gcp\deploy.ps1
# Requires: gcloud CLI logged in, Docker Desktop running, GCP Project set

$ErrorActionPreference = "Stop"

# 1. Verify Docker Daemon
try {
    $null = docker version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Docker not ready"
    }
} catch {
    Write-Host "❌ Error: Docker daemon is not running! Please start Docker Desktop first." -ForegroundColor Red
    exit 1
}

# 2. Get GCP Project ID
$PROJECT_ID = (gcloud config get-value project 2>$null).Trim()
if (-not $PROJECT_ID) {
    Write-Host "❌ Error: No GCP project configured. Run: gcloud config set project [YOUR_PROJECT_ID]" -ForegroundColor Red
    exit 1
}

$REGION = "us-central1"
$REGISTRY = "${REGION}-docker.pkg.dev/${PROJECT_ID}/signal"
$SERVICES = @("gateway-service", "auth-service", "integration-service", "scoring-service", "triage-service", "analytics-service")

try {
    $GIT_TAG = (git rev-parse --short HEAD 2>$null).Trim()
} catch {
    $GIT_TAG = "v1"
}

Write-Host "▶ Authenticating Docker with Artifact Registry..." -ForegroundColor Cyan
gcloud auth print-access-token | docker login -u oauth2accesstoken --password-stdin "${REGION}-docker.pkg.dev"

$BACKEND_DIR = if (Test-Path "mvnw.cmd") { "." } else { "./backend" }

Write-Host "▶ Building all services with Maven (Java 21)..." -ForegroundColor Cyan
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"
if ($BACKEND_DIR -eq "./backend") {
    Push-Location backend
    .\mvnw.cmd clean package -DskipTests --quiet
    Pop-Location
} else {
    .\mvnw.cmd clean package -DskipTests --quiet
}

# Check if signal-vpc exists in GCP
$HAS_SIGNAL_VPC = (gcloud compute networks list --filter="name=signal-vpc" --format="value(name)" 2>$null).Trim()

foreach ($SERVICE in $SERVICES) {
    Write-Host "`n▶ Building image: $SERVICE" -ForegroundColor Yellow
    docker build `
        -t "${REGISTRY}/${SERVICE}:latest" `
        -t "${REGISTRY}/${SERVICE}:${GIT_TAG}" `
        "${BACKEND_DIR}/${SERVICE}"

    Write-Host "▶ Pushing image: $SERVICE" -ForegroundColor Yellow
    docker push "${REGISTRY}/${SERVICE}:latest"
    docker push "${REGISTRY}/${SERVICE}:${GIT_TAG}"

    Write-Host "▶ Deploying to Cloud Run: $SERVICE" -ForegroundColor Green
    if ($HAS_SIGNAL_VPC -eq "signal-vpc") {
        gcloud run deploy $SERVICE `
            --image="${REGISTRY}/${SERVICE}:latest" `
            --network=signal-vpc `
            --subnet=signal-vpc `
            --region=$REGION `
            --platform=managed `
            --quiet
    } else {
        gcloud run deploy $SERVICE `
            --image="${REGISTRY}/${SERVICE}:latest" `
            --region=$REGION `
            --platform=managed `
            --quiet
    }

    Write-Host "✓ Deployed: $SERVICE" -ForegroundColor Green
}

Write-Host "`n✅ All services deployed successfully." -ForegroundColor Green
$GATEWAY_URL = gcloud run services describe gateway-service --region=$REGION --format="value(status.url)"
Write-Host "Gateway URL: $GATEWAY_URL" -ForegroundColor Cyan
