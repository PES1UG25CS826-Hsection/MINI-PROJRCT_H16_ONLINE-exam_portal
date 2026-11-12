# Online Exam Portal

A web portal for creating, conducting, and evaluating online exams. The system is intended for students, teachers/examiners, and administrators.

## Stack

- **API:** Java 21, Spring Boot 3, Spring Data JPA, Spring Actuator
- **Web:** React 18, TypeScript, and Vite single-page application
- **Data:** PostgreSQL 15 with versioned Flyway migrations; Redis 7
- **Local orchestration:** Docker Compose

## Run locally

1. Install Docker Desktop with Compose enabled.
2. Copy `.env.example` to `.env` and set `POSTGRES_PASSWORD` to a non-empty local password. Compose refuses to start without it. With an existing Postgres volume, use the password already stored for that database role; changing this environment variable alone does not rotate the database password. Keep `.env` private; it is ignored by Git.
3. Start the services with `docker compose up --build`.
4. Open <http://localhost:3000>. The API health endpoint is <http://localhost:3000/actuator/health>; the API is also available on port 8080 inside the Compose network.
5. Stop the services with `docker compose down`. Add `-v` only when you also want to delete the local Postgres and Redis data volumes.

The API runs Flyway migrations at startup. Hibernate is set to `ddl-auto: validate`, so schema changes belong in a new migration under `backend/src/main/resources/db/migration/` rather than automatic table generation.

For development without Compose, run the API from `backend/` with `mvn spring-boot:run` and the web app from `frontend/` with `npm ci && npm run dev`. Set `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`, and `REDIS_PORT` in the environment. The backend requires database username and password values; it has no built-in credentials.

## Environment variables

| Variable | Used by | Default / requirement |
|---|---|---|
| `POSTGRES_DB` | Compose / Postgres | `online_exam` |
| `POSTGRES_USER` | Compose / Postgres | `exam` |
| `POSTGRES_PASSWORD` | Compose / API | Required; set a private local value in `.env` |
| `POSTGRES_PORT` | Compose | `5432` on the host |
| `REDIS_PORT` | Compose | `6379` on the host |
| `WEB_PORT` | Compose | `3000` on the host |
| `DATABASE_URL` | API outside Compose | JDBC URL; Compose sets it to the `db` service |
| `DATABASE_USERNAME`, `DATABASE_PASSWORD` | API outside Compose | Required; no application fallback credentials |
| `REDIS_HOST`, `REDIS_PORT` | API outside Compose | Redis connection; Compose points to `redis:6379` |
| `BACKUP_AGE_RECIPIENT` | Optional backup profile | Required to enable encrypted backups |
| `BACKUP_INTERVAL_SECONDS` | Optional backup profile | `86400` (maximum 24 hours) |
| `BACKUP_RETENTION_DAYS` | Optional backup profile | `30` |

Do not commit `.env` or a private age identity. See [backup and restore instructions](ops/backup/README.md).

## Initial database model

`V1__initial_schema.sql` creates users, questions, exams, exam-question assignments, attempts, saved attempt answers, results, and an audit log. Identifiers are UUIDs, timestamps use `TIMESTAMPTZ`, structured answer data uses JSONB, and foreign keys and checks protect core relationships and state values. The migration is designed to run once on an empty database; later schema changes should be additive migrations.

| Table | Purpose |
|---|---|
| `users` | Student, teacher, and administrator accounts |
| `questions` | Authored questions, options, answer key, and points |
| `exams` / `exam_questions` | Exam settings and ordered question assignments |
| `attempts` / `attempt_answers` | Student exam sessions and saved answers |
| `results` | Final score and grading metadata for a submitted attempt |
| `audit_log` | Actor, action, entity, request IP, and structured event details |

## Project documents

| Document | File | Standard |
|---|---|---|
| Software Requirements Specification | [SRS.pdf](SRS.pdf) | IEEE 830 / 29148 |
| Test Plan (23 test cases and traceability) | [Test_Plan.pdf](Test_Plan.pdf) | IEEE 829 |
| Software Architecture and Design Specification | [Architecture_Design.pdf](Architecture_Design.pdf) | IEEE 1016 |

The documents describe the original Part-1 requirements and design. The application scaffold is the starting point for implementing the portal workflows.

The Part-1 documents cover 21 functional, 10 non-functional, and 10 security requirements; UML use-case, component, and sequence diagrams; and requirement-to-test traceability.

## Architecture and current implementation

The project is intended to be a modular monolith: HTTP controllers call application services, which use repositories backed by PostgreSQL. Flyway owns schema changes; Redis is available for cache or ephemeral state. The current API implementation is only a health endpoint and application startup. Authentication, role enforcement, question and exam workflows, student attempts, result computation, and reports have not yet been integrated. Do not treat the schema alone as evidence those workflows are implemented.

The approved SRS specifies Java 17. The scaffold currently builds with Java 21 (Maven and runtime images use Eclipse Temurin 21, and `pom.xml` sets `java.version` to 21). Spring Boot 3.5.16 supports Java 17 and later, so Java 17 is a compatible target; align the Maven property and both Docker image tags with 17 in a separately verified change before claiming SRS compliance. This version deviation is deliberate and documented pending that change.

The `backup` Compose service is opt-in. Configure its age recipient and follow [the encrypted backup setup and restore drill](ops/backup/README.md) before relying on it operationally.

## Two-minute demonstration script

1. **0:00–0:20** — Show the repository structure and explain the React/TypeScript client, Spring Boot API, PostgreSQL schema migrations, and Redis service.
2. **0:20–0:45** — Start the stack with `docker compose up --build`; show the web page at `http://localhost:3000` and API health at `http://localhost:3000/actuator/health`.
3. **0:45–1:10** — Show PostgreSQL and Redis health with `docker compose ps`, then explain that Flyway applies the initial schema on API startup.
4. **1:10–1:35** — Show the initial schema tables and the optional encrypted backup profile. Explain that restoration is verified into a temporary database, without deleting the active volumes.
5. **1:35–2:00** — State the current limitation clearly: sign-in and exam workflows are not implemented yet. Show the SRS and this documented Java 21 deviation from its Java 17 requirement.

This is a presentation script only; no demonstration video has been recorded.

## Team

- Manoj A C (PES1UG25CS826)
- Manoj Gaonkar (PES1UG25CS827)
- Ruthik (PES1UG25CS837)

## API starter endpoint

- `GET /api/health` returns a small service status response.
- Spring Actuator exposes health at `/actuator/health`.
