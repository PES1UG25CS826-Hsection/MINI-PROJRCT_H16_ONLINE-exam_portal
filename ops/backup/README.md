# PostgreSQL backup and restore

The optional Compose `backup` profile takes a PostgreSQL custom-format dump immediately on startup, then every 24 hours by default. It encrypts each dump with age before writing it to the persistent `backup_data` volume. Only encrypted archives are retained there. The default retention period is 30 days.

## Configure and start daily backups

1. Install `age` on a trusted admin workstation and create an identity: `age-keygen -o backup-age-identity.txt`.
2. Store the private identity outside the repository in a protected location. Never copy it into the backup volume or commit it.
3. Put only the matching `age1...` public recipient in `.env` as `BACKUP_AGE_RECIPIENT=age1...`.
4. Set `.env` database values to match the existing database volume. `POSTGRES_PASSWORD` does not rotate a role password in an already-initialized volume.
5. Start the scheduled backup service with `docker compose --profile backup up -d backup`.
6. Check `docker compose --profile backup logs backup` and confirm an `Encrypted backup completed` message. Files are stored in the `backup_data` Docker volume. Replicate that volume to an access-controlled off-host location to protect against loss of the Docker host.

`BACKUP_INTERVAL_SECONDS` defaults to 86400 and is capped at 86400 to preserve the RPO target. `BACKUP_RETENTION_DAYS` defaults to 30. Each interval is measured start-to-start, so dump duration does not extend the 24-hour schedule. A backup failure is logged and retried at the next interval. The service is opt-in so a missing encryption recipient cannot silently result in plaintext backups.

## Verify a backup can be restored

This drill restores to a temporary database on the same PostgreSQL server, checks that the schema and Flyway history are present, then drops only that explicitly named temporary database. It does not alter the source database or any Docker volume.

```sh
docker compose --profile backup run --rm --no-deps \
  --user 0:0 \
  -v "$PWD/secrets/backup-age-identity:/run/secrets/backup-age-identity:ro" \
  --entrypoint /usr/local/bin/restore-test.sh backup \
  /backups/online_exam_YYYYMMDDTHHMMSSZ.dump.age \
  /run/secrets/backup-age-identity
```

Replace the archive name with an existing encrypted backup. The identity file must match the public recipient used to encrypt it. The command reports the restored public table count and Flyway version; a non-zero exit indicates a failed drill.

## Restore procedure

1. Put the application in maintenance mode and take a fresh safety backup before replacing any database.
2. Create a new empty target database. Never restore over the active database as a first step.
3. Keep the encrypted archive unchanged and mount the authorized age identity read-only into a temporary backup container.
4. Decrypt to a protected temporary file or stream the archive directly into `pg_restore --exit-on-error --no-owner` using a role permitted to restore into the new target database.
5. Verify Flyway history, application health, and representative records on the target, then switch the application connection during maintenance. Retain the old database until the restored system is accepted.
6. Remove any plaintext temporary dump securely after verification. Do not remove Docker volumes as part of the restore procedure.

The RPO target is one completed backup per 24 hours. The actual RTO must be timed during the restore drill and remain below four hours; document the measured duration for each deployment environment.
