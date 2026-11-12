#!/bin/sh
set -eu

if [ "$#" -ne 2 ]; then
  echo "Usage: restore-test.sh BACKUP_FILE AGE_IDENTITY_FILE" >&2
  exit 2
fi
: "${PGHOST:?PGHOST is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"

backup_file=$1
identity_file=$2
restore_db="oep_restore_check_$(date -u +%Y%m%d%H%M%S)_$$"
temporary_dump=$(mktemp /tmp/oep-restore-check.XXXXXX)
database_created=false
trap 'rm -f "$temporary_dump"; if [ "$database_created" = true ]; then dropdb --no-password --host="$PGHOST" --port="${PGPORT:-5432}" --username="$PGUSER" "$restore_db"; fi' EXIT HUP INT TERM

age --decrypt --identity "$identity_file" "$backup_file" > "$temporary_dump"
createdb --no-password --host="$PGHOST" --port="${PGPORT:-5432}" --username="$PGUSER" "$restore_db"
database_created=true
pg_restore --exit-on-error --no-owner --host="$PGHOST" --port="${PGPORT:-5432}" --username="$PGUSER" --dbname="$restore_db" "$temporary_dump"

table_count=$(psql --no-password --host="$PGHOST" --port="${PGPORT:-5432}" --username="$PGUSER" --dbname="$restore_db" --tuples-only --no-align \
  --command="SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'")
if [ "$table_count" -lt 9 ]; then
  echo "Restore verification failed: expected at least 9 public tables, found $table_count" >&2
  exit 1
fi
migration_version=$(psql --no-password --host="$PGHOST" --port="${PGPORT:-5432}" --username="$PGUSER" --dbname="$restore_db" --tuples-only --no-align \
  --command="SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")
if [ -z "$migration_version" ]; then
  echo "Restore verification failed: Flyway history is missing" >&2
  exit 1
fi

echo "Restore verification passed: public_tables=$table_count, flyway_version=$migration_version, target=$restore_db"
