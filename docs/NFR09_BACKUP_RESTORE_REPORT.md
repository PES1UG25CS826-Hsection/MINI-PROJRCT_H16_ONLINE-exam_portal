# NFR-09 Backup and Restore Verification

## Implemented workflow

The opt-in Compose backup profile runs an encrypted PostgreSQL custom-format dump on startup and then every 86,400 seconds by default. The interval is capped at 86,400 seconds; archives are encrypted with `age` before being written to the persistent backup volume. The default retention is 30 days. The restore drill decrypts the selected archive into a uniquely named temporary database, runs `pg_restore --exit-on-error`, and checks public table count and Flyway history before dropping only that temporary database.

Configuration and operator commands: [backup instructions](../ops/backup/README.md). The private age identity stays outside Git and is mounted read-only for a restore. Configure replication of the encrypted backup volume to access-controlled off-host/object storage in the deployment environment; this repository does not provision or validate a remote destination.

## Targets and evidence

- RPO target: at most 24 hours, conditional on the scheduled backup service running and completing successfully.
- RTO target: recovery within four hours. Measure per environment; this is not inferred from the script.
- Backup archive generated in this verification: **completed** against the isolated PostgreSQL 15.19 test database after Flyway migrations V1 and V2. The encrypted custom-format file is `oep_nfr09_test_20261120T043241Z.dump.age` (32.8 KiB) in the new `oep-final-nfr09_backup_data` volume. It was encrypted with a test-only age key; the private key was kept out of Git.
- Restore into an isolated test database: **passed**. The drill restored to `oep_restore_check_20261120043812_1`, found 13 public tables and Flyway version 2, and matched the source user count (1, including the clearly labeled test canary). A follow-up query confirmed the temporary restore database was dropped and the source canary remained.
- Measured drill command elapsed time: approximately **4.8 seconds** on this local Docker host, including starting the one-off restore container. This is a local database-drill duration, not a measured full application recovery time or deployment RTO.
- Off-site upload or recovery: **not configured or verified**.
- Existing project database and Docker volumes: not modified. The separately created `oep-final-nfr09` PostgreSQL, Redis and backup volumes were left intact; the source test canary and encrypted archive remain there.

Do not treat the configured interval or successful script syntax as proof of a completed backup, recovery, RPO, or RTO.
