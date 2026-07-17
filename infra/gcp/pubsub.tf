# Topics (equivalent to SQS queues)
resource "google_pubsub_topic" "raw_messages" {
  name = "signal-raw-messages"
  message_retention_duration = "86400s"   # 24 hours
}

resource "google_pubsub_topic" "scored_items" {
  name = "signal-scored-items"
  message_retention_duration = "86400s"
}

resource "google_pubsub_topic" "interruption_signals" {
  name = "signal-interruption-signals"
  message_retention_duration = "86400s"
}

# Subscriptions (consumers pull from these)
resource "google_pubsub_subscription" "scoring_consumer" {
  name  = "signal-scoring-consumer"
  topic = google_pubsub_topic.raw_messages.name

  ack_deadline_seconds       = 60
  message_retention_duration = "86400s"
  retain_acked_messages      = false

  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "300s"
  }

  dead_letter_policy {
    dead_letter_topic     = google_pubsub_topic.raw_messages_dlq.id
    max_delivery_attempts = 5
  }
}

resource "google_pubsub_subscription" "triage_consumer" {
  name  = "signal-triage-consumer"
  topic = google_pubsub_topic.scored_items.name
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "300s"
  }
}

resource "google_pubsub_subscription" "analytics_consumer" {
  name  = "signal-analytics-consumer"
  topic = google_pubsub_topic.interruption_signals.name
  ack_deadline_seconds = 60
}

# Dead letter topics
resource "google_pubsub_topic" "raw_messages_dlq" {
  name = "signal-raw-messages-dlq"
}
