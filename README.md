# ☕ Signal — Backend Services

[![Backend CI](https://github.com/DhivyaSivakumar123/signal_backend/actions/workflows/ci.yml/badge.svg)](https://github.com/DhivyaSivakumar123/signal_backend/actions/workflows/ci.yml)

<p align="left">
  <img src="https://skillicons.dev/icons?i=java,spring,postgres,docker,redis,aws,gcp" />
</p>

---

## 📖 Overview

**Signal Backend** is a multi-module microservice platform built on **Spring Boot 3**, **Java 21**, and **Docker**. It orchestrates real-time message polling from Google Workspace (Gmail, Calendar), applies intelligent AI urgency scoring via Groq (`llama-3.1-8b-instant`), and delivers targeted email digest notifications — all running inside a containerized, event-driven architecture.

---

## 🏛️ System Architecture

```mermaid
flowchart TD
    subgraph Ingestion["1. Ingestion Layer"]
        Gmail["Google Gmail API (REAL)"]
        GCal["Google Calendar API (REAL)"]
        SlackStub["Slack Connector (STUB)"]
        OutlookStub["Outlook Connector (STUB)"]
    end

    subgraph Messaging["2. Event-Driven Messaging Layer"]
        subgraph LocalEnv["Local Environment: AWS SQS via LocalStack (:4566)"]
            QRaw["Queue: signal-raw-messages"]
            QScored["Queue: signal-scored-items"]
            QSignals["Queue: signal-interruption-signals"]
        end
        subgraph GCPEnv["GCP Cloud Profile: Google Cloud Pub/Sub"]
            TRaw["Topic: signal-raw-messages"]
            TScored["Topic: signal-scored-items"]
            TSignals["Topic: signal-interruption-signals"]
        end
    end

    subgraph Processing["3. Core Microservices (Spring Boot 3, Java 21)"]
        GW["gateway-service (:8080)\nJWT Filter & Role Propagation"]
        Auth["auth-service (:8081)\nOAuth2 & Token Blacklist"]
        Integ["integration-service (:8082)\nAES-256-GCM Token Encryption & Poller"]
        Scorer["scoring-service (:8083)\nGroq Cloud Llama-3.1-8b-instant"]
        Triage["triage-service (:8084)\nFeed, Digest Scheduler, SMTP Mailer"]
        Analytics["analytics-service (:8085)\nRollup & Volume Metrics"]
    end

    subgraph Storage["4. Persistent State"]
        PG[(PostgreSQL Multi-Schema DB)]
        Redis[(Redis Cache & Session Store)]
    end

    Gmail & GCal -->|Poller / Sync| Integ
    SlackStub -.->|Stubbed / Inactive| Integ
    OutlookStub -.->|Stubbed / Inactive| Integ

    Integ -->|RawMessageEvent| QRaw
    Integ -.->|Cloud Event| TRaw
    QRaw -->|Consume Raw| Scorer
    TRaw -.->|Pull Sub| Scorer

    Scorer -->|Groq API| GroqAPI["Groq Cloud API\nllama-3.1-8b-instant"]
    GroqAPI -->|UrgencyResult JSON| Scorer

    Scorer -->|ScoredItemEvent| QScored
    Scorer -.->|Cloud Event| TScored

    QScored -->|Consume Scored| Triage
    QScored -->|Consume Scored| QSignals
    TScored -.->|Pull Sub| Triage
    TScored -.->|Pull Sub| TSignals

    QSignals -->|Rollup Metrics| Analytics
    TSignals -.->|Rollup Metrics| Analytics

    GW -->|Route + X-User-Role| Auth & Integ & Scorer & Triage & Analytics
    Auth & Integ & Scorer & Triage & Analytics --> PG
    Auth & Integ & Scorer --> Redis
```

---

## 🔌 Real vs Stub Integrations

| Provider | Status | Implementation Details |
|---|---|---|
| **Google Gmail** | **REAL** | Polls Google Gmail REST API (`/gmail/v1/users/me/messages?q=is:unread`), parses RFC 822 headers, extracts snippets, and performs automatic OAuth2 refresh token rotation. |
| **Google Calendar** | **REAL** | Ingests calendar events via Google Calendar v3 API (`/calendar/v3/calendars/primary/events`) to detect upcoming meetings and context changes. |
| **Slack** | **STUB** | Stub connector structure retained for OAuth flow and future webhook ingestion. Mock random generator permanently disabled to ensure zero synthetic data. |
| **Microsoft Outlook** | **STUB** | Stub entity and endpoint structure preserved. Synthetic generator disabled; ready for Microsoft Graph API credentials. |

---

## 🔄 Dual Messaging: LocalStack SQS vs GCP Pub/Sub

Signal Backend supports two interchangeable messaging backends depending on the active Spring profile:

1. **Local Development Profile (`docker` / default `application.yml`)**:
   - **Engine**: **AWS SQS** running in **LocalStack** container at `http://localhost:4566`.
   - **Queues**: `signal-raw-messages`, `signal-scored-items`, `signal-interruption-signals`.
   - **Configuration**: Managed via `io.awspring.cloud:spring-cloud-aws-starter-sqs`.
2. **GCP Cloud Profile (`gcp` / `application-gcp.yml`)**:
   - **Engine**: **Google Cloud Pub/Sub** topics and pull subscriptions.
   - **Autoconfiguration**: AWS SQS autoconfiguration is explicitly excluded (`io.awspring.cloud.autoconfigure.sqs.SqsAutoConfiguration`).
   - **Topics**: `signal-raw-messages`, `signal-interruption-signals`, `signal-scored-items`.
3. **Architectural Abstraction**:
   - Service business logic interacts strictly through domain event publishers (`RawMessagePublisher`, `InterruptionSignalPublisher`, `ScoredItemPublisher`) and subscriber listeners. This decoupling allows running locally with zero AWS/GCP bills on LocalStack, while seamlessly deploying to GCP without modifying application code.

---

## 📡 REST API Endpoint Count

*Extracted directly from controller mapping annotations (`@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`):*

| Service | Controller | Method Endpoints | Key Sample Routes |
|---|---|:---:|---|
| **auth-service** | `AuthController.java` | **8** | `POST /api/auth/dev-login`<br>`GET /api/me`<br>`GET /api/auth/users/{id}`<br>`POST /api/auth/refresh`<br>`POST /api/auth/logout`<br>`GET /oauth2/authorize/{provider}`<br>`GET /oauth2/callback/{provider}`<br>`POST /api/auth/callback` |
| **integration-service** | `IntegrationController.java` | **9** | `POST /api/integrations/slack/connect`<br>`POST /api/integrations/gmail/connect`<br>`POST /api/integrations/outlook/connect`<br>`POST /api/integrations/calendar/connect`<br>`GET /api/integrations`<br>`POST /api/integrations/sync`<br>`DELETE /api/integrations/{id}`<br>`GET /api/integrations/oauth2/authorize/{provider}`<br>`GET /api/integrations/oauth2/callback/{provider}` |
| **triage-service** | `TriageController.java` | **8** | `GET /api/triage/today`<br>`GET /api/triage/focus-slots`<br>`POST /api/triage/items/{id}/feedback`<br>`PUT /api/triage/preferences`<br>`POST /api/triage/digest/send-now`<br>`GET /api/triage/preferences`<br>`GET /api/triage/feedback/bias/{userId}/{senderId}`<br>`GET /api/triage/preferences/{userId}` |
| **analytics-service** | `AnalyticsController.java` | **2** | `GET /api/analytics/team-load`<br>`GET /api/analytics/recommendations` |
| **scoring-service** | *(Event Consumer)* | **0** | Event-driven SQS/PubSub queue consumer (no REST endpoints) |
| **Grand Total** | **All Microservices** | **27** | *(Excludes Spring Boot `/actuator/health` and Gateway proxy routes)* |

---

## ☁️ Cloud Run Deployment (Configuration Provided; Verify Before Use)

Cloud Run configuration provided; verify before use.

A health verification check was performed on **October 21, 2026** against the deployed Cloud Run Gateway endpoint (`curl -s -o /dev/null -w "%{http_code}" https://gateway-service-644308977417.us-central1.run.app/actuator/health`), which returned HTTP **`503`** (`{"status":"DOWN"}` due to Redis dependency unavailability in that deployment environment). Downstream service configurations and Dockerfiles are provided in the repository, but active availability must be verified prior to production traffic.

| Service | Target Platform | Cloud Run Endpoint (Configured) | Verified Status (Checked Oct 21, 2026) |
|---|---|---|---|
| 🔀 **Gateway Service** | Google Cloud Run (`us-central1`) | `https://gateway-service-644308977417.us-central1.run.app` | **HTTP 503 (DOWN)** — Dependency failure (Redis offline) |
| 🔑 **Auth Service** | Google Cloud Run (`us-central1`) | Configured (`auth-service`) | Private backend (IAM invoker only) |
| 📥 **Integration Service** | Google Cloud Run (`us-central1`) | Configured (`integration-service`) | Private backend (IAM invoker only) |
| 🧠 **Scoring Service** | Google Cloud Run (`us-central1`) | Configured (`scoring-service`) | Private backend (IAM invoker only) |
| 📋 **Triage Service** | Google Cloud Run (`us-central1`) | Configured (`triage-service`) | Private backend (IAM invoker only) |
| 📈 **Analytics Service** | Google Cloud Run (`us-central1`) | Configured (`analytics-service`) | Private backend (IAM invoker only) |

*Local & Infrastructure State:*
- **Local Dev Orchestration**: Fully runnable locally via Docker Compose (external PostgreSQL with 5 schemas, Redis, LocalStack SQS).
- **Cloud IAM Security**: Downstream Cloud Run services are secured with Google Cloud IAM (`roles/run.invoker`) for internal microservice communication through Gateway Service.

---

## 🐳 Docker Compose Quickstart (Clean Clone)

Follow these steps to run the entire backend stack from a clean clone:

### 1. Clone Repository
```bash
git clone https://github.com/Akaash-S/signal_backend.git
cd signal_backend
```

### 2. Configure Environment File
Copy the example environment template:
```bash
cp .env.example .env
```
Ensure the following key variables are set in `.env` (all secrets below are **sample / example placeholders**):
```properties
# [EXAMPLE / PLACEHOLDER - Generate a real 32-byte / 64-hex string for production]
SIGNAL_ENCRYPTION_KEY=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef

# [EXAMPLE / PLACEHOLDER - Replace with your HMAC-SHA256 JWT signing secret]
SIGNAL_JWT_SECRET=sample_dev_jwt_secret_key_minimum_32_characters_for_testing_purposes

# [EXAMPLE / PLACEHOLDER - Replace with your Groq Cloud API key]
SIGNAL_SCORING_PROVIDER=groq
SIGNAL_SCORING_GROQ_API_KEY=gsk_sample_groq_api_key_placeholder_for_local_development

# [EXAMPLE / PLACEHOLDER - Local Docker PostgreSQL credentials]
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/signal_db
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
```

### 3. Build Service JARs
Using the Maven wrapper:
```bash
# On Linux/macOS:
./mvnw clean package -DskipTests

# On Windows (PowerShell):
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"; ./mvnw clean package -DskipTests
```

### 4. Start Containers
```bash
docker compose --env-file .env up -d --build
```

### 5. Services & Container Count
The stack defines **8 services** (`docker compose config --services`):
| Service Name | Port | Description |
|---|---|---|
| `analytics-service` | `8085` | Communication load & rollup metrics |
| `auth-service` | `8081` | Authentication & token management |
| `gateway-service` | `8080` | Central API Gateway & CORS filter |
| `integration-service` | `8082` | Real Gmail/Calendar connectors & AES-256 token crypto |
| `localstack` | `4566` | Emulates AWS SQS locally (`signal-raw-messages`, `signal-scored-items`, `signal-interruption-signals`) |
| `redis` | `6379` | Session blacklist & poller deduplication cache |
| `scoring-service` | `8083` | AI urgency scoring with Groq |
| `triage-service` | `8084` | Feed curation, preferences & SMTP digests |

> [!NOTE]
> **PostgreSQL Configuration**: The `docker-compose.yml` file defines 8 application and support services but does not contain a `postgres` container. The backend services connect to an external or host PostgreSQL instance specified by `SPRING_DATASOURCE_URL` (which references host `postgres` in `.env.example`). When developing locally, ensure an external PostgreSQL server is running with the required schemas (`auth`, `integration`, `scoring`, `triage`, `analytics`, `gateway`).

Verify running container status:
```bash
docker compose ps
```

---

## 🧪 Automated Test Suite

Run all tests across the reactor:
```bash
$env:JAVA_HOME = "C:\\Program Files\\Java\\jdk-21.0.10"; ./mvnw test
```

### Test Count Breakdown (Evidence from `./mvnw test` session):
| Module | Test Class | Passed | Failed | Errors | Skipped |
|---|---|:---:|:---:|:---:|:---:|
| `common-lib` | *(Shared library models)* | 0 | 0 | 0 | 0 |
| `gateway-service` | `JwtAuthFilterTest` | 7 | 0 | 0 | 0 |
| `auth-service` | `AuthControllerTest`, `DevLoginDisabledTest`, `AuthRedisLogicTest` | 6 | 0 | 0 | 0 |
| `integration-service` | `AesGcmEncryptionConverterTest`, `EmailPollerTokenRefreshTest`, `RefreshTokenMigrationServiceTest` | 16 | 0 | 0 | 0 |
| `scoring-service` | `GroqUrgencyScorerTest` | 8 | 0 | 0 | 0 |
| `triage-service` | `RankingServiceTest` | 4 | 0 | 0 | 0 |
| `analytics-service` | *(Rollup aggregators)* | 0 | 0 | 0 | 0 |
| **Grand Total** | **All Modules** | **41** | **0** | **0** | **0** |

- **Known gaps**: `analytics-service` has zero automated tests (rollup aggregators). `triage-service` tests cover `RankingService` logic (urgency ordering, thread collapsing, tie-breaking), but digest delivery and email notifications are not yet covered by automated unit tests.

---

## 🔒 Security & Hygiene Audit

- **Dev Login Endpoint Protection**:
  - `POST /api/auth/dev-login` is disabled by default via `signal.auth.dev-login-enabled=false` and is explicitly disabled in `application.yml`, `application-gcp.yml`, and `docker-compose.yml`.
  - When disabled, any invocation returns `403 Forbidden` (`{"data": null, "error": {"code": "FORBIDDEN", "message": "Dev login endpoint is disabled in this environment."}}`). Verified via `DevLoginDisabledTest`.
- **Google Cloud Run IAM Architecture**:
  - **Public Ingress**: Only `gateway-service` allows public invocations (`allUsers` has `roles/run.invoker`) as the single, authenticated application entry point.
  - **Private Downstream Services**: All 5 downstream services (`auth-service`, `integration-service`, `scoring-service`, `triage-service`, `analytics-service`) are completely private. Public requests return `403 Forbidden` enforced directly by Google Cloud infrastructure.
  - **Service-to-Service Authorization**: The Gateway's runtime service account (`<PROJECT_NUMBER>-compute@developer.gserviceaccount.com`) is granted `roles/run.invoker` on all downstream microservices.
- **Strict Gateway CORS Policy**:
  - Implemented in `GatewayConfig.java`: restricts cross-origin browser access strictly to `https://signal-mocha-nine.vercel.app`, `http://localhost:4200`, and `http://localhost:80`.
  - Enforces explicit allowed methods (`GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`, `PATCH`), explicit allowed headers, exposed security headers (`Authorization`, `X-User-Id`, `X-User-Role`), and preflight caching (`maxAge = 3600s`).
- **OAuth Refresh Token Encryption at Rest**:
  - AES-256-GCM encryption enforced via JPA `AesGcmEncryptionConverter` using a 32-byte key from `SIGNAL_ENCRYPTION_KEY`, dynamic 12-byte IVs, and versioned `v1:ENC:` format.
- **Tracked Files & Secret Hygiene**:
  - Verified that no `*.tfvars`, `*.tfstate`, or `.env*` files containing secrets are tracked in git (`.env.example` and `terraform.tfvars.example` are committed as sanitized templates).
- **Database Initialization**:
  - `init-db.sql` only declares PostgreSQL schemas (`CREATE SCHEMA IF NOT EXISTS ...`); it contains zero user credentials, passwords, or sensitive seed data.

---

## 🛠️ What I Built

*Primary contributor: Akaash-S (verified with `git shortlog -sn --all`: 56 commits in this repository, 81 of 87 across both frontend and backend repositories).*

### Key Components & Implementations:
- **Live Gmail and Calendar Ingestion (`EmailPoller`, `CalendarPoller`)**:
  - `EmailPoller.java`: Direct REST ingestion from Google Gmail API (`/gmail/v1/users/me/messages?q=is:unread`), parsing RFC 822 MIME headers (`From`, `Subject`, `Date`), extracting body snippets, and publishing `RawMessageEvent` payloads to event queues.
  - `CalendarPoller.java`: Ingests upcoming meetings and schedule context via Google Calendar v3 API (`/calendar/v3/calendars/primary/events`).
- **OAuth2 Refresh-Token Recovery (`EmailPoller`, `CalendarPoller`)**:
  - Implements automatic access-token recovery upon receiving `401 Unauthorized` responses from Google APIs by invoking the OAuth2 token refresh endpoint with securely stored credentials.
- **AES-256-GCM Token Encryption with Legacy Migration (`AesGcmEncryptionConverter`, `RefreshTokenMigrationService`)**:
  - `AesGcmEncryptionConverter.java`: JPA attribute converter encrypting sensitive OAuth refresh tokens at rest using AES-256-GCM with dynamic 12-byte initialization vectors (IV), 128-bit authentication tags, and versioned `v1:ENC:` prefix.
  - `RefreshTokenMigrationService.java`: Startup migration service that scans legacy unencrypted plaintext tokens in the database, encrypts them in place, and logs migration telemetry safely without leaking plaintext.
- **On-Demand Sync Endpoint (`IntegrationController`)**:
  - `IntegrationController.java` (`POST /api/integrations/sync`): Allows users to trigger immediate message synchronization. Note: Registered pollers run **sequentially** in a loop across active integrations.
- **OpenAI to Groq LLM Migration (`GroqUrgencyScorer`)**:
  - Replaced legacy OpenAI dependency with Groq Cloud (`llama-3.1-8b-instant`), enforcing strict JSON structured outputs (`UrgencyResult`) and structured error handling via `GroqApiException`.
- **LocalStack Local Messaging & Pub/Sub Portability**:
  - Decoupled messaging infrastructure: uses AWS SQS emulated locally via LocalStack (`:4566`), swappable to Google Cloud Pub/Sub topics under the `gcp` profile (never deployed on AWS cloud infrastructure).
- **Docker Compose Orchestration (`docker-compose.yml`)**:
  - Configured multi-container topology orchestrating all 8 microservices and dependencies with environment variable injection, network bridging, and healthcheck dependencies.
