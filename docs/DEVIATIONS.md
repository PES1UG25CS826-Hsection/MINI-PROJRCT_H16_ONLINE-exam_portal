# Requirements Deviations and Validation Gaps

**Review date:** 2026-11-20

| Requirement / document | Current status | Gap or deviation | Evidence / next action |
|---|---|---|---|
| SRS platform: Java 17, Spring Boot 3 | Java target 17; Spring Boot 3.5.16 | Java target aligns to the SRS; exact Boot minor version was not prescribed. | `backend/pom.xml`, CI, and Temurin 17 Docker images. |
| FR-07 question bank CRUD | Implemented; focused tests and UI are available | Live demo verified exam question creation, but not full question bank CRUD in the current browser run. | Run CRUD, ownership, and deletion/archive cases against PostgreSQL 15. |
| FR-08 question selection and negative marking | Implemented; focused test suite passed | Live UI workflow did not exercise negative-score calculation end-to-end. | Add/retain PostgreSQL-backed scoring verification for penalty boundaries. |
| FR-09 exam groups and publishing | Candidate live flow passed | Create group, add student, assign group, publish, and student visibility/start passed; unauthorized student request to teacher group API returned 403. | Repeat against merged main and verify unassigned/unpublished cases against PostgreSQL. |
| FR-12 authoritative timer/auto-submit | Partial | Browser shows a timer and submits at expiry; server deadline enforcement and close/reconnect auto-submit are not validated. | Enforce deadline on every write/submit and add integration cases. |
| FR-05 full admin account management | Partial | Admin summary and account creation exist; edit/deactivate/delete flows are incomplete. | Complete guarded lifecycle operations and audit events. |
| NFR-05 five-person usability test | Not run | No real first-time participant timings are available. | Run with at least five students and record outcomes; do not infer success from developer use. |
| NFR-06 browser matrix | Partial | Edge 157 at 360px was checked; latest two versions of Chrome, Firefox, Edge, and Safari were not all tested. | Run and record the required eight-version browser matrix. |
| NFR-08 >=70% unit coverage | Not met | Auth-fix candidate measured 41.6% line and 31.0% branch coverage (47 tests; two database tests skipped). | Expand meaningful tests, document exclusions, and report a generated JaCoCo result >=70%. |
| NFR-09 daily backup / restore | Restore drill passed; cadence/off-host evidence pending | Encrypted restore on PostgreSQL 15 restored 13 public tables and Flyway v2 in about 4.8 seconds. A continuous 24-hour cadence and independent off-host copy have not been observed. | Preserve the timed drill report; verify schedule over 24 hours and an independent copy. |
| NFR-10 WCAG 2.1 AA | Focused automated and keyboard checks passed | Edge 157 at 360px with axe-core reported zero WCAG 2.1 A/AA violations on audited sign-in, teacher, group, question bank, exam creation, student, attempt, and results pages. Full manual screen-reader review and cross-browser review remain. | Feature commit `a40bccc`; complete assistive-technology and contrast review across supported browsers. |
| Part 3 system validation | Partial candidate-flow verification passed | Isolated Compose migration/startup and live API/UI flows passed on feature candidates. Maven Testcontainers checks still skip because the Maven container cannot access Docker. | Complete the full system case matrix and rerun database integration tests in a Docker-enabled CI environment. |
| TASK-04 final docs and demo | Documentation and recording available | README, deviations, presentation guide, and a 159-second silent workflow video are included. The video is a scripted demonstration, not a usability study. | See `docs/PRESENTATION_GUIDE.md` and `docs/demo/sprint2-demo.webm`; test the feature-branch combination described there. |
