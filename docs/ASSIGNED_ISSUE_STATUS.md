# Faculty Task Status

**Repository:** `PES1UG25CS826-Hsection/MINI-PROJRCT_H16_ONLINE-exam_portal`
**Checked:** 2026-11-20. Status distinguishes implementation and local/merged evidence; an implemented feature is not marked accepted when its acceptance evidence is missing.

| Task ID | GitHub Issue | Implementation | Testing | Commit ID | Status |
|---|---:|---|---|---|---|
| TASK-01 | #48 | Spring Boot API, React/Vite SPA, Compose services, environment sample and CI merged in PR #53. | PR CI passed; clean-stack startup evidence still needs a current run. | `76ef1dc` | Merged; runtime recheck pending |
| FR-06 | #7 | Teacher draft exam creation and validation merged in PR #58. | PostgreSQL migration/persistence test and controller tests exist; browser flow not yet demonstrated in this run. | `7d5c361` | Implemented; browser evidence pending |
| FR-07 | #8 | Teacher question bank CRUD for MCQ, multi-select and true/false merged in PR #60. | Controller tests cover CRUD/validation/ownership; browser and PostgreSQL end-to-end evidence pending. | `ed64f92` | Implemented; integration evidence pending |
| FR-08 | #9 | Bank-question selection and negative-mark configuration/scoring merged in PR #61. | Controller/scoring tests exist; PostgreSQL workflow evidence pending. | `cc42042` | Implemented; integration evidence pending |
| FR-09 | #10 | Publish/unpublish and group assignment merged in PR #63. | Controller/eligibility tests exist; current full-flow evidence pending. | `1c9b090` | Implemented; integration evidence pending |
| TASK-02 | #49 | Flyway schema and workflow migrations merged in PR #53. | Migration tests exist; verify from an empty PostgreSQL 15 instance as part of the current demo run. | `76ef1dc` | Merged; clean database verification pending |
| FR-18 | #30 | Teacher reports and CSV export merged in PR #64. | Metrics, ownership and CSV escaping tests exist; live export flow pending. | `6969ca0` | Implemented; live-flow evidence pending |
| FR-19 | #31 | Admin summary merged in PR #65. | Summary tests cover persisted count queries and empty totals; authenticated HTTP check pending. | `cb8ce86` | Implemented; HTTP authorization evidence pending |
| FR-20 | #32 | Per-attempt question/option randomization merged in PR #66. | Randomization tests cover ordering stability, answer mapping and hidden keys; PostgreSQL attempt reload/scoring evidence pending. | `334141a` | Implemented; integration evidence pending |
| NFR-05 | #33 | First-use guidance/usability work recorded locally. | The required five-person, timed first-use study has not been run. | `4b18831` (local) | Incomplete; study evidence required |
| NFR-06 | #34 | Responsive/browser compatibility changes recorded locally. | Production build and responsive checks are not a substitute for latest-two Chrome/Firefox/Edge/Safari coverage; browser matrix remains unrun. | `040ca29` (local) | Incomplete; browser matrix required |
| NFR-08 | #35 | Meaningful controller/service/auth/security tests and a JaCoCo line-coverage gate are present in this worktree. | `mvn -B verify '-Dspring-boot.repackage.skip=true'`: 72 tests passed, 0 failed/errors/skipped; PostgreSQL 15 Testcontainers ran. JaCoCo: 72.00% lines, 50.71% branches; gate passed. Repackaging was skipped because Windows held the artifact open. | `005995f` (local) | Local acceptance evidence met; not yet pushed |
| NFR-09 | #36 | Encrypted scheduled backup and restore workflow implemented. | Restore drill was recorded in commit `1a8ddfb`; external backup storage remains operator configuration. | `1a8ddfb` | Implemented; external-copy configuration pending |
| NFR-10 | #38 | Keyboard/focus and accessibility updates merged in PR #67. | Focused keyboard/accessibility fixes recorded; complete WCAG contrast audit/scan evidence still needs review. | `24a386e` | Implemented; full audit evidence pending |
| TASK-04 | #39 | README setup, student registration, opt-in demo accounts and test commands are documented; deviations and two-minute script/video exist. | README reviewed against current Compose/auth setup; video exists (`df1a448`). No manual browser demonstration was recorded in this pass. | `df1a448`; docs `d83f6d6`, `432b89c` (local) | Local documentation ready; not yet pushed |

## Current authentication work

Student-only public registration, confirmation-password validation, separate sign-in after signup, opt-in three-role demo seeding and bearer-session security are implemented in the local `fix/auth-filter-registration` worktree. Commits: `4a55a1a` auth and `005995f` NFR-08. The isolated PostgreSQL 15/Redis 7 live flow verified signup (201), duplicate (409), weak-password (400), student login, role denials (403), Admin/Teacher access (200), BCrypt persistence and idempotent seeding across API restart. No credentials are stored in Git.

No issue is marked complete solely because code is present. The local commits shown above are not necessarily present on `main`; check their branch/PR state before submission. No changes were pushed during this continuation.
