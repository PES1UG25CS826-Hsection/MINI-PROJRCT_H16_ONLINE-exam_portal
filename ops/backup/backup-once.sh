#!/bin/sh
set -eu
set -o pipefail

: "${PGHOST:?PGHOST is required}"
: "${PGDATABASE:?PGDATABASE is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"
: "${BACKUP_AGE_RECIPIENT:?Set BACKUP_AGE_RECIPIENT to an age public key}"
: "${BACKUP_DIR:=/backups}"
: "${BACKUP_RETENTION_DAYS:=30}"

case "$BACKUP_RETENTION_DAYS" in
  ''|*[!0-9]*) echo "BACKUP_RETENTION_DAYS must be a positive integer" >&2; exit 2 ;;
esac
if [ "$BACKUP_RETENTION_DAYS" -lt 1 ]; then
  echo "BACKUP_RETENTION_DAYS must be at least 1" >&2
  exit 2
fi

umask 077
mkdir -p "$BACKUP_DIR"
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
destination="$BACKUP_DIR/${PGDATABASE}_${timestamp}.dump.age"
temporary=$(mktemp "$BACKUP_DIR/.${PGDATABASE}_${timestamp}.XXXXXX")
trap 'rm -f "$temporary"' EXIT HUP INT TERM

pg_dump --no-password --format=custom --no-owner --host="$PGHOST" --port="${PGPORT:-5432}" --username="$PGUSER" "$PGDATABASE" \
  | age --recipient "$BACKUP_AGE_RECIPIENT" > "$temporary"
chmod 0600 "$temporary"
mv "$temporary" "$destination"
trap - EXIT HUP INT TERM

find "$BACKUP_DIR" -maxdepth 1 -type f -name "${PGDATABASE}_*.dump.age" -mtime "+$BACKUP_RETENTION_DAYS" -delete
echo "Encrypted backup completed: $destination"
