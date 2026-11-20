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
2. Copy `.env.example` to `.env` and set a unique `POSTGRES_PASSWORD`. Public registration creates Student accounts only. Demo accounts are disabled by default. To enable local Admin, Teacher, and Student demo accounts, set `DEMO_ACCOUNTS_ENABLED=true` and set each `DEMO_*_EMAIL` plus a private `DEMO_*_PASSWORD` of at least 12 characters. Keep those secrets only in `.env`; never commit them. Seeding hashes passwords and skips existing email addresses.
3. Run `docker compose up --build` from the repository root.
4. Open <http://localhost:3000>. The web container proxies `/api/` and `/actuator/` to the API service on port 8080 inside the Compose network; health checks are at <http://localhost:3000/actuator/health> and <http://localhost:3000/api/health>.
5. Stop with `docker compose down`. Adding `-v` also deletes local database and Redis data.

### Registration and sign in

Open the portal and choose **Create account**. Enter a full name, valid email, and a password with at least 8 characters, an uppercase letter, a number, and a symbol; confirm the password and register. Public registration always assigns the Student role. After the success message, use the returned **Sign in** link and log in with the new credentials. There is no password-recovery endpoint, so the login page does not show a nonfunctional recovery link.

For a local faculty demo, enable seeding and provide credentials in `.env` (examples are shown with blank password values in `.env.example`):

```dotenv
DEMO_ACCOUNTS_ENABLED=true
DEMO_ADMIN_EMAIL=admin@example.test
DEMO_ADMIN_PASSWORD=<private password of at least 12 characters>
DEMO_TEACHER_EMAIL=teacher@example.test
DEMO_TEACHER_PASSWORD=<private password of at least 12 characters>
DEMO_STUDENT_EMAIL=student@example.test
DEMO_STUDENT_PASSWORD=<private password of at least 12 characters>
```

Restart the API after changing these settings. The accounts are created once with BCrypt-hashed passwords; repeated starts do not duplicate existing email addresses. Teacher and Admin routes are enforced by backend role checks. Do not use demo credentials on a shared or public deployment.

### Encrypted database backups

Set `BACKUP_AGE_RECIPIENT` in `.env` to an `age` public key and run `docker compose --profile backup up -d --build`. The backup service takes an encrypted PostgreSQL custom-format dump immediately, then schedules subsequent dumps no more than 24 hours apart, keeping 30 days by default. Backups are stored in the Docker `backup_data` volume. Copy them to an independent, access-controlled storage location for protection from host or volume loss. Create and protect the corresponding age identity separately; never commit it. See [`ops/backup/README.md`](ops/backup/README.md) for restore steps. A restore drill has not yet been executed for this submission, so the 4-hour RTO is not verified.

## Local development

- Backend: install JDK 17 and Maven, start PostgreSQL 15 and Redis 7, set `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`, and `REDIS_PORT`, then run `mvn spring-boot:run` from `backend/`.
- Frontend: install Node.js 22, then run `npm ci`, `npm run dev` from `frontend/`.
- Backend checks and coverage: run `mvn verify` from `backend/`. The JaCoCo HTML report is generated under `backend/target/site/jacoco/`.
- Frontend production build: run `npm ci` and `npm run build` from `frontend/`.
- Frontend test script: no standalone frontend test runner is currently configured; use `npm run build` for the production compilation check.

CI runs the backend Maven verification and frontend production build on pushes and pull requests. Database migrations are applied by Flyway at API startup; Hibernate validates the schema rather than creating tables automatically.

## Current implementation

The current code includes student registration and login, token-based sessions, login throttling/temporary lockout, role-protected teacher/admin APIs, a reusable question bank, exam draft/publish and student-group assignment, student exam attempts with answer saving and review flags, scoring/results, teacher metrics/CSV export, an admin summary/user creation, and a responsive role-based web UI.

## Validation status (2026-11-20)

- Backend on the local authentication worktree: Maven `verify` passed with 72 tests, 0 failures/errors/skips. PostgreSQL 15 Testcontainers migration/persistence tests ran. JaCoCo reported 72.00% line and 50.71% branch coverage; the 70% line gate passed. Boot jar repackaging was skipped because Windows temporarily held the artifact open.
- Frontend: Vite production build passed against the modified authentication screens. No standalone frontend test runner is configured.
- Backend role checks verify that a Student cannot invoke teacher or Admin controller methods. Signup, hashed password handling, duplicate handling, seeding and bearer authentication have focused tests.
- An isolated PostgreSQL 15/Redis 7 smoke test verified signup and login, proper duplicate/weak-password statuses, BCrypt hashes, role denials, Admin/Teacher access and idempotent demo seeding after API restart. The full API/web Docker image build was not completed because Docker base-image downloads were very slow; the Vite production build and host API integration passed.
- Usability study, full cross-browser matrix and complete WCAG contrast audit remain evidence gaps. Backup restore evidence is recorded in `docs/ASSIGNED_ISSUE_STATUS.md`; external backup storage is deployment configuration.

See [`docs/UPDATED_TEST_CASES.md`](docs/UPDATED_TEST_CASES.md) and the four reports in [`docs/`](docs/) for the actual test evidence and pending validation. Reports clearly distinguish passing unit tests from system cases that have not been executed.

See [`docs/DEVIATIONS.md`](docs/DEVIATIONS.md) for known scope and evidence gaps, and [`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md) for the two-minute demo script. The script is not a recorded demo video.

## Project documents

- [Software Requirements Specification](SRS.pdf)
- [Test Plan](Test_Plan.pdf)
- [Architecture and Design](Architecture_Design.pdf)
- [Part 3 test cases and reports](docs/)

## Database tables

The Flyway migrations define users, questions, exams, exam-question assignments, attempts, saved answers, results, and audit events. Use additive migrations under `backend/src/main/resources/db/migration/` for schema changes.
