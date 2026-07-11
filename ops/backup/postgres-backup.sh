#!/usr/bin/env bash
# ops/backup/postgres-backup.sh
#
# 把工作记录数据库按 custom 格式 dump 到 BACKUP_DIR，并可选地上传到 RCLONE_REMOTE。
#
# 关键不变量：
#   1. dump 必须先写到 .partial 文件再 rename 成最终名，
#      避免 pg_dump 中途失败后留下一个看似正常的损坏 dump。
#   2. rclone 必须对每个文件独立 copyto，不能用单次 copy 拷贝多源多目标。

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
partial_file="${backup_file}.partial"
checksum_file="${backup_file}.sha256"
metadata_file="${backup_file}.json"
lock_file="${BACKUP_DIR}/.backup.lock"

mkdir -p "${BACKUP_DIR}"

exec 9>"${lock_file}"

if ! flock -n 9; then
  echo "another backup process is running" >&2
  exit 1
fi

cleanup_partial() {
  rm -f "${partial_file}"
}
trap cleanup_partial EXIT

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
  --file="${partial_file}"

# 写入成功后才原子重命名为最终文件名。
mv "${partial_file}" "${backup_file}"

sha256sum "${backup_file}" > "${checksum_file}"

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
  remote_prefix="${RCLONE_REMOTE%/}/${backup_name}"

  rclone copyto "${backup_file}" "${remote_prefix}.dump"
  rclone copyto "${checksum_file}" "${remote_prefix}.dump.sha256"
  rclone copyto "${metadata_file}" "${remote_prefix}.dump.json"
fi

find "${BACKUP_DIR}" \
  -type f \
  -name 'aegisops-*.dump*' \
  -mtime "+${BACKUP_RETENTION_DAYS}" \
  -delete

trap - EXIT

echo "backup completed: ${backup_file}"
