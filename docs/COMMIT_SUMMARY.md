# Faculty Task Commit Summary

The IDs below are commit IDs present in the local repository history. They identify implementation or documentation snapshots; verify each branch and GitHub PR state before submitting. Local-only commits are marked.

| Task ID | Issue | Commit | Description |
|---|---:|---|---|
| TASK-01 | #48 | `76ef1dc` | Scaffold, initial schema, services and app instructions; merged through PR #53. |
| TASK-02 | #49 | `76ef1dc` | Initial schema and migrations; merged through PR #53. |
| FR-06 | #7 | `7d5c361` | Teacher exam creation feature merge. |
| FR-07 | #8 | `ed64f92` | Question bank CRUD feature merge. |
| FR-08 | #9 | `cc42042` | Question selection and negative marking feature merge. |
| FR-09 | #10 | `1c9b090` | Exam publishing and group assignment feature merge. |
| FR-18 | #30 | `6969ca0` | Teacher reports and CSV export feature merge. |
| FR-19 | #31 | `cb8ce86` | Admin summary report feature merge. |
| FR-20 | #32 | `334141a` | Per-attempt question and option randomization feature merge. |
| NFR-05 | #33 | `4b18831`, `f99c840` (local) | Simulated usability analysis and blank real-student collection form; no real study performed. |
| NFR-06 | #34 | `040ca29`, `f99c840`, `9787682` (local) | Responsive styles and 16 Playwright checks passed across Chromium, Firefox, WebKit and Edge; system Chrome/Safari not tested. |
| NFR-08 | #35 | `005995f` (local), building on `575a420` | Expanded meaningful tests and JaCoCo gate; current integrated result is reported in `CODE_COVERAGE_REPORT.md`. |
| NFR-09 | #36 | `1a8ddfb`, `df4f564` (local) | Encrypted PostgreSQL backup and restore; isolated drill restored 13 tables, Flyway v2, and matching test-user count. |
| NFR-10 | #38 | `24a386e`, `f99c840`, `9787682` (local) | Accessibility/focus updates and eight axe scans across four engines; manual WCAG audit pending. |
| TASK-04 | #39 | `df1a448`, `d83f6d6`, `432b89c`, `f99c840`, `eee5530`, `70b3e93` (local) | Faculty guide, reports, tracker, browser checks and final evidence documentation. |

Authentication changes are in commit `4a55a1a` (`fix: complete student registration and demo authentication`); final evidence updates are in `f99c840`, `eee5530`, and `70b3e93`. Browser and restore improvements are in `9787682` and `df4f564`. Consult the remote branch head to determine which commits have been pushed; no commit here authorizes a direct push to `main` or a merge.
