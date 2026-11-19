# System Validation Report — Online Exam Portal

**Assessment date:** 2026-11-20

**Status:** PARTIAL — isolated candidate-stack and browser workflows passed; full acceptance matrix remains open.

## Environment and scope

A disposable Docker Compose stack ran PostgreSQL 15.19 and Redis 7 with the candidate application. Flyway applied migrations V1–V2 on a fresh database, the frontend returned HTTP 200, and `/actuator/health` returned `UP`. The live smoke run used a pushed auth-fix branch; the browser workflow used the separately pushed form-fix branch. These candidate commits are not merged to main.

`mvn -B verify` on the auth integration candidate passed 47 tests with 0 failures and 0 errors. Two PostgreSQL/Testcontainers tests were skipped because the Maven container had no host Docker socket. JaCoCo measured 41.6% line coverage and 31.0% branch coverage, below the 70% target.

## Results

| Area | Result | Evidence / limitation |
|---|---|---|
| Service startup and migrations | Passed on isolated stack | PostgreSQL 15.19 and Redis healthy; Flyway V1–V2 applied on a fresh database; frontend HTTP 200; actuator `UP`. |
| Registration and authenticated requests | Passed for teacher/student smoke | Registration/login, `/me`, bearer-protected flows passed on auth-fix candidate. Rate-limit and temporary lockout behavior were not retested in this smoke. |
| Teacher authoring | Passed in browser/API candidate flow | Group creation/member addition, draft exam/question creation, assignment and publish passed. |
| Student attempt | Passed in browser/API candidate flow | Assigned published exam visible; start, answer save, submit, and result display passed. Server deadline expiry and reconnect behavior were not tested. |
| Group eligibility and authorization | Partially passed | Assigned student saw and started the exam; student access to teacher group API returned 403. Full unassigned/unpublished matrix remains. |
| Reports and CSV | Passed in API smoke and browser report | Teacher report and CSV export returned successfully in live API smoke; teacher report dialog displayed in browser workflow. |
| Administrator functions | Partial | Admin summary and account creation are implemented; a live authenticated admin workflow was not recorded in this run. |
| Accessibility and responsive layout | Focused pass | Edge 157 at 360px; axe-core reported zero WCAG 2.1 A/AA violations on audited routes, no horizontal overflow, and keyboard focus was checked after login and exam start. Other browsers and assistive technology remain untested. |
| Backup/restore | Restore drill passed separately | Encrypted PostgreSQL 15 restore recovered 13 public tables and Flyway v2 in about 4.8 seconds. Continuous daily cadence and an independent off-host copy remain unverified. |
| Coverage | Target not met | 41.6% line (177/425) and 31.0% branch (88/284); target is 70% line coverage. |

## Browser evidence

A scripted Edge workflow is recorded in [`demo/sprint2-demo.webm`](demo/sprint2-demo.webm). It covers student registration, teacher group/member setup, exam and question creation, assignment/publishing, a student attempt and result, and a teacher report. The 159-second video is silent and uses disposable local accounts. It does not count as a first-time-student usability study.

## Required follow-up

1. Run the latest-two Chrome, Firefox, Edge, and Safari matrix at 360px and a desktop viewport.
2. Conduct and document the five-person first-time usability study.
3. Raise measured line coverage to at least 70% and run the skipped database tests in Docker-enabled CI.
4. Review with assistive technology and verify color contrast for all rendered states.
5. Observe the scheduled backup over 24 hours and verify an independent off-host copy.
6. Complete remaining admin lifecycle, timer-expiry, unassigned/unpublished, and scoring boundary cases.
7. Retest after the candidate branches are reviewed and merged; no candidate branch is a substitute for mainline integration.
