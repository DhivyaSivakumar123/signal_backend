# ☕ Signal Backend Services

<p align="center">
  <img src="https://img.shields.io/badge/Java_21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker" />
  <img src="https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white" alt="Redis" />
  <img src="https://img.shields.io/badge/AWS_SQS-FF9900?style=for-the-badge&logo=amazonsqs&logoColor=white" alt="AWS SQS" />
</p>

---

## 📖 Overview

Welcome to the **Signal Backend**! This is a multi-module microservice platform built using **Spring Boot**, **Java 21**, and **Docker**. The platform orchestrates message polling, data ingestion pipelines, natural language processing prioritization, user preferences, team diagnostics, and email digest notifications.

---

## 🏗️ Architecture & Modules

The backend consists of six microservices structured to communicate via REST and SQS message queues:

| Service | Port | Description |
|---|---|---|
| **🔀 Gateway Service** | `8080` | Reverse proxy and centralized security gateway. |
| **🔑 Auth Service** | `8081` | OAuth verification and JWT credentials generation. |
| **📥 Integration Service** | `8082` | Polling connectors for Slack, Gmail, and Outlook. |
| **🧠 Scoring Service** | `8083` | Priority calculation based on custom preferences. |
| **📋 Triage Service** | `8084` | User preferences, scheduling, and email summaries. |
| **📈 Analytics Service** | `8085` | Workspace team volume statistics. |

---

## 🌟 Advanced Features

- **📧 Real SMTP Digest Dispatcher:** End-to-end delivery of priority updates directly to user emails via Gmail SMTP using Secure STARTTLS and database lookup.
- **⏰ IST-Aware Scheduler:** Precise cron execution supporting local Indian Standard Time (IST / `Asia/Kolkata`) zones inside virtual containers.
- **🐳 Multi-Container Orchestration:** Fully containerized setup, running Postgres schemas, Redis servers, and LocalStack (mock SQS/S3 environment) out of the box.

---

## 🚀 Getting Started

### 📋 Prerequisites
Ensure you have the following installed:
- [Docker & Docker Desktop](https://www.docker.com/)
- [Java Development Kit (JDK) 21](https://adoptium.net/)
- [Maven v3.8+](https://maven.apache.org/)

### ⚙️ Environment Configuration
Create a `.env` file in the root backend directory to store your credentials:
```properties
# Database Credentials
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=yourpassword
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/signal_db

# Spring Mail SMTP Settings
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=yourmail@gmail.com
SPRING_MAIL_PASSWORD=your_16_char_app_password
```

### 🐳 Deploying Services
1. Build JAR packages:
   ```bash
   .\mvnw.cmd clean package -DskipTests
   ```
2. Build and launch all Docker containers:
   ```bash
   docker-compose --env-file .env up -d --build
   ```

### 📊 Monitoring Logs
Inspect active logs from the triage microservice:
```bash
docker logs -f triage-service
```
