# Assigned GitHub Issue Status

**Repository:** `PES1UG25CS826-Hsection/MINI-PROJRCT_H16_ONLINE-exam_portal`
**GitHub assignee checked:** `ManojGaonkar05`
**Checked:** 2026-11-19. All listed issues were open at the time of review.

| Issue | Summary | Evidence in this branch | Remaining before issue can be claimed complete |
|---|---|---|---|
| #48 | Project scaffold | Spring Boot, React/Vite, Compose, env sample, CI, run instructions; delivered in merged PRs #53 and #57. | Verify clean Compose startup and service health. |
| #49 | Schema/migrations | Flyway V1 baseline and V2 exam workflow migration; delivered in merged PRs #53 and #57. | Run clean migration against PostgreSQL 15 and validate schema. |
| #39 | Documentation/demo | README, deviations list, two-minute script. | Record and link actual demo video. |
| #38 | Accessibility | Skip link, focus-visible styles, labelled form controls, responsive styles. | Automated scan, full keyboard review, documented 4.5:1 contrast audit. |
| #36 | Daily backup/restore | Encrypted daily backup profile, retention, restore script and instructions. | Configure external storage, run backup and timed restore drill, record proof. |
| #35 | Unit coverage | Maven/JaCoCo and scoring unit tests in CI. | Coverage is 1.9% line and 2.8% branch; reach 70% with meaningful tests. |
| #34 | Browser support | Responsive layout with 360px minimum width. | Test latest two browser versions across Chrome/Firefox/Edge/Safari and record results. |
| #33 | Usability | First-use flows are present in the UI. | Run and record the required five-person study; 90%/3-minute target is unverified. |
| #32 | Per-attempt randomization | Stable deterministic question and option order per attempt when enabled. | Add workflow/integration tests proving stable order and answer mapping. |
| #31 | Admin summary | Admin-only persisted totals for users, exams, attempts. | Add authorization and empty-database integration tests. |
| #30 | Teacher reports | Average/high/low/pass percentage and escaped CSV for owned exams. | Add ownership, aggregation, and CSV integration tests. |
| #10 | Group assignment | Group/membership schema, teacher/admin management API/UI, draft exam assignment, and student list/start eligibility checks. | Run group visibility and direct-start rejection tests against PostgreSQL 15. |
| #9 | Question selection/negative marks | Reusable bank questions can be selected for drafts; negative marking is persisted and applied. | Add end-to-end scoring validation against PostgreSQL 15. |
| #8 | Question bank CRUD | Teacher UI/API supports create, list, edit, delete, and ownership/in-use protections. | Exercise CRUD and rejection cases against PostgreSQL 15. |
| #7 | Teacher exam creation | Draft exam fields and validation for dates, duration, and marks. `ExamControllerTest` checks request constraints, invalid windows/marks, and H2 persistence. `ExamControllerPostgresTest` migrates an empty PostgreSQL 15.19 database through Flyway V1–V3 and verifies persisted draft fields, audit event, and validation before writes. | Browser-level create flow remains unverified. |

PR #53 (TASK-01/TASK-02) and PR #57 (workflow and deliverables) were merged to `main` on 2026-11-19. GitHub Actions backend and frontend checks passed on the merge commit. Assigned issues remain open until their remaining evidence above is recorded; this report does not claim that unresolved verification work is complete.
