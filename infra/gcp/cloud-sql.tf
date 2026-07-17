resource "google_sql_database_instance" "signal" {
  name             = "signal-postgres"
  database_version = "POSTGRES_15"
  region           = var.region

  settings {
    tier              = "db-f1-micro"   # cheapest — upgrade when needed
    availability_type = "ZONAL"         # use REGIONAL for production HA

    backup_configuration {
      enabled            = true
      start_time         = "03:00"
      binary_log_enabled = false
    }

    ip_configuration {
      ipv4_enabled    = false           # no public IP — private only
      private_network = google_compute_network.signal_vpc.id
    }

    database_flags {
      name  = "max_connections"
      value = "100"
    }
  }

  deletion_protection = false           # set to true for production

  depends_on = [google_service_networking_connection.vpc_peering]

  lifecycle {
    ignore_changes = [settings[0].version]
  }
}

# One database per service schema
resource "google_sql_database" "schemas" {
  for_each = toset(["auth", "integration", "scoring", "triage", "analytics"])
  name     = each.value
  instance = google_sql_database_instance.signal.name
}

resource "google_sql_user" "signal_user" {
  name     = "signal"
  instance = google_sql_database_instance.signal.name
  password = var.db_password
}

# VPC for private networking
resource "google_compute_network" "signal_vpc" {
  name                    = "signal-vpc"
  auto_create_subnetworks = true
}

resource "google_compute_global_address" "private_ip_range" {
  name          = "signal-private-ip"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.signal_vpc.id
}

resource "google_service_networking_connection" "vpc_peering" {
  network                 = google_compute_network.signal_vpc.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_ip_range.name]
}

output "db_connection_name" {
  value = google_sql_database_instance.signal.connection_name
}
output "db_private_ip" {
  value = google_sql_database_instance.signal.private_ip_address
}
