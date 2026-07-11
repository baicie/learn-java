#!/usr/bin/env bash
# ops/backup/postgres-restore-verify.sh

set -euo pipefail

: "${1:?usage: postgres-restore-verify.sh BACKUP_FILE}"

backup_file="$1"

: "${PGHOST:?PGHOST is required}"
: "${PGPORT:=5432}"
: "${PGUSER:?PGUSER is required}"
: "${PGDATABASE:=postgres}"

test -f "${backup_file}"
test -f "${backup_file}.sha256"

sha256sum -c \
  "${backup_file}.sha256"

verify_database="aegisops_restore_verify_$(date +%s)"

cleanup() {
  dropdb \
    --if-exists \
    --host="${PGHOST}" \
    --port="${PGPORT}" \
    --username="${PGUSER}" \
    "${verify_database}" \
    >/dev/null 2>&1 || true
}

trap cleanup EXIT

createdb \
  --host="${PGHOST}" \
  --port="${PGPORT}" \
  --username="${PGUSER}" \
  "${verify_database}"

pg_restore \
  --host="${PGHOST}" \
  --port="${PGPORT}" \
  --username="${PGUSER}" \
  --dbname="${verify_database}" \
  --no-owner \
  --no-privileges \
  "${backup_file}"

psql \
  --host="${PGHOST}" \
  --port="${PGPORT}" \
  --username="${PGUSER}" \
  --dbname="${verify_database}" \
  --set=ON_ERROR_STOP=1 \
  <<'SQL'
select
    to_regclass(
        'work_record.wr_template'
    ) is not null
    as template_table_exists;

select
    to_regclass(
        'work_record.wr_record'
    ) is not null
    as record_table_exists;

select
    to_regclass(
        'public.audit_log'
    ) is not null
    as audit_table_exists;

select count(*)
from flyway_schema_history
where success = true;

select count(*)
from work_record.wr_template;

select count(*)
from work_record.wr_record;

select count(*)
from public.audit_log;
SQL

echo "restore verification completed"
