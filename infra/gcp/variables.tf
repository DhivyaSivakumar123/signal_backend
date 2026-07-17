variable "project_id" {
  description = "GCP Project ID"
  type        = string
}

variable "region" {
  description = "GCP region"
  type        = string
  default     = "us-central1"
}

variable "db_password" {
  description = "Cloud SQL postgres password"
  type        = string
  sensitive   = true
}

variable "jwt_secret" {
  description = "JWT signing secret"
  type        = string
  sensitive   = true
}

variable "google_client_id" {
  type = string
}

variable "google_client_secret" {
  type      = string
  sensitive = true
}

variable "slack_client_id" {
  type = string
}

variable "slack_client_secret" {
  type      = string
  sensitive = true
}

variable "slack_signing_secret" {
  type      = string
  sensitive = true
}

variable "openai_api_key" {
  type      = string
  sensitive = true
}

variable "services" {
  description = "List of backend microservice names"
  type        = list(string)
  default     = [
    "gateway-service",
    "auth-service",
    "integration-service",
    "scoring-service",
    "triage-service",
    "analytics-service"
  ]
}

variable "domain_name" {
  description = "Custom domain name for backend load balancer"
  type        = string
  default     = "api.signal.app"
}

