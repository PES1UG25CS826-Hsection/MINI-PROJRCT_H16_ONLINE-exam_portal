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
