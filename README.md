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

Set `BACKUP_AGE_RECIPIENT` in `.env` to an `age` public key and run `docker compose --profile backup up -d --build`. The backup service takes an encrypted PostgreSQL custom-format dump immediately, then schedules subsequent dumps no more than 24 hours apart, keeping 30 days by default. Backups are stored in the Docker `backup_data` volume. Copy them to an independent, access-controlled storage location for protection from host or volume loss. Create and protect the corresponding age identity separately; never commit it. See [`ops/backup/README.md`](ops/backup/README.md) for restore steps. A PostgreSQL 15 restore drill restored 13 public tables and Flyway version 2 in about 4.8 seconds on a disposable local stack. The four-hour RTO was met in that drill; the 24-hour backup cadence and an independent off-host copy still need operational evidence.

## Local development

- Backend: install JDK 17 and Maven, start PostgreSQL 15 and Redis 7, set `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`, and `REDIS_PORT`, then run `mvn spring-boot:run` from `backend/`.
- Frontend: install Node.js 22, then run `npm ci`, `npm run dev` from `frontend/`.
- Backend checks and coverage: run `mvn verify` from `backend/`. The JaCoCo HTML report is generated under `backend/target/site/jacoco/`.
- Frontend production build: run `npm ci` and `npm run build` from `frontend/`.

CI runs the backend Maven verification and frontend production build on pushes and pull requests. Database migrations are applied by Flyway at API startup; Hibernate validates the schema rather than creating tables automatically.

## Current implementation

The current code includes student registration and login, token-based sessions, login throttling/temporary lockout, role-protected teacher/admin APIs, a reusable question bank, exam draft/publish and student-group assignment, student exam attempts with answer saving and review flags, scoring/results, teacher metrics/CSV export, an admin summary/user creation, and a responsive role-based web UI.

## Validation status (2026-11-20)

- Backend auth-fix candidate: `mvn -B verify` passed 47 tests with 0 failures and 0 errors; 2 PostgreSQL/Testcontainers tests were skipped because the Maven container could not access Docker. JaCoCo measured 41.6% line coverage and 31.0% branch coverage; the 70% line target is not met.
- Infrastructure: an isolated Compose stack reported PostgreSQL 15.19 and Redis healthy, applied Flyway V1–V2 on a fresh database, returned HTTP 200 from the frontend, and reported `UP` at `/actuator/health`.
- Live API workflow on the auth-fix candidate passed teacher/student registration and login, group/member management, exam/question creation, group assignment and publishing, student visibility and attempt, answer save/submit, teacher report and CSV. A student request to teacher group management correctly returned 403.
- Browser workflow on the form-fix candidate passed group creation and member addition, draft and question creation, assignment and publishing, a student attempt and submission, results, and the teacher report. See [`docs/PRESENTATION_GUIDE.md`](docs/PRESENTATION_GUIDE.md) and the [recorded demo](docs/demo/sprint2-demo.webm).
- Edge 157 at 360px with axe-core reported zero WCAG 2.1 A/AA violations on the audited sign-in, teacher, group, question-bank, exam creation, student, attempt, and results screens; no horizontal overflow was found. This is a focused scan, not a full assistive-technology or browser matrix.
- Remaining evidence: 70% coverage, latest-two Chrome/Firefox/Edge/Safari matrix, real first-time-student usability study, broader assistive-technology review, backup-cadence observation, and independent off-host backup copy. Candidate fixes and tests are on feature branches and have not been merged to main; consult the completion tracker for branch and issue status.

See [`docs/UPDATED_TEST_CASES.md`](docs/UPDATED_TEST_CASES.md), [`docs/DEVIATIONS.md`](docs/DEVIATIONS.md), [`docs/SYSTEM_VALIDATION_REPORT.md`](docs/SYSTEM_VALIDATION_REPORT.md), and the reports in `docs/` for detailed evidence and gaps.
## Project documents

- [Software Requirements Specification](SRS.pdf)
- [Test Plan](Test_Plan.pdf)
- [Architecture and Design](Architecture_Design.pdf)
- [Part 3 test cases and reports](docs/)

## Database tables

The Flyway migrations define users, questions, exams, exam-question assignments, attempts, saved answers, results, and audit events. Use additive migrations under `backend/src/main/resources/db/migration/` for schema changes.
