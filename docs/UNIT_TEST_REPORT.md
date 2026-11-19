# Unit Test Report — Online Exam Portal

**Execution date:** 2026-11-19
**Command:** `mvn verify` (backend)
**Framework:** JUnit 5 / Spring Boot Test

| Metric | Result |
|---|---:|
| Test classes | 1 |
| Tests run | 5 |
| Passed | 5 |
| Failed | 0 |
| Errors | 0 |
| Skipped | 0 |

The executed suite tests the isolated scoring service: correct answer scoring, negative marking, unanswered questions, order-independent multi-select comparison, and a zero score floor. The suite does not yet provide unit coverage for authentication, authorization, exam workflows, persistence, or reporting.

Raw Surefire results are generated at `backend/target/surefire-reports/` after `mvn verify`. Only the summary is committed here; generated build output is not committed.

## Latest candidate run — 2026-11-20

The auth integration candidate completed `mvn -B verify`: 47 tests, 0 failures, 0 errors, and 2 PostgreSQL/Testcontainers tests skipped because the Maven container had no Docker socket. A separate live API smoke run against PostgreSQL 15 and Redis 7 exercised registration/login, role-protected exam authoring and group assignment, student attempt submission, reports, and CSV export. These results are from feature branches and are not evidence that the same candidate changes have been merged to main. The table above preserves the earlier 2026-11-19 baseline run.
