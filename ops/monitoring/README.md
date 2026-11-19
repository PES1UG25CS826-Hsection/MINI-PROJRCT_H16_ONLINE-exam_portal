# Availability monitoring

The API exposes Spring Actuator health and readiness at `/actuator/health` and `/actuator/health/readiness`. The probe records UTC timestamp, HTTP status, response time, success, and transport errors as one JSON object per line. Store the log on a monitoring host or durable volume outside the application instance.

## Run a probe

Keep the process supervised by the deployment's service manager or container scheduler. The default interval is one minute:

```sh
python3 ops/monitoring/probe.py https://exam.example.edu/actuator/health/readiness --interval 60 --output /var/log/online-exam/availability.jsonl
```

Retain raw records and record planned maintenance separately in the incident/change log. To exclude a scheduled window from the contractual denominator, mark the exact affected probe records with `"excluded": true` in a reviewed copy of the raw log; never alter the original log. Unexpected downtime is not excluded.

## Produce a monthly summary

```sh
python3 ops/monitoring/availability-report.py /var/log/online-exam/availability.jsonl 2026-11 --interval 60
```

The report counts every expected sample, treats missing records as missing evidence, and does not pass the 99.5% target if any expected probe is missing. Include the report, original logs, deployment revision, monitoring interval, incident list, maintenance exclusions, and measured UTC period in the project evidence. The month must be complete before it can demonstrate the monthly target. This repository contains no live deployment logs; the target cannot be claimed as measured yet.
