# ☕ Signal — Backend Services

<p align="left">
  <img src="https://skillicons.dev/icons?i=java,spring,postgres,docker,redis,aws" />
</p>

---

## 📖 Overview

**Signal Backend** is a multi-module microservice platform built on **Spring Boot 3**, **Java 21**, and **Docker**. It orchestrates real-time message polling from Slack, Gmail, Outlook and Calendar, applies intelligent scoring and prioritization, and delivers targeted email digest notifications — all running inside a fully containerized environment.

---

## 🏗️ Microservices

| Service | Port | Responsibility |
|---|---|---|
| 🔀 **Gateway Service** | `8080` | Reverse proxy, JWT auth filter, centralized security. |
| 🔑 **Auth Service** | `8081` | OAuth 2.0 login flow and JWT credential generation. |
| 📥 **Integration Service** | `8082` | Real-time polling connectors for Slack, Gmail, Outlook. |
| 🧠 **Scoring Service** | `8083` | AI priority ranking engine based on user preferences. |
| 📋 **Triage Service** | `8084` | Preferences, digest scheduling, SMTP delivery. |
| 📈 **Analytics Service** | `8085` | Team workspace volume and communication stats. |

---

## ✨ Features

- **📧 Real SMTP Email Digest** — Authenticated Gmail SMTP delivery with SSL trust config and native DB user email lookup.
- **⏰ IST-Aware Scheduler** — Cron-based digest dispatching that runs on Indian Standard Time (`Asia/Kolkata`) inside Docker containers.
- **🐳 Full Container Orchestration** — One-command `docker-compose` setup spinning up Postgres, Redis, and LocalStack (AWS SQS/S3 mock).
- **🔒 JWT Security** — Gateway-enforced token validation protecting all downstream services.

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (Virtual Threads) |
| Framework | Spring Boot 3, Spring Security |
| Database | PostgreSQL (Neon cloud + local Docker) |
| Messaging | AWS SQS (via LocalStack for local dev) |
| Cache | Redis |
| Email | JavaMailSender + Gmail SMTP |
| Container | Docker, Docker Compose |

---

## 🚀 Getting Started

### Prerequisites
- [Docker Desktop](https://www.docker.com/)
- [JDK 21](https://adoptium.net/)
- [Maven 3.8+](https://maven.apache.org/)

### Environment Setup
Create a `.env` file in the `backend/` directory:

```properties
# Gmail SMTP
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=yourmail@gmail.com
SPRING_MAIL_PASSWORD=your_16_char_app_password
```

### Build & Deploy
```bash
# Build all service JARs
.\mvnw.cmd clean package -DskipTests

# Start all containers
docker-compose --env-file .env up -d --build
```

### View Logs
```bash
docker logs -f triage-service
```
