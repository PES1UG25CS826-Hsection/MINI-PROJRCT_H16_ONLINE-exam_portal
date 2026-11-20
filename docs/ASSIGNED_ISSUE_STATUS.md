# Assigned GitHub Issue Status

Repository: `PES1UG25CS826-Hsection/MINI-PROJRCT_H16_ONLINE-exam_portal`

Assignee checked: `ManojGaonkar05`

Status checked: 2026-11-20

GitHub reports 15 assigned issues: #48 and #49 are closed; #7–#10 and #30–#39 (excluding #37) remain open. Open status is not the same as missing code: seven feature issues have implementation and focused-test PRs merged, but their issue records remain open. No acceptance criteria are marked complete here solely because code exists.

## Issue evidence

| Issue | Status and evidence | Remaining acceptance work |
|---|---|---|
| #48 — TASK-01 scaffold | Closed; PR #53 merged. Compose configuration validates with the existing local environment. | The live Compose check below covers a running stack; no new implementation needed. |
| #49 — TASK-02 schema | Closed; PR #53 merged. Fresh PostgreSQL migration was previously exercised through Flyway V1–V2. | No new implementation identified. |
| #7 — FR-06 teacher creates exam | Implementation merged in PR #57; follow-up test PR #58 merged. Exam draft validation/persistence tests passed. Live end-to-end smoke covered teacher draft creation. | Issue is still open in GitHub; owner acceptance/issue-state update remains. |
| #8 — FR-07 question bank CRUD | Implementation merged in PR #57; follow-up tests merged in PR #60. Focused CRUD tests passed. Live smoke covered question creation. | Issue is still open in GitHub; owner acceptance/issue-state update remains. |
| #9 — FR-08 question selection/negative marking | Implementation merged in PR #57; follow-up tests merged in PR #61. Selection/scoring tests passed. | Issue is still open in GitHub; PostgreSQL-specific verification and owner acceptance remain. |
| #10 — FR-09 publish and group assignment | Implementation merged in PR #57; follow-up tests merged in PR #63. Live smoke covered assignment, publishing, student visibility, and direct start eligibility. | Issue is still open in GitHub; owner acceptance/issue-state update remains. |
| #30 — FR-18 teacher reports and CSV | Implementation merged in PR #57; follow-up tests merged in PR #64. Report metrics, ownership, and CSV escaping tests passed; live smoke exercised report/CSV. | Issue is still open in GitHub; owner acceptance/issue-state update remains. |
| #31 — FR-19 admin summary | Implementation merged in PR #57; follow-up tests merged in PR #65. Persisted summary count tests passed. | Issue is still open in GitHub; authenticated admin HTTP check and owner acceptance remain. |
| #32 — FR-20 attempt randomization | Implementation merged in PR #57; follow-up tests merged in PR #66. Tests cover stable per-attempt order, disabled ordering, answer mapping, and answer-key hiding. | Issue is still open in GitHub; owner acceptance/issue-state update remains. |
| #33 — NFR-05 usability | Login and exam-start flows are present. | The required first-time-student study has not been run. Collect participant timings and feedback; do not infer a 90% result. |
| #34 — NFR-06 browser support | Edge 157 was checked at 360px; focused pages had no horizontal overflow. | The latest-two-version matrix for Chrome, Firefox, Edge, and Safari has not been run. Only Edge was available in the recorded local checks. |
| #35 — NFR-08 coverage | Candidate branch `feature/nfr-08-coverage-improvements`, commit `95e98ab`, reports 70.64% line (296/419) and 51.06% branch (145/284) coverage. `mvn -B clean verify`: 70 tests, 0 failures/errors, 2 PostgreSQL/Testcontainers skips due to no Docker socket in Maven. | Candidate is not merged. Confirm coverage in CI and document exclusions before claiming acceptance. |
| #36 — NFR-09 backup/restore | Branch `fix/nfr-09-backup-drill`, commit `1a8ddfb`; encrypted backup/restore drill recovered 13 tables and Flyway v2 in 4.8 seconds. | A continuous 24-hour schedule observation and independent off-host copy are not verified. |
| #38 — NFR-10 accessibility | Candidate `fix/nfr-10-login-focus`, commit `a40bccc`; Edge 157 at 360px with axe-core reported zero WCAG 2.1 A/AA violations on audited sign-in, teacher, student, attempt, and results routes. Keyboard focus and scrollable table regions were checked. | Full supported-browser matrix, manual assistive-technology review, and complete rendered-state contrast audit remain. |
| #39 — TASK-04 documentation/demo | Branch `docs/task-04-final-demo`, commits `df1a448` and `d84597c`; README, deviations, validation reports, presentation guide, and a 159.12-second 1280×720 demo video are pushed. Video was fully decoded without errors. | Candidate is not merged; faculty/repository-owner review remains. |

## Current runtime and test evidence

- `docker compose config -q` passed for the `origin/main` Compose file when supplied the existing local `.env` (values were not printed).
- The existing demo containers are running: PostgreSQL and Redis report healthy; API `/actuator/health` reports `UP`; exposed API and frontend endpoints returned HTTP 200.
- The backend candidate full verification passed as recorded for #35. Two database integration tests were skipped because the Maven container cannot access the Docker socket; they are not counted as passing.
- A frontend Vite production build passed in the browser-compatibility worktree.
- Previous live PostgreSQL/Redis smoke covered registration/login, teacher group/member and exam/question creation, assignment/publish, student attempt/save/submit, teacher report/CSV, and student denial from a teacher-only group route.

## Submission state

Candidate branches above are pushed and remain unmerged unless a later PR says otherwise. At the time of this report, the only open PRs returned by repository search were #51 and #62; neither is for these candidate branches. Earlier PR creation through the connected GitHub integration returned HTTP 403. No issues were closed in this update. Do not report open issues as accepted until the repository owner reviews the evidence and updates the issue state.
