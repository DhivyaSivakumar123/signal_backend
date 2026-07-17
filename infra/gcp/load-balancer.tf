# Reserve a static IP for the backend
resource "google_compute_global_address" "signal_ip" {
  name = "signal-api-ip"
}

# NEG (Network Endpoint Group) pointing to gateway Cloud Run service
resource "google_compute_region_network_endpoint_group" "gateway_neg" {
  name                  = "signal-gateway-neg"
  network_endpoint_type = "SERVERLESS"
  region                = var.region

  cloud_run {
    service = google_cloud_run_v2_service.services["gateway-service"].name
  }
}

resource "google_compute_backend_service" "gateway_backend" {
  name     = "signal-gateway-backend"
  protocol = "HTTPS"

  backend {
    group = google_compute_region_network_endpoint_group.gateway_neg.id
  }
}

resource "google_compute_url_map" "signal" {
  name            = "signal-url-map"
  default_service = google_compute_backend_service.gateway_backend.id
}

resource "google_compute_target_https_proxy" "signal" {
  name             = "signal-https-proxy"
  url_map          = google_compute_url_map.signal.id
  ssl_certificates = [google_compute_managed_ssl_certificate.signal.id]
}

resource "google_compute_managed_ssl_certificate" "signal" {
  name = "signal-ssl-cert"
  managed {
    domains = [var.domain_name]
  }
}

resource "google_compute_global_forwarding_rule" "signal_https" {
  name                  = "signal-https-rule"
  ip_protocol           = "TCP"
  load_balancing_scheme = "EXTERNAL"
  port_range            = "443"
  target                = google_compute_target_https_proxy.signal.id
  ip_address            = google_compute_global_address.signal_ip.id
}

output "api_ip" {
  value       = google_compute_global_address.signal_ip.address
  description = "Point your DNS A record api.signal.app to this IP"
}
