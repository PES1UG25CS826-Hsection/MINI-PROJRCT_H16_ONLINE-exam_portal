# Unit Test Report — Online Exam Portal

**Execution date:** 2026-11-20
**Command:** `mvn -B clean verify` followed by a final `mvn -B verify` (backend)
**Framework:** JUnit 5 / Spring Boot Test

| Metric | Result |
|---|---:|
| Test classes | 20 |
| Tests run | 72 |
| Passed | 72 |
| Failed | 0 |
| Errors | 0 |
| Skipped | 0 |

The suite covers authentication and demo seeding, bearer sessions and role checks, admin and group management, exam workflows, PostgreSQL 15 migrations/persistence, randomization, scoring, question bank, and reports. The run used the local Maven installation and Docker Desktop; the PostgreSQL Testcontainers migration test executed rather than being skipped. JaCoCo measured 72.00% line coverage and 50.71% branch coverage; the configured 70% line gate passed.

Raw Surefire results are generated at `backend/target/surefire-reports/` after `mvn verify`. Only the summary is committed here; generated build output is not committed.
