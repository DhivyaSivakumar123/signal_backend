resource "google_artifact_registry_repository" "signal" {
  location      = var.region
  repository_id = "signal"
  description   = "Signal backend Docker images"
  format        = "DOCKER"
}

output "registry_url" {
  value = "${var.region}-docker.pkg.dev/${var.project_id}/signal"
}
