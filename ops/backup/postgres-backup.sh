#!/usr/bin/env bash
# ops/backup/postgres-backup.sh

set -euo pipefail

umask 077

: "${PGHOST:?PGHOST is required}"
: "${PGPORT:=5432}"
: "${PGDATABASE:?PGDATABASE is required}"
: "${PGUSER:?PGUSER is required}"
: "${BACKUP_DIR:=/var/backups/aegisops}"
: "${BACKUP_RETENTION_DAYS:=30}"

timestamp="$(date -u +'%Y%m%dT%H%M%SZ')"
backup_name="aegisops-${timestamp}"
backup_file="${BACKUP_DIR}/${backup_name}.dump"
checksum_file="${backup_file}.sha256"
metadata_file="${backup_file}.json"
lock_file="${BACKUP_DIR}/.backup.lock"

mkdir -p "${BACKUP_DIR}"

exec 9>"${lock_file}"

if ! flock -n 9; then
  echo "another backup process is running" >&2
  exit 1
fi

started_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"

pg_dump \
  --host="${PGHOST}" \
  --port="${PGPORT}" \
  --username="${PGUSER}" \
  --dbname="${PGDATABASE}" \
  --format=custom \
  --compress=9 \
  --no-owner \
  --no-privileges \
  --file="${backup_file}"

sha256sum "${backup_file}" \
  > "${checksum_file}"

finished_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
size_bytes="$(stat -c '%s' "${backup_file}")"

cat > "${metadata_file}" <<EOF
{
  "database": "${PGDATABASE}",
  "startedAt": "${started_at}",
  "finishedAt": "${finished_at}",
  "sizeBytes": ${size_bytes},
  "format": "postgres-custom",
  "checksumAlgorithm": "sha256"
}
EOF

if [[ -n "${RCLONE_REMOTE:-}" ]]; then
  rclone copy \
    "${backup_file}" \
    "${checksum_file}" \
    "${metadata_file}" \
    "${RCLONE_REMOTE}"
fi

find "${BACKUP_DIR}" \
  -type f \
  -name 'aegisops-*.dump*' \
  -mtime "+${BACKUP_RETENTION_DAYS}" \
  -delete

echo "backup completed: ${backup_file}"
