CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS integration;
CREATE SCHEMA IF NOT EXISTS scoring;
CREATE SCHEMA IF NOT EXISTS triage;
CREATE SCHEMA IF NOT EXISTS analytics;

-- Triage
CREATE TABLE IF NOT EXISTS triage.user_preferences (
    user_id UUID PRIMARY KEY,
    stated_priorities TEXT,
    digest_time TIME WITHOUT TIME ZONE,
    digest_channel VARCHAR(255),
    last_digest_sent_at DATE
);

CREATE TABLE IF NOT EXISTS triage.sender_urgency_biases (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    sender_id VARCHAR(255) NOT NULL,
    bias_delta INTEGER NOT NULL,
    CONSTRAINT uk_sender_urgency_biases_user_sender UNIQUE (user_id, sender_id)
);

-- Scoring
CREATE TABLE IF NOT EXISTS scoring.thread_groups (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    topic_hash VARCHAR(255) NOT NULL,
    thread_external_id VARCHAR(255),
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL
);

