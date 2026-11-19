# Faculty Presentation Guide

This guide is for a local demonstration of the Online Exam Portal. It describes the current UI and the checks that have actually been recorded. Do not present an unrun workflow or a pending requirement as verified.

## 1. Start and stop the application

From the repository root, in PowerShell:

```powershell
Copy-Item .env.example .env
notepad .env
docker compose up --build
```

Set a local `POSTGRES_PASSWORD`. To seed demo admin and teacher accounts, set both their email and a unique password of at least 12 characters. Keep the file private and do not reuse real account credentials. Leave the optional seed values blank if you do not need those accounts. Student accounts can be registered from the sign-in page.

Wait for PostgreSQL and Redis health checks, then open <http://localhost:3000>. The API health checks are <http://localhost:3000/actuator/health> and <http://localhost:3000/api/health>. Flyway applies the versioned migrations at API startup; Hibernate validates the resulting schema.

Stop the services with:

```powershell
docker compose down
```

This preserves the local database and Redis volumes. Only use `docker compose down -v` when you intentionally want to delete all local database and Redis data.

## 2. Demonstration setup

Use fictional data and a disposable local database. Before the presentation:

1. Start the stack and confirm both health endpoints report healthy.
2. Register one student through the sign-in page.
3. Sign in as the seeded teacher and create a student group.
4. Add the registered student's email as a group member.
5. Prepare an exam schedule that includes the demonstration time.

The app has no default credentials. Do not show the local `.env` file or passwords in a screen recording.

## 3. Suggested workflow

### Teacher

1. Sign in with the demo teacher account.
2. Open **Create exam**. Enter a title, subject, duration from 5 to 240 minutes, start and end times, total marks, and passing marks. Set optional negative marking, attempt limit, answer visibility, or randomization.
3. Create the draft, add questions, select question type and marks, and enter answer options and correct answers.
4. Open **Student groups** and create a group if one is not already prepared. Add the registered student's email.
5. Return to the draft question editor, assign the group, then publish the exam from **Dashboard**.
6. Open **Report** to view teacher metrics or **CSV** to download the result rows after a student submits.

### Student

1. Register or sign in with the student account.
2. Open **Dashboard** and start the assigned, published exam during its configured time window.
3. Answer questions, use the review marker if needed, and submit the attempt.
4. Open **My results** to view the submitted result. Correct answers are shown only when the exam allows it.

### Administrator

Sign in with the seeded demo admin account. **Dashboard** displays saved user, exam, and attempt totals. **Student groups** and **Users** provide the corresponding management views.

## 4. Architecture and data

- **Frontend:** React 19 and Vite, served by Nginx. The browser sends API requests through the same-origin `/api/` proxy.
- **Backend:** Java 17 and Spring Boot 3 REST API. Role-aware endpoints cover teacher authoring, student attempts, reporting, and administrator summaries.
- **Database:** PostgreSQL 15. Flyway owns schema changes; migrations are additive. The schema stores users, questions, exams and exam-question ordering, groups, attempts, answers, results, and audit events.
- **Sessions and cache:** Redis 7 stores expiring bearer sessions and login-throttling state.
- **Local orchestration:** Docker Compose runs the web, API, PostgreSQL, and Redis services. The optional backup profile encrypts PostgreSQL dumps with age.

## 5. Verification evidence and limitations

The auth integration candidate passed `mvn -B verify`: 47 tests, 0 failures, 0 errors, with 2 PostgreSQL/Testcontainers tests skipped because Maven could not access the Docker socket. Its JaCoCo report measured 41.6% line coverage (177/425 lines) and 31.0% branch coverage (88/284 branches), below the 70% target. A live API smoke run against PostgreSQL 15 and Redis 7 passed teacher registration/login, group and member management, exam and question creation, assignment and publishing, student visibility, attempt save/submit, teacher report and CSV export; an unauthorized student group request returned 403. The auth correction is on a feature branch and is not merged to main.

An encrypted PostgreSQL backup restore drill on the separate backup feature branch restored 13 public tables and Flyway version 2 in about 4.8 seconds. This is a local drill; production recovery-point and recovery-time objectives have not been established.

A focused Edge 157 run at 360px used axe-core against sign-in, teacher dashboard, groups, question bank, exam creation, student dashboard, attempt, and results pages and reported zero WCAG 2.1 A/AA violations on those pages. The pages had no horizontal overflow; keyboard focus after sign-in and entering an exam was checked. This is not a full assistive-technology or browser-matrix audit. The latest Chrome, Firefox, Edge, and Safari compatibility matrix and a real-participant usability study are outstanding.

[Watch the scripted demonstration video](demo/sprint2-demo.webm) (159 seconds, 1280×720, silent). It uses disposable local accounts and covers student registration, teacher group creation and membership, draft exam and question creation, assignment and publishing, a student attempt and submission, results, and the teacher report. This recording is a workflow demonstration, not a usability study. The async form and sign-out navigation corrections and keyboard-scrollable table updates are pushed to feature branches but are not merged to main; use a checkout containing those branches and the auth fix for the demonstrated version.
## 6. Likely faculty questions

**Why use both PostgreSQL and Redis?** PostgreSQL stores durable portal records. Redis stores short-lived login sessions and throttling state.

**How are schema changes applied?** Flyway runs numbered SQL migrations when the API starts. Hibernate checks the schema without generating it.

**How are students restricted to an exam?** A teacher publishes the exam and assigns it to a group. Student listing and start checks require a published exam and group membership.

**How are backups protected?** The optional scheduled service writes age-encrypted PostgreSQL dumps and keeps a configurable retention period. The private age identity is kept outside the repository.

**What remains to verify?** The 70% coverage target, full browser matrix, real usability study, manual assistive-technology review, and production recovery objectives remain open. Present their status plainly.
