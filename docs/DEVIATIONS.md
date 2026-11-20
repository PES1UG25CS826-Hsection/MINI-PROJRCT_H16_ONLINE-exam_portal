# Requirements Deviations and Validation Gaps

**Review date:** 2026-11-19

| Requirement / document | Current status | Gap or deviation | Evidence / next action |
|---|---|---|---|
| SRS platform: Java 17, Spring Boot 3 | Java target 17; Spring Boot 3.5.16 | Java target aligns to SRS; exact Boot minor version was not prescribed. | `backend/pom.xml`, Java 17 CI, Temurin 17 Docker images. |
| FR-07 question bank CRUD | Implemented in code; not end-to-end validated | Bank create/list/edit/delete and draft exam selection endpoints/UI are present. Editing/deleting questions used by published or attempted exams is restricted. | Run the question-bank acceptance tests against PostgreSQL 15 and add coverage. |
| FR-09 student-group assignment | Implemented in code; not end-to-end validated | Group/membership and exam-group schema, APIs, UI, and student list/start eligibility checks are present. | Run the group-membership acceptance tests against PostgreSQL 15 and add coverage. |
| FR-12 authoritative timer/auto-submit | Partial | The browser shows a timer and submits on expiry; server-side deadline enforcement and close/reconnect auto-submit are not validated. | Enforce attempt deadline on every write/submit and add integration tests. |
| FR-05 full admin account management | Partial | Admin summary and account creation exist; edit/deactivate/delete flows are incomplete. | Complete guarded lifecycle operations and audit events. |
| NFR-03 monthly availability >=99.5% | Not verified; no qualifying deployment records | No deployment URL or historical monitoring records are available, and a full monthly period cannot be measured before submission. A short staging run cannot establish the monthly target. | See [`NFR-03_DEVIATION.md`](NFR-03_DEVIATION.md). Ask the instructor/project owner to approve a written scope deviation; until approval, keep issue #45 open and report the target as not verified. |
| NFR-05 five-person usability test | Not run | No first-time participant timings are available. | Run with at least five students and record each outcome; do not infer success from developer use. |
| NFR-06 browser matrix | Not run | Latest-two browser versions and 360px use were not tested across Chrome, Firefox, Edge, and Safari. | Run and record browser/viewport matrix. |
| NFR-08 >=70% unit coverage | Not met | Current backend line coverage is 1.9%; branch coverage is 2.8%. | Expand meaningful tests; report generated JaCoCo totals. |
| NFR-09 daily backup / restore | Implementation added; operational evidence pending | Backup profile is opt-in and stores encrypted dumps in a Docker volume. No timed restore drill or off-host copy is verified. | Configure the age recipient, enable the profile, copy backups off-host, run restore test, record actual RPO/RTO. |
| NFR-10 WCAG 2.1 AA | Partial, not audited | Skip link, keyboard focus styling, form labels, and responsive layout are present; no automated scan or full contrast/keyboard audit was run. | Run axe/WAVE plus manual keyboard and contrast review on student flows. |
| Part 3 system validation | Not run | Docker/PostgreSQL/Redis were unavailable in the execution environment. | Start services and execute all system test cases; update the report with actual evidence. |
| TASK-04 two-minute demo | Script provided; video missing | A presentation script is included, but it is not a recorded demo. | Record and link the actual two-minute demo before claiming this item complete. |
