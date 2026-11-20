# NFR-09 Backup and Restore Verification

## Implemented workflow

The opt-in Compose backup profile runs an encrypted PostgreSQL custom-format dump on startup and then every 86,400 seconds by default. The interval is capped at 86,400 seconds; archives are encrypted with `age` before being written to the persistent backup volume. The default retention is 30 days. The restore drill decrypts the selected archive into a uniquely named temporary database, runs `pg_restore --exit-on-error`, and checks public table count and Flyway history before dropping only that temporary database.

Configuration and operator commands: [backup instructions](../ops/backup/README.md). The private age identity stays outside Git and is mounted read-only for a restore. Configure replication of the encrypted backup volume to access-controlled off-host/object storage in the deployment environment; this repository does not provision or validate a remote destination.

## Targets and evidence

- RPO target: at most 24 hours, conditional on the scheduled backup service running and completing successfully.
- RTO target: recovery within four hours. Measure per environment; this is not inferred from the script.
- Backup archive generated in this verification: **not completed**. An isolated PostgreSQL 15/Redis Compose project and new named volumes were created, but building the project API to apply migrations required downloading large Maven/JRE image layers; the build was stopped because transfer remained very slow. The backup/restore service was therefore not run.
- Restore into an isolated test database: **not run**.
- Measured restore duration: **not available**.
- Off-site upload or recovery: **not configured or verified**.
- Existing project database and Docker volumes: not modified. The separately created `oep-final-nfr09` test volumes were left intact; do not remove them as part of cleanup without explicit direction.

Do not treat the configured interval or successful script syntax as proof of a completed backup, recovery, RPO, or RTO.
