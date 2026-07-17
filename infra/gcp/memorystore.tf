resource "google_redis_instance" "signal" {
  name           = "signal-redis"
  tier           = "BASIC"              # use STANDARD_HA for production
  memory_size_gb = 1
  region         = var.region

  authorized_network = google_compute_network.signal_vpc.id
  connect_mode       = "PRIVATE_SERVICE_ACCESS"

  redis_version     = "REDIS_7_0"
  display_name      = "Signal Redis Cache"

  depends_on = [google_service_networking_connection.vpc_peering]
}

output "redis_host" {
  value = google_redis_instance.signal.host
}
output "redis_port" {
  value = google_redis_instance.signal.port
}
