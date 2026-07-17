locals {
  secrets = {
    "signal-db-password"          = var.db_password
    "signal-jwt-secret"           = var.jwt_secret
    "signal-google-client-id"     = var.google_client_id
    "signal-google-client-secret" = var.google_client_secret
    "signal-slack-client-id"      = var.slack_client_id
    "signal-slack-client-secret"  = var.slack_client_secret
    "signal-slack-signing-secret" = var.slack_signing_secret
    "signal-openai-api-key"       = var.openai_api_key
  }
}

resource "google_secret_manager_secret" "secrets" {
  for_each  = local.secrets
  secret_id = each.key
  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "secret_values" {
  for_each    = local.secrets
  secret      = google_secret_manager_secret.secrets[each.key].id
  secret_data = each.value
}

# Service account for Cloud Run services to access secrets
resource "google_service_account" "signal_runner" {
  account_id   = "signal-runner"
  display_name = "Signal Cloud Run Service Account"
}

resource "google_project_iam_member" "secret_accessor" {
  project = var.project_id
  role    = "roles/secretmanager.secretAccessor"
  member  = "serviceAccount:${google_service_account.signal_runner.email}"
}

resource "google_project_iam_member" "pubsub_publisher" {
  project = var.project_id
  role    = "roles/pubsub.publisher"
  member  = "serviceAccount:${google_service_account.signal_runner.email}"
}

resource "google_project_iam_member" "pubsub_subscriber" {
  project = var.project_id
  role    = "roles/pubsub.subscriber"
  member  = "serviceAccount:${google_service_account.signal_runner.email}"
}

resource "google_project_iam_member" "cloudsql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.signal_runner.email}"
}
