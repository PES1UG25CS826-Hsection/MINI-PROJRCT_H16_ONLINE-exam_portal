# Backup and restore drill evidence

**Run date:** 2026-11-20 (Asia/Calcutta)
**Purpose:** NFR-09 (#36) encrypted backup and restore verification.
**Environment:** Disposable Docker Compose project `codex-verify-portal`; PostgreSQL 15.19. No user data or existing Docker volumes were used.

## Result

- The candidate backup image produced `/backups/online_exam_20261119T203507Z.dump.age` using an ephemeral age recipient. The private identity was kept outside the repository and removed after the drill.
- The candidate restore command decrypted that archive and restored it into its generated temporary database. Verification found 13 public tables and Flyway version 2. The script removed only that temporary database after verification.
- Restore elapsed time was 4.8 seconds, below the four-hour RTO target.
- The configured schedule is 86,400 seconds, bounded to at most 24 hours; retention defaults to 30 days. The scheduler takes a backup immediately at startup.

## Scope

This drill verifies one complete encrypted backup and restore. It does not measure an ongoing 24-hour production backup cadence or test off-host replication. Production operators must configure the age recipient and replicate the encrypted backup volume to approved off-host storage.

The restore container needs `DAC_OVERRIDE` for the one-off restore because encrypted archives are owner-only (`0600`) and the service drops all capabilities by default. The scheduled backup service remains capability-restricted.
