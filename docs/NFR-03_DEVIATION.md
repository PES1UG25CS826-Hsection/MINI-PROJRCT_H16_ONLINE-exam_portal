# NFR-03 Availability Deviation Request

**Status: proposed; approval has not been recorded.**

## Original requirement

Issue #45 requires at least 99.5% monthly availability during exam windows, supported by monitoring records for the measured period.

## Current evidence

There is no deployed service URL, no historical uptime log, and no complete month of monitoring records in this repository. Therefore, the monthly availability target is **not verified**. A local health check, short staging run, synthetic data, or deployment configuration cannot prove the original monthly target.

## Requested disposition

Ask the course instructor/project owner to approve one of these written dispositions:

1. **Keep the original target:** leave issue #45 open and collect actual monitoring records for a complete month after deployment.
2. **Approve a submission deviation:** accept the monitoring implementation and deployment plan as the deliverable for this submission, record monthly availability as *not measured*, and track the remaining measurement after submission.
3. **Provide historical records:** use authentic records from an existing deployment of this same system, with the source, measured dates, probe interval, exclusions, and deployment revision documented.

Do not select option 2 on the instructor's behalf. Record their approval, date, and any revised acceptance criteria below before treating the deviation as accepted.

| Approval record | Value |
|---|---|
| Approver (instructor/project owner) | Pending |
| Decision and revised acceptance criteria | Pending |
| Approval date | Pending |
| Evidence or communication reference | Pending |

## Evidence plan if a deployment becomes available

Run `ops/monitoring/probe.py` against the deployed readiness URL and retain the original JSONL records. A short observation can describe only that specific interval; it must not be labeled a monthly result or counted as proof of 99.5% monthly availability. Continue monitoring until a complete monthly period exists, then use `ops/monitoring/availability-report.py` and attach its report, raw logs, deployment revision, interval, incident list, and maintenance exclusions.

## Backlog disposition

Until authentic monthly records meet the requirement or an authorized written deviation is approved, issue #45 stays **open / unverified**. Do not mark it Done based solely on code, a local demo, a short test, or this request document.
