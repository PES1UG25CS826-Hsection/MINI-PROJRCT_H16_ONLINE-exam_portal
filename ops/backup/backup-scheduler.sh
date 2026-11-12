#!/bin/sh
set -eu

: "${BACKUP_AGE_RECIPIENT:?Set BACKUP_AGE_RECIPIENT to an age public key in .env before enabling the backup profile}"
: "${BACKUP_INTERVAL_SECONDS:=86400}"

case "$BACKUP_INTERVAL_SECONDS" in
  ''|*[!0-9]*) echo "BACKUP_INTERVAL_SECONDS must be a positive integer" >&2; exit 2 ;;
esac
if [ "$BACKUP_INTERVAL_SECONDS" -lt 60 ]; then
  echo "BACKUP_INTERVAL_SECONDS must be at least 60" >&2
  exit 2
fi
if [ "$BACKUP_INTERVAL_SECONDS" -gt 86400 ]; then
  echo "BACKUP_INTERVAL_SECONDS must not exceed 86400 (the 24-hour RPO)" >&2
  exit 2
fi

while :; do
  started_at=$(date +%s)
  if ! /usr/local/bin/backup-once.sh; then
    echo "Encrypted database backup failed; it will retry after ${BACKUP_INTERVAL_SECONDS}s" >&2
  fi
  elapsed=$(( $(date +%s) - started_at ))
  delay=$(( BACKUP_INTERVAL_SECONDS - elapsed ))
  if [ "$delay" -lt 1 ]; then delay=60; fi
  sleep "$delay"
done
