#!/usr/bin/env bash
# ops/backup/postgres-restore-verify.sh
#
# 在临时 PostgreSQL 数据库中恢复 dump，并通过一组强校验判断恢复是否真的成功。
# 任意一项失败时，脚本必须以非零状态退出，否则视为假成功。

set -euo pipefail

: "${1:?usage: postgres-restore-verify.sh BACKUP_FILE}"

backup_file="$1"

: "${PGHOST:?PGHOST is required}"
: "${PGPORT:=5432}"
: "${PGUSER:?PGUSER is required}"
: "${PGDATABASE:=postgres}"

test -f "${backup_file}"
test -f "${backup_file}.sha256"

sha256sum -c "${backup_file}.sha256"

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

# --exit-on-error 让 pg_restore 在遇到 schema/数据错误时立即失败，
# 而不是只打印 warning 后继续。
pg_restore \
  --host="${PGHOST}" \
  --port="${PGPORT}" \
  --username="${PGUSER}" \
  --dbname="${verify_database}" \
  --no-owner \
  --no-privileges \
  --exit-on-error \
  "${backup_file}"

psql \
  --host="${PGHOST}" \
  --port="${PGPORT}" \
  --username="${PGUSER}" \
  --dbname="${verify_database}" \
  --set=ON_ERROR_STOP=1 \
  --variable=ON_ERROR_STOP=on \
  <<'SQL'
do $$
begin
    if to_regclass('work_record.wr_template') is null then
        raise exception 'wr_template was not restored';
    end if;

    if to_regclass('work_record.wr_record') is null then
        raise exception 'wr_record was not restored';
    end if;

    if to_regclass('public.audit_log') is null then
        raise exception 'audit_log was not restored';
    end if;

    if not exists (
        select 1
        from flyway_schema_history
        where success = true
    ) then
        raise exception 'flyway history is empty or not successful';
    end if;

    perform count(*) from work_record.wr_template;
    perform count(*) from work_record.wr_record;
    perform count(*) from public.audit_log;
end
$$;
SQL

echo "restore verification completed"
