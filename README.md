# Online Exam Portal

A role-based online exam portal built with React, Spring Boot, PostgreSQL, and Redis. This repository is an active mini-project submission; see the validation status below before treating it as production-ready.

## Technology

- Java 17 and Spring Boot 3
- React 19 and Vite
- PostgreSQL 15 with Flyway migrations
- Redis 7 for bearer sessions and login rate limiting
- Docker Compose for local services

## Run with Docker Compose

1. Install Docker Desktop with Compose enabled.
2. Copy `.env.example` to `.env`. Set a unique `POSTGRES_PASSWORD`. To create demo admin/teacher accounts, supply both email and a password of at least 12 characters for each. Leave these values blank to disable the optional accounts.
3. Run `docker compose up --build` from the repository root.
4. Open <http://localhost:3000>. The API is available at <http://localhost:8080>; health checks are at `/actuator/health` and `/api/health`.
5. Stop with `docker compose down`. Adding `-v` also deletes local database and Redis data.

Student accounts can be registered from the UI. Admin and teacher accounts are only seeded when their environment variables are explicitly configured; the app has no default credentials. Change demo passwords before sharing a running instance.

## Local development

- Backend: install JDK 17 and Maven, start PostgreSQL 15 and Redis 7, set `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`, and `REDIS_PORT`, then run `mvn spring-boot:run` from `backend/`.
- Frontend: install Node.js 22, then run `npm ci`, `npm run dev` from `frontend/`.
- Backend checks and coverage: run `mvn verify` from `backend/`. The JaCoCo HTML report is generated under `backend/target/site/jacoco/`.
- Frontend production build: run `npm ci` and `npm run build` from `frontend/`.

CI runs the backend Maven verification and frontend production build on pushes and pull requests. Database migrations are applied by Flyway at API startup; Hibernate validates the schema rather than creating tables automatically.

## Current implementation

The current code includes student registration and login, token-based sessions, login throttling/temporary lockout, role-protected teacher/admin APIs, exam draft and publish actions, supported question types, student exam attempts with answer saving and review flags, scoring/results, teacher metrics/CSV export, an admin summary/user creation, and a responsive role-based web UI.

## Validation status (2026-11-19)

- Backend: Maven `verify` completed; 5 scoring unit tests passed, 0 failed.
- Frontend: Vite production build completed successfully.
- System validation: not run. Docker was unavailable in the build environment, so PostgreSQL/Redis-backed workflows were not exercised end-to-end.
- Backend line coverage: 2.7%; branch coverage: 4.0%. The Test Plan target of 70% is not met. Current tests cover only the scoring service.
- The system is not yet validated against every SRS requirement. In particular, full group/eligibility assignment, all scheduling/timer edge cases, security tests, accessibility checks, and load/recovery targets require further implementation or evidence.

See [`docs/UPDATED_TEST_CASES.md`](docs/UPDATED_TEST_CASES.md) and the four reports in [`docs/`](docs/) for the actual test evidence and pending validation. Reports clearly distinguish passing unit tests from system cases that have not been executed.

## Project documents

- [Software Requirements Specification](SRS.pdf)
- [Test Plan](Test_Plan.pdf)
- [Architecture and Design](Architecture_Design.pdf)
- [Part 3 test cases and reports](docs/)

## Database tables

The Flyway migrations define users, questions, exams, exam-question assignments, attempts, saved answers, results, and audit events. Use additive migrations under `backend/src/main/resources/db/migration/` for schema changes.
