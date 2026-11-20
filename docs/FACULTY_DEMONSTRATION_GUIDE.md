# Faculty Demonstration Guide

## Start the application

1. Install Docker Desktop with Compose and Node.js 22 for running browser tests.
2. Copy `.env.example` to `.env`; set a private `POSTGRES_PASSWORD`.
3. For a local faculty demo only, set `DEMO_ACCOUNTS_ENABLED=true` and provide distinct private passwords of at least 12 characters for `DEMO_ADMIN_PASSWORD`, `DEMO_TEACHER_PASSWORD`, and `DEMO_STUDENT_PASSWORD`. Use local email addresses in the matching `DEMO_*_EMAIL` variables. Do not commit `.env` or use the credentials on a public/shared service.
4. From the repository root, run `docker compose up --build` and wait for the database health check and API startup.
5. Open <http://localhost:3000>; health endpoints are `/actuator/health` and `/api/health` on the same host.

## Suggested live walkthrough

1. On the sign-in screen, choose **Create account**, register a new Student, then return to sign-in and authenticate.
2. Show the Student dashboard and available exams. Use seeded demo content or create and publish an exam from the Teacher account first.
3. As Teacher, create a draft exam, select/add questions, configure its schedule and marks, assign a group, then publish it.
4. As Student, start the exam, answer a question, save or mark it for review, and submit.
5. Return as Teacher to show a result report and CSV action; as Admin show summary counts and account administration.
6. Mention that public signup is Student-only and server authorization protects Teacher/Admin APIs.

Use only records and credentials created for the demonstration. Do not imply that a simulated usability study represents student research. Browser evidence currently covers Microsoft Edge 157 with stubbed API responses; it is not a live browser integration demonstration.

## Reproducible checks

```powershell
cd backend
mvn -B verify '-Dspring-boot.repackage.skip=true'
cd ..\frontend
npm ci
npm run build
npm run test:browser
```

The backend `verify` run enforces a 70% JaCoCo line-coverage floor and executes the PostgreSQL Testcontainers migration/persistence tests when Docker is available. The browser suite targets installed Microsoft Edge. The latest recorded evidence is in `CODE_COVERAGE_REPORT.md`, `NFR06_BROWSER_COMPATIBILITY.md`, `NFR09_BACKUP_RESTORE_REPORT.md`, and `NFR10_ACCESSIBILITY_REPORT.md`.

## Evidence limitations for the demonstration

- The five-person usability table is explicitly simulated; recruit real students and fill in `NFR05_REAL_STUDY_TEMPLATE.md` for actual evidence.
- Only Microsoft Edge 157 automated browser tests were run; Chrome, Firefox, Safari and manual screen/device testing were not performed.
- Axe checks covered sign-in and Student dashboard only. Keyboard-only, contrast measurement, screen-reader checks and full WCAG 2.1 AA audit remain outstanding.
- A real encrypted backup/isolated restore drill, off-site backup and measured recovery-time test were not completed in this run.
- These test records do not constitute faculty acceptance or production deployment validation.
