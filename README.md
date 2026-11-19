# Online Exam Portal

A web portal for creating, conducting, and evaluating online exams. The system is intended for students, teachers/examiners, and administrators.

## Stack

- **API:** Java 21, Spring Boot 3, Spring Data JPA, Spring Actuator
- **Web:** React and Vite single-page application
- **Data:** PostgreSQL 15 with versioned Flyway migrations; Redis 7
- **Local orchestration:** Docker Compose

## Run locally

1. Install Docker Desktop with Compose enabled.
2. Copy `.env.example` to `.env`. Change `POSTGRES_PASSWORD` and `TOKEN_SECRET` before sharing this environment.
3. Start the services with `docker compose up --build`.
4. Open <http://localhost:3000>. The API health endpoint is <http://localhost:3000/actuator/health>; the API is also available on port 8080 inside the Compose network.
5. Stop the services with `docker compose down`. Add `-v` only when you also want to delete the local Postgres and Redis data volumes.

The API runs Flyway migrations at startup. Hibernate is set to `ddl-auto: validate`, so schema changes belong in a new migration under `backend/src/main/resources/db/migration/` rather than automatic table generation. Set `TOKEN_SECRET` to a unique random value of at least 32 characters before sharing or deploying the service; the built-in value is only for local development.

For development without Compose, run the API from `backend/` with `mvn spring-boot:run` and the web app from `frontend/` with `npm install && npm run dev`. Set `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`, and `REDIS_PORT` as needed.

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

- [Software Requirements Specification](SRS.pdf)
- [Test Plan](Test_Plan.pdf)
- [Architecture and Design](Architecture_Design.pdf)

The documents describe the original Part-1 requirements and design. The application scaffold is the starting point for implementing the portal workflows.

## API starter endpoint

- `GET /api/health` returns a small service status response.
- Spring Actuator exposes health at `/actuator/health`.

## Student workflow API

- `POST /api/auth/register` creates a student account; `POST /api/auth/login` returns a 12-hour bearer token.
- `GET /api/student/exams` returns assigned, published assessments in `upcoming`, `available`, and `completed` groups.
- `POST /api/exams/{examId}/attempts` starts or resumes the student's in-progress attempt. Eligibility and attempt limits are checked on the server.
- `GET /api/attempts/{attemptId}` returns a server-timed attempt, saved answers, and question options without answer keys.
- `PUT /api/attempts/{attemptId}/answers/{questionId}` stores the answer and review flag. Updates are idempotent and rejected after the server deadline plus the 10-second grace period.
- `POST /api/attempts/{attemptId}/submit` grades once, stores the result, and makes the attempt read-only. The expiry worker also submits attempts when their server deadline passes.
- `GET /api/attempts/{attemptId}/result` returns score, percentage, and pass/fail; per-question review is returned only when enabled on the exam.

Answer state is in PostgreSQL, so reconnecting to another API instance restores the same attempt. API instances are stateless and share the database; the Nginx proxy resolves the `api` service name dynamically. For a local multi-instance check, run `docker compose up --build --scale api=3`. This shares one database and Redis service across the replicas.

## Performance and availability evidence

See [`ops/performance/README.md`](ops/performance/README.md) for the 500-user request workload and result timing thresholds, and [`ops/monitoring/README.md`](ops/monitoring/README.md) for the uptime probe and monthly report. These scripts produce reproducible evidence, but a real deployment and a complete monitoring month are required to claim the 500-user performance and 99.5% availability targets. No production environment or measured month is included in this repository, so those measured acceptance criteria remain pending real environment evidence.
