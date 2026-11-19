# System Validation Report — Online Exam Portal

**Assessment date:** 2026-11-19
**Status:** BLOCKED / NOT EXECUTED

## Environment and scope

The planned system validation covers the end-to-end student, teacher, and administrator workflows using the application with PostgreSQL 15 and Redis 7. The repository provides a Docker Compose configuration for these dependencies.

## Result

System validation could not be performed in this environment because Docker is not installed/available, so the PostgreSQL and Redis services could not be started. No end-to-end workflow is claimed as passed. This is an environment blocker, not a passing test result.

| Area | Expected validation | Result |
|---|---|---|
| Service startup and migrations | API starts; Flyway applies migrations to an empty database. | Not run |
| Registration, login, lockout | Persisted accounts, token sessions, rate limiting and lockout behave as specified. | Not run |
| Teacher exam authoring | Create draft, add questions, validate totals, publish. | Not run |
| Student attempt | Start exam, save answers/review flags, enforce timing and submit. | Not run |
| Group eligibility and question bank | Create groups, add students, select bank questions, enforce group-only access. | Not run; code is present but needs live database verification |
| Results and reports | Persist score and display/export results and metrics. | Not run |
| Administrator functions | Restrict summary and account management to admins. | Not run |
| Backup/restore | Run encrypted daily backup and time an isolated restore drill. | Not run; Docker volume and PostgreSQL were unavailable |

## Required follow-up

Run `docker compose up --build`, execute the test cases in `UPDATED_TEST_CASES.md` through the UI/API, record each actual outcome and evidence, then update this report. Do not submit this blocked report as proof that system testing passed.
