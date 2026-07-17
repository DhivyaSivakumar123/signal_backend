locals {
  registry = "${var.region}-docker.pkg.dev/${var.project_id}/signal"

  # Common env vars shared by all services
  common_env = [
    { name = "SPRING_PROFILES_ACTIVE", value = "gcp" },
    { name = "GCP_PROJECT_ID",         value = var.project_id },
    { name = "DB_HOST",                value = google_sql_database_instance.signal.private_ip_address },
    { name = "DB_PASSWORD",            value = var.db_password },
    { name = "REDIS_HOST",             value = google_redis_instance.signal.host },
    { name = "REDIS_PORT",             value = tostring(google_redis_instance.signal.port) },
  ]

  # Per-service config
  service_config = {
    "gateway-service" = {
      port     = 8080
      cpu      = "1"
      memory   = "512Mi"
      min_inst = 1
      max_inst = 3
    }
    "auth-service" = {
      port     = 8081
      cpu      = "1"
      memory   = "512Mi"
      min_inst = 1
      max_inst = 3
    }
    "integration-service" = {
      port     = 8082
      cpu      = "1"
      memory   = "768Mi"
      min_inst = 1
      max_inst = 5
    }
    "scoring-service" = {
      port     = 8083
      cpu      = "2"
      memory   = "1Gi"
      min_inst = 1
      max_inst = 5
    }
    "triage-service" = {
      port     = 8084
      cpu      = "1"
      memory   = "512Mi"
      min_inst = 1
      max_inst = 3
    }
    "analytics-service" = {
      port     = 8085
      cpu      = "1"
      memory   = "512Mi"
      min_inst = 1
      max_inst = 3
    }
  }
}

resource "google_cloud_run_v2_service" "services" {
  for_each = local.service_config
  name     = each.key
  location = var.region

  template {
    service_account = google_service_account.signal_runner.email

    scaling {
      min_instance_count = each.value.min_inst
      max_instance_count = each.value.max_inst
    }

    vpc_access {
      network_interfaces {
        network    = google_compute_network.signal_vpc.name
        subnetwork = google_compute_network.signal_vpc.name
      }
      egress = "PRIVATE_RANGES_ONLY"
    }

    containers {
      image = "${local.registry}/${each.key}:latest"
      name  = each.key

      ports {
        container_port = each.value.port
      }

      resources {
        limits = {
          cpu    = each.value.cpu
          memory = each.value.memory
        }
        cpu_idle          = true
        startup_cpu_boost = true
      }

      # Common env vars
      dynamic "env" {
        for_each = local.common_env
        content {
          name  = env.value.name
          value = env.value.value
        }
      }

      # Health check
      startup_probe {
        http_get {
          path = "/actuator/health"
          port = each.value.port
        }
        initial_delay_seconds = 10
        timeout_seconds       = 5
        period_seconds        = 10
        failure_threshold     = 3
      }
      liveness_probe {
        http_get {
          path = "/actuator/health"
          port = each.value.port
        }
        period_seconds    = 30
        timeout_seconds   = 5
        failure_threshold = 3
      }
    }
  }
}

# Allow gateway-service to be publicly accessible
resource "google_cloud_run_v2_service_iam_member" "gateway_public" {
  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.services["gateway-service"].name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

output "gateway_url" {
  value = google_cloud_run_v2_service.services["gateway-service"].uri
}
