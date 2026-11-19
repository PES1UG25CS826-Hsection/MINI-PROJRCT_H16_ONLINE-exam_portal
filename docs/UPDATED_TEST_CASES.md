# Updated Test Case Document — Online Exam Portal

**Revision date:** 2026-11-19
**Basis:** current implementation, SRS, and Test Plan. Cases marked *not executed* are not evidence of passing behavior.

| ID | Requirement | Test case / expected result | Current evidence/status |
|---|---|---|---|
| UT-SCORE-01 | Result scoring | Correct single-choice response earns its configured points. | Pass; `ExamScoringServiceTest` |
| UT-SCORE-02 | Negative marking | Incorrect response applies the configured penalty. | Pass; `ExamScoringServiceTest` |
| UT-SCORE-03 | Unanswered question | No response earns zero and no penalty. | Pass; `ExamScoringServiceTest` |
| UT-SCORE-04 | Multi-select scoring | Selection order does not change exact-match scoring. | Pass; `ExamScoringServiceTest` |
| UT-SCORE-05 | Score boundary | Penalties cannot reduce the final score below zero. | Pass; `ExamScoringServiceTest` |
| SYS-AUTH-01 | Registration | Register a student with a valid unique email and strong password; reject duplicate/weak inputs. | Not executed against PostgreSQL/Redis |
| SYS-AUTH-02 | Login and roles | Valid credentials issue a session; invalid credentials fail; role-protected APIs deny unauthorized roles. | Not executed against PostgreSQL/Redis |
| SYS-AUTH-03 | Account protection | Five failed logins lock the account for the configured interval; rate limit is enforced. | Not executed against Redis |
| SYS-EXAM-01 | Exam authoring | Teacher creates a draft and adds supported questions; invalid totals prevent publishing. | Not executed against PostgreSQL |
| SYS-EXAM-02 | Student attempt | Student starts an eligible published exam; correct answers are not exposed; answers and review flags persist. | Not executed against PostgreSQL/Redis |
| SYS-EXAM-03 | Submission and result | Submission stores one result, applies scoring, and appears in student/teacher views. | Not executed against PostgreSQL |
| SYS-EXAM-04 | Group eligibility | Assign a published exam to a group; allow only group members to list/start it. | Implemented; not executed against PostgreSQL |
| SYS-QUESTION-01 | Question bank maintenance | Teacher views, edits, and deletes owned questions; reject changes to another teacher's or in-use questions. | Implemented; not executed against PostgreSQL |
| SYS-ADMIN-01 | Administration | Admin summary and account creation are restricted to administrators. | Not executed against PostgreSQL |
| SYS-ADMIN-02 | Account lifecycle | Admin edits, deactivates/reactivates, and deletes accounts under relationship constraints. | Lifecycle endpoints not implemented |
| SYS-REPORT-01 | Reports | Teacher can retrieve exam metrics and download a CSV. | Not executed against PostgreSQL |
| SYS-NFR-05 | First-use usability | Five first-time students complete login-to-start; record completion times and failures. | Participant study not run |
| SYS-NFR-06 | Browser/viewport | Run student flow on latest two Chrome, Firefox, Edge, Safari versions at 360px and desktop widths. | Browser matrix not run |
| SYS-NFR-09 | Backup/restore | Confirm encrypted backup runs within 24 hours and time an isolated restore drill. | Scripts added; Docker-backed drill not run |
| SYS-NFR-10 | Accessibility | Run automated scan, full keyboard walkthrough, and verify normal-text contrast >= 4.5:1. | No automated scan or full audit run |

## Execution note

The five scoring unit tests ran in Maven `verify` and passed. The system cases require a running PostgreSQL 15 and Redis 7 instance plus end-to-end execution; they were not run in this environment. See `SYSTEM_VALIDATION_REPORT.md` for the validation status.
