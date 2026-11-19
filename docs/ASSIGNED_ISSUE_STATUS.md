# Assigned GitHub Issue Status

**Repository:** `PES1UG25CS826-Hsection/MINI-PROJRCT_H16_ONLINE-exam_portal`
**GitHub assignee checked:** `ManojGaonkar05`
**Checked:** 2026-11-19. All listed issues were open at the time of review.

| Issue | Summary | Evidence in this branch | Remaining before issue can be claimed complete |
|---|---|---|---|
| #48 | Project scaffold | Spring Boot, React/Vite, Compose, env sample, CI, run instructions. | Merge/push PR and verify clean Compose startup. |
| #49 | Schema/migrations | Flyway V1 baseline and V2 exam workflow migration. | Run clean migration against PostgreSQL 15 and validate schema. |
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
| #8 | Question bank CRUD | Teacher UI/API supports create, list, edit, delete, and ownership/in-use protections. `QuestionBankControllerTest` covers all three types, list filtering, update/delete paths, ownership/use rejection, and validation errors. | PostgreSQL 15 CRUD/migration integration and browser workflow remain unverified locally. |
| #7 | Teacher exam creation | Draft exam fields and validation for dates, duration, marks. | Add persisted workflow tests and validate all acceptance cases. |

The code is on local branch `se/deliverables`, based on the open scaffold PR's commit. No issue was closed and no completion claim was sent to GitHub. Reports under `docs/` preserve actual pass/fail/block status.
