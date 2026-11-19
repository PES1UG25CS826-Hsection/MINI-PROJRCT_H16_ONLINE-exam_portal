# Assigned GitHub Issue Status

**Repository:** `PES1UG25CS826-Hsection/MINI-PROJRCT_H16_ONLINE-exam_portal`
**GitHub assignee checked:** `ManojGaonkar05`
**Checked:** 2026-11-20. GitHub currently shows 13 assigned issues open. Issues #48 and #49 were closed when the deliverables were merged through PR #57.

| Issue | Summary | Evidence in this branch | Remaining before issue can be claimed complete |
|---|---|---|---|
| #48 | Project scaffold (closed) | Spring Boot, React/Vite, Compose, env sample, CI, run instructions; merged through PR #57. | Verify clean Compose startup. |
| #49 | Schema/migrations (closed) | Flyway V1 baseline and V2/V3 workflow migrations; merged through PR #57. | Run clean migration against PostgreSQL 15 and validate schema. |
| #39 | Documentation/demo | README, deviations list, two-minute script. | Record and link actual demo video. |
| #38 | Accessibility | Skip link, focus-visible styles, labelled form controls, responsive styles. | Automated scan, full keyboard review, documented 4.5:1 contrast audit. |
| #36 | Daily backup/restore | Encrypted daily backup profile, retention, restore script and instructions. | Configure external storage, run backup and timed restore drill, record proof. |
| #35 | Unit coverage | Maven/JaCoCo and scoring unit tests in CI. | Coverage is 1.9% line and 2.8% branch; reach 70% with meaningful tests. |
| #34 | Browser support | Responsive layout with 360px minimum width. | Test latest two browser versions across Chrome/Firefox/Edge/Safari and record results. |
| #33 | Usability | First-use flows are present in the UI. | Run and record the required five-person study; 90%/3-minute target is unverified. |
| #32 | Per-attempt randomization | Stable deterministic question and option order per attempt when enabled. `AttemptRandomizationTest` verifies enabled ordering changes and stays stable across reads for an attempt, disabled ordering stays in stored order, saved answer values map through the randomized options, and answer keys are hidden before submission. | PostgreSQL 15 attempt reload/scoring integration remains unverified because the local Docker engine is unavailable. |
| #31 | Admin summary | Admin-only persisted totals for active users, exams, attempts, and submitted attempts. `AdminSummaryControllerTest` covers persisted counts, zero totals, and the admin-only controller authorization annotation. | PostgreSQL 15 persisted-count integration and authenticated HTTP authorization remain unverified because the local Docker engine is unavailable. |
| #30 | Teacher reports | Average/high/low/pass percentage and escaped CSV for owned exams. | Add ownership, aggregation, and CSV integration tests. |
| #10 | Group assignment | Group/membership schema, teacher/admin management API/UI, draft exam assignment, and student list/start eligibility checks. `ExamGroupControllerTest` covers assignment persistence, ownership, draft-only mutation, unassignment, and audit calls; `ExamStudentEligibilityTest` verifies published/group-filtered student listing and rejection of direct starts by unassigned students. | PostgreSQL 15 group visibility and direct-start integration tests remain unverified because the local Docker engine is unavailable. |
| #9 | Question selection/negative marks | Reusable bank question assignment persists order and points overrides; negative mark percentage is stored and applied by scoring. `QuestionSelectionControllerTest` covers append order/override persistence, duplicate and published-exam rejection, removal and audit behavior; `ExamScoringServiceTest` covers zero, 25%, 33%, and 100% penalties, unanswered and multi-select answers, and final-score floor. | PostgreSQL 15 migration and persisted workflow/scoring integration remain unverified because the local Docker engine is unavailable. |
| #8 | Question bank CRUD | Teacher UI/API supports create, list, edit, delete, and ownership/in-use protections. `QuestionBankControllerTest` covers all three types, list filtering, update/delete paths, ownership/use rejection, and validation errors. | PostgreSQL 15 CRUD/migration integration and browser workflow remain unverified locally. |
| #7 | Teacher exam creation | Draft exam fields and validation for dates, duration, and marks. `ExamControllerPostgresTest` migrates an empty PostgreSQL 15.19 database through Flyway V1–V3 and verifies persisted draft fields, audit event, and validation before writes. | Browser-level create flow remains unverified. |

PR #53 (TASK-01/TASK-02) and PR #57 (workflow and deliverables) were merged to `main` on 2026-11-19. GitHub Actions backend and frontend checks passed on the merge commit. Issues #48 and #49 closed with that merge; the other 13 assigned issues remain open pending the stated verification or evidence. This report does not claim unresolved work is complete.
