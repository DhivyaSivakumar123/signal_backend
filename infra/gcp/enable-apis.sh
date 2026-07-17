#!/bin/bash
# infra/gcp/enable-apis.sh
# Run: chmod +x enable-apis.sh && ./enable-apis.sh

PROJECT_ID=$(gcloud config get-value project 2>/dev/null)
echo "Enabling APIs for project: $PROJECT_ID"

gcloud services enable \
  run.googleapis.com \
  sql-component.googleapis.com \
  sqladmin.googleapis.com \
  redis.googleapis.com \
  pubsub.googleapis.com \
  storage.googleapis.com \
  secretmanager.googleapis.com \
  artifactregistry.googleapis.com \
  servicenetworking.googleapis.com \
  compute.googleapis.com \
  cloudresourcemanager.googleapis.com \
  iam.googleapis.com \
  logging.googleapis.com \
  monitoring.googleapis.com \
  --project=$PROJECT_ID

echo "All APIs enabled."
