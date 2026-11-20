# Online Exam Portal

A role-based online exam portal built with React, Spring Boot, PostgreSQL, and Redis. This repository is an active mini-project submission; see the validation status below before treating it as production-ready.

## Team

| Name | SRN |
|---|---|
| Manoj A C | PES1UG25CS826 |
| Manoj Gaonkar | PES1UG25CS827 |
| Ruthik | PES1UG25CS837 |

## Project backlog

- Functional, non-functional, and security requirements are tracked as [GitHub issues](../../issues).
- The [project board](https://github.com/users/PES1UG25CS826-Hsection/projects/1) tracks sprint work.
- Each assigned item should be submitted through a pull request linked to its issue.

## Planning highlights

- The Phase 1 documents define 21 functional, 10 non-functional, and 10 security requirements.
- The submitted architecture includes use case, component, and sequence diagrams.
- The Test Plan provides the requirement-to-test-case traceability baseline; Phase 3 updates are in `docs/`.

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
4. Open <http://localhost:3000>. The web container proxies `/api/` and `/actuator/` to the API service on port 8080 inside the Compose network; health checks are at <http://localhost:3000/actuator/health> and <http://localhost:3000/api/health>.
5. Stop with `docker compose down`. Adding `-v` also deletes local database and Redis data.

Student accounts can be registered from the UI. Admin and teacher accounts are only seeded when their environment variables are explicitly configured; the app has no default credentials. Change demo passwords before sharing a running instance.

### Encrypted database backups

Set `BACKUP_AGE_RECIPIENT` in `.env` to an `age` public key and run `docker compose --profile backup up -d --build`. The backup service takes an encrypted PostgreSQL custom-format dump immediately, then schedules subsequent dumps no more than 24 hours apart, keeping 30 days by default. Backups are stored in the Docker `backup_data` volume. Copy them to an independent, access-controlled storage location for protection from host or volume loss. Create and protect the corresponding age identity separately; never commit it. See [`ops/backup/README.md`](ops/backup/README.md) for restore steps. A restore drill has not yet been executed for this submission, so the 4-hour RTO is not verified.

## Local development

- Backend: install JDK 17 and Maven, start PostgreSQL 15 and Redis 7, set `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`, and `REDIS_PORT`, then run `mvn spring-boot:run` from `backend/`.
- Frontend: install Node.js 22, then run `npm ci`, `npm run dev` from `frontend/`.
- Backend checks and coverage: run `mvn verify` from `backend/`. The JaCoCo HTML report is generated under `backend/target/site/jacoco/`.
- Frontend production build: run `npm ci` and `npm run build` from `frontend/`.

CI runs the backend Maven verification and frontend production build on pushes and pull requests. Database migrations are applied by Flyway at API startup; Hibernate validates the schema rather than creating tables automatically.

## Current implementation

The current code includes student registration and login, token-based sessions, login throttling/temporary lockout, role-protected teacher/admin APIs, a reusable question bank, exam draft/publish and student-group assignment, student exam attempts with answer saving and review flags, scoring/results, teacher metrics/CSV export, an admin summary/user creation, and a responsive role-based web UI.

## Validation status (2026-11-19)

- Backend: Maven `verify` completed; 5 scoring unit tests passed, 0 failed.
- Frontend: Vite production build completed successfully.
- System validation: not run. Docker was unavailable in the build environment, so PostgreSQL/Redis-backed workflows were not exercised end-to-end.
- NFR-03 monthly availability: not verified. No deployment URL, historical logs, or complete month of probe records are available. The proposed deviation is documented in [`docs/NFR-03_DEVIATION.md`](docs/NFR-03_DEVIATION.md); it requires instructor/project-owner approval and does not claim the 99.5% target was met.
- Backend line coverage: 1.9%; branch coverage: 2.8%. The Test Plan target of 70% is not met. Current tests cover only the scoring service.
- The group and question-bank workflows are implemented in code but have not yet been validated end-to-end. Scheduling/timer edge cases, security tests, accessibility checks, and load/recovery targets also require further evidence.
- The automated encrypted backup profile and restore procedure are documented, but no timed restore drill or external backup copy has been verified.

See [`docs/UPDATED_TEST_CASES.md`](docs/UPDATED_TEST_CASES.md) and the four reports in [`docs/`](docs/) for the actual test evidence and pending validation. Reports clearly distinguish passing unit tests from system cases that have not been executed.

See [`docs/DEVIATIONS.md`](docs/DEVIATIONS.md) for known scope and evidence gaps, and [`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md) for the two-minute demo script. The script is not a recorded demo video.

## Project documents

- [Software Requirements Specification](SRS.pdf)
- [Test Plan](Test_Plan.pdf)
- [Architecture and Design](Architecture_Design.pdf)
- [Part 3 test cases and reports](docs/)

## Database tables

The Flyway migrations define users, questions, exams, exam-question assignments, attempts, saved answers, results, and audit events. Use additive migrations under `backend/src/main/resources/db/migration/` for schema changes.
