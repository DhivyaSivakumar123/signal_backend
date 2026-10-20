#!/bin/bash
# infra/gcp/deploy.sh
# Usage: ./infra/gcp/deploy.sh or ./deploy.sh
# Requires: gcloud CLI logged in, Docker running, PROJECT_ID set

set -e

PROJECT_ID=$(gcloud config get-value project 2>/dev/null)
REGION="us-central1"
REGISTRY="${REGION}-docker.pkg.dev/${PROJECT_ID}/signal"
SERVICES=("gateway-service" "auth-service" "integration-service" "scoring-service" "triage-service" "analytics-service")
GIT_TAG=$(git rev-parse --short HEAD 2>/dev/null || echo "v1")

echo "▶ Authenticating Docker with Artifact Registry..."
gcloud auth print-access-token 2>/dev/null | docker.exe login -u oauth2accesstoken --password-stdin ${REGION}-docker.pkg.dev

if [ -f "mvnw.cmd" ]; then
  BACKEND_DIR="."
else
  BACKEND_DIR="./backend"
fi

echo "▶ Building all services with Maven..."
export JAVA_HOME="C:/Program Files/Java/jdk-21.0.10"
if [ "$BACKEND_DIR" = "./backend" ]; then
  (cd backend && ./mvnw clean package -DskipTests)
else
  ./mvnw clean package -DskipTests
fi

for SERVICE in "${SERVICES[@]}"; do
  echo "▶ Building image: $SERVICE"
  docker.exe build \
    -t "${REGISTRY}/${SERVICE}:latest" \
    -t "${REGISTRY}/${SERVICE}:${GIT_TAG}" \
    ${BACKEND_DIR}/${SERVICE}

  echo "▶ Pushing image: $SERVICE"
  gcloud auth print-access-token 2>/dev/null | docker.exe login -u oauth2accesstoken --password-stdin ${REGION}-docker.pkg.dev
  docker.exe push "${REGISTRY}/${SERVICE}:latest"
  docker.exe push "${REGISTRY}/${SERVICE}:${GIT_TAG}"

  echo "▶ Deploying to Cloud Run: $SERVICE"
  gcloud run deploy ${SERVICE} \
    --image="${REGISTRY}/${SERVICE}:latest" \
    --network=signal-vpc \
    --subnet=signal-vpc \
    --region=${REGION} \
    --platform=managed \
    --quiet

  echo "✓ Deployed: $SERVICE"
done

echo ""
echo "✅ All services deployed."
echo "Gateway URL: $(gcloud run services describe gateway-service --region=${REGION} --format='value(status.url)')"
