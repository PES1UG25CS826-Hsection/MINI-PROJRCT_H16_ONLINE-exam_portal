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

Use only records and credentials created for the demonstration. Do not imply that a simulated usability study represents student research. Browser evidence covers Playwright Chromium, Firefox, WebKit and Microsoft Edge with stubbed API responses; it is not a live browser integration demonstration. WebKit does not establish Safari compatibility.

## Reproducible checks

```powershell
cd backend
mvn -B verify '-Dspring-boot.repackage.skip=true'
cd ..\frontend
npm ci
npx playwright install chromium firefox webkit
npm run build
npm run test:browser
```

The backend `verify` run enforces a 70% JaCoCo line-coverage floor and executes the PostgreSQL Testcontainers migration/persistence tests when Docker is available. The browser suite targets installed Microsoft Edge. The latest recorded evidence is in `CODE_COVERAGE_REPORT.md`, `NFR06_BROWSER_COMPATIBILITY.md`, `NFR09_BACKUP_RESTORE_REPORT.md`, and `NFR10_ACCESSIBILITY_REPORT.md`.

## Evidence limitations for the demonstration

- The five-person usability table is explicitly simulated; recruit real students and fill in `NFR05_REAL_STUDY_TEMPLATE.md` for actual evidence.
- Automated browser tests passed in Playwright Chromium 159.0.8154.6, Firefox 159.0, WebKit 27.2 and Microsoft Edge 157.0.4341.60 with API stubs. The system Chrome channel, Safari, and manual screen/device testing were not performed.
- Axe checks covered sign-in and Student dashboard only. Keyboard-only, contrast measurement, screen-reader checks and full WCAG 2.1 AA audit remain outstanding.
- An isolated encrypted backup/restore drill passed in approximately 4.8 seconds with schema, Flyway version and user-count checks. Off-site backup was not tested, and full-system recovery time was not measured.
- A demonstration video is recorded in prior commit `df1a448`; its playback and contents were not rechecked during this pass. These test records do not constitute faculty acceptance or production deployment validation.
