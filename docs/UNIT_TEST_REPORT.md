# Unit Test Report — Online Exam Portal

**Execution date:** 2026-11-20
**Command:** `mvn verify` (backend)
**Framework:** JUnit 5 / Spring Boot Test

| Metric | Result |
|---|---:|
| Test classes | 4 |
| Tests run | 17 |
| Passed | 17 |
| Failed | 0 |
| Errors | 0 |
| Skipped | 0 |

The suite covers scoring (5 tests), session token behavior (4 tests), bearer-token filter behavior (3 tests), and exam/question validation (5 tests). It does not yet exercise database-backed persistence, full authentication flows, report aggregation/CSV export, group eligibility, or browser behavior.

Raw Surefire results are generated at `backend/target/surefire-reports/` after `mvn verify`. Only the summary is committed here; generated build output is not committed.
