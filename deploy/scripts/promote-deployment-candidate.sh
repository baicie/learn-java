#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

candidate_root=""
active_root=""
manifest=""
backup_dir=""
recover_only=0
backup_temp=""
lock_temp=""
lock_held=0
lock_mode=""
recovering=0
paths=()

usage() {
  cat >&2 <<'EOF'
Usage: promote-deployment-candidate.sh \
  --candidate-root PATH --active-root PATH \
  --manifest PATH --backup-dir PATH

       promote-deployment-candidate.sh \
  --active-root PATH --recover-only
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --candidate-root)
      [ "$#" -ge 2 ] || { usage; exit 2; }
      candidate_root=$2
      shift 2
      ;;
    --active-root)
      [ "$#" -ge 2 ] || { usage; exit 2; }
      active_root=$2
      shift 2
      ;;
    --manifest)
      [ "$#" -ge 2 ] || { usage; exit 2; }
      manifest=$2
      shift 2
      ;;
    --backup-dir)
      [ "$#" -ge 2 ] || { usage; exit 2; }
      backup_dir=$2
      shift 2
      ;;
    --recover-only)
      recover_only=1
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

if [ -z "$active_root" ]; then
  usage
  exit 2
fi
if [ "$recover_only" = "1" ]; then
  if [ -n "$candidate_root" ] || [ -n "$manifest" ] || [ -n "$backup_dir" ]; then
    usage
    exit 2
  fi
else
  for required_value in candidate_root manifest backup_dir; do
    if [ -z "${!required_value}" ]; then
      usage
      exit 2
    fi
  done
fi

for command_name in basename chmod cp dirname grep ln mkdir mktemp mv rm sha256sum sync; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "Required command is not available: $command_name" >&2
    exit 1
  }
done

if [ ! -d "$active_root" ]; then
  echo "Active deployment root is missing: $active_root" >&2
  exit 1
fi
active_root="$(cd "$active_root" && pwd -P)"
backup_root="$active_root/deploy/backups"
mkdir -p "$backup_root"
chmod 0700 "$backup_root"
backup_root="$(cd "$backup_root" && pwd -P)"
journal_file="$backup_root/.promotion-in-progress"
lock_file="$backup_root/.promotion.lock"

boot_id() {
  if [ -r /proc/sys/kernel/random/boot_id ]; then
    IFS= read -r current_boot_id < /proc/sys/kernel/random/boot_id
    printf '%s\n' "$current_boot_id"
  else
    printf 'unknown\n'
  fi
}

read_lock_owner() {
  lock_owner_pid=""
  lock_owner_boot=""
  if [ -f "$lock_file" ]; then
    {
      IFS= read -r lock_owner_pid || true
      IFS= read -r lock_owner_boot || true
    } < "$lock_file"
  fi
}

acquire_lock() {
  local attempt
  local current_boot
  if command -v flock >/dev/null 2>&1; then
    exec 9>"$lock_file"
    if ! flock -n 9; then
      exec 9>&-
      echo "Another deployment promotion is already running." >&2
      return 1
    fi
    lock_mode="flock"
    lock_held=1
    return 0
  fi
  if [ "${PROMOTION_REQUIRE_FLOCK:-0}" = "1" ]; then
    echo "Production deployment promotion requires flock." >&2
    return 1
  fi

  current_boot="$(boot_id)"
  for attempt in 1 2 3; do
    lock_temp="$(mktemp "$backup_root/.promotion-lock.XXXXXX")"
    {
      printf '%s\n' "$$"
      printf '%s\n' "$current_boot"
    } > "$lock_temp"
    chmod 0600 "$lock_temp"
    if ln "$lock_temp" "$lock_file" 2>/dev/null; then
      rm -f -- "$lock_temp"
      lock_temp=""
      lock_mode="portable"
      lock_held=1
      return 0
    fi
    rm -f -- "$lock_temp"
    lock_temp=""

    read_lock_owner
    case "$lock_owner_pid" in
      ''|*[!0-9]*) ;;
      *)
        if [ "$lock_owner_boot" = "$current_boot" ] \
          && kill -0 "$lock_owner_pid" 2>/dev/null; then
          echo "Another deployment promotion is running with pid $lock_owner_pid." >&2
          return 1
        fi
        ;;
    esac
    rm -f -- "$lock_file"
  done
  echo "Unable to acquire deployment promotion lock: $lock_file" >&2
  return 1
}

release_lock() {
  local current_boot
  if [ "$lock_held" != "1" ]; then
    return 0
  fi
  if [ "$lock_mode" = "flock" ]; then
    flock -u 9 || true
    exec 9>&-
    lock_held=0
    lock_mode=""
    return 0
  fi
  current_boot="$(boot_id)"
  read_lock_owner
  if [ "$lock_owner_pid" = "$$" ] && [ "$lock_owner_boot" = "$current_boot" ]; then
    rm -f -- "$lock_file"
  fi
  lock_held=0
  lock_mode=""
}

load_manifest_paths() {
  local manifest_path=$1
  local expected_sha
  local relative_path
  local existing_path
  paths=()
  while read -r expected_sha relative_path; do
    [ -n "$expected_sha" ] || continue
    relative_path="${relative_path#\*}"
    case "$relative_path" in
      deploy/*) ;;
      *)
        echo "Candidate manifest path must be below deploy/: $relative_path" >&2
        return 1
        ;;
    esac
    case "/$relative_path/" in
      *"/../"*|*"/./"*)
        echo "Candidate manifest contains an unsafe path: $relative_path" >&2
        return 1
        ;;
    esac
    case "$relative_path" in
      *.aegisops-promotion-tmp)
        echo "Candidate manifest uses a reserved path: $relative_path" >&2
        return 1
        ;;
    esac
    for existing_path in "${paths[@]:-}"; do
      if [ -n "$existing_path" ] && [ "$existing_path" = "$relative_path" ]; then
        echo "Candidate manifest contains a duplicate path: $relative_path" >&2
        return 1
      fi
    done
    paths+=("$relative_path")
  done < "$manifest_path"
  if [ "${#paths[@]}" -eq 0 ]; then
    echo "Candidate manifest is empty." >&2
    return 1
  fi
}

write_marker() {
  local marker_path=$1
  local marker_value=$2
  local marker_temp="${marker_path}.tmp.$$"
  rm -f -- "$marker_temp"
  printf '%s\n' "$marker_value" > "$marker_temp"
  chmod 0600 "$marker_temp"
  sync
  mv -- "$marker_temp" "$marker_path"
  sync
}

verify_recovery_backup() {
  local recovery_backup=$1
  local relative_path
  for required_file in \
    deployment-manifest.sha256 \
    ACTIVE_BEFORE.sha256 \
    MISSING_BEFORE \
    RECOVERY_METADATA.sha256; do
    if [ ! -f "$recovery_backup/$required_file" ]; then
      echo "Promotion recovery file is missing: $recovery_backup/$required_file" >&2
      return 1
    fi
  done
  if [ ! -d "$recovery_backup/CANDIDATE" ]; then
    echo "Promotion recovery candidate is missing: $recovery_backup/CANDIDATE" >&2
    return 1
  fi
  (
    cd "$recovery_backup"
    sha256sum -c RECOVERY_METADATA.sha256 >/dev/null
    if [ -s ACTIVE_BEFORE.sha256 ]; then
      sha256sum -c ACTIVE_BEFORE.sha256 >/dev/null
    fi
    cd CANDIDATE
    sha256sum -c ../deployment-manifest.sha256 >/dev/null
  ) || return 1
  load_manifest_paths "$recovery_backup/deployment-manifest.sha256" || return 1
  for relative_path in "${paths[@]}"; do
    if [ -f "$recovery_backup/$relative_path" ]; then
      if grep -Fxq -- "$relative_path" "$recovery_backup/MISSING_BEFORE"; then
        echo "Promotion recovery path is both present and missing: $relative_path" >&2
        return 1
      fi
    elif ! grep -Fxq -- "$relative_path" "$recovery_backup/MISSING_BEFORE"; then
      echo "Promotion recovery has no prior state for: $relative_path" >&2
      return 1
    fi
  done
}

verify_active_candidate() {
  local recovery_backup=$1
  (
    cd "$active_root"
    sha256sum -c "$recovery_backup/deployment-manifest.sha256" >/dev/null 2>&1
  )
}

restore_prior_active() {
  local recovery_backup=$1
  local relative_path
  local target_path
  local temporary_path
  verify_recovery_backup "$recovery_backup" || return 1
  for relative_path in "${paths[@]}"; do
    target_path="$active_root/$relative_path"
    temporary_path="${target_path}.aegisops-promotion-tmp"
    mkdir -p "$(dirname "$target_path")" || return 1
    rm -f -- "$temporary_path" || return 1
    if [ -f "$recovery_backup/$relative_path" ]; then
      cp -p -- "$recovery_backup/$relative_path" "$temporary_path" || return 1
      mv -- "$temporary_path" "$target_path" || return 1
    else
      rm -f -- "$target_path" || return 1
    fi
  done
  sync
  if [ -s "$recovery_backup/ACTIVE_BEFORE.sha256" ]; then
    (
      cd "$active_root"
      sha256sum -c "$recovery_backup/ACTIVE_BEFORE.sha256" >/dev/null
    ) || return 1
  fi
  while IFS= read -r relative_path; do
    [ -n "$relative_path" ] || continue
    if [ -e "$active_root/$relative_path" ] || [ -L "$active_root/$relative_path" ]; then
      echo "Promotion recovery could not remove new path: $relative_path" >&2
      return 1
    fi
  done < "$recovery_backup/MISSING_BEFORE"
  write_marker "$recovery_backup/PROMOTION_ROLLED_BACK" "rolled-back" || return 1
}

resolve_journal_backup() {
  local journal_relative=""
  local journal_name
  if ! IFS= read -r journal_relative < "$journal_file"; then
    echo "Deployment promotion journal is unreadable: $journal_file" >&2
    return 1
  fi
  case "$journal_relative" in
    deploy/backups/*) ;;
    *)
      echo "Deployment promotion journal contains an unsafe path." >&2
      return 1
      ;;
  esac
  journal_name="${journal_relative#deploy/backups/}"
  case "$journal_name" in
    ''|.*|*/*|*[!A-Za-z0-9._-]*)
      echo "Deployment promotion journal contains an unsafe backup name." >&2
      return 1
      ;;
  esac
  journal_backup="$backup_root/$journal_name"
  if [ ! -d "$journal_backup" ]; then
    echo "Deployment promotion recovery backup is missing: $journal_backup" >&2
    return 1
  fi
}

clear_journal() {
  rm -f -- "$journal_file"
  sync
}

recover_journal() {
  local recovery_backup
  if [ ! -f "$journal_file" ]; then
    return 0
  fi
  recovering=1
  resolve_journal_backup || return 1
  recovery_backup="$journal_backup"
  if [ -f "$recovery_backup/PROMOTION_COMPLETE" ] \
    && verify_recovery_backup "$recovery_backup" \
    && verify_active_candidate "$recovery_backup"; then
    clear_journal || return 1
    recovering=0
    echo "Verified completed deployment promotion: $recovery_backup"
    return 0
  fi
  echo "Recovering interrupted deployment promotion from $recovery_backup" >&2
  restore_prior_active "$recovery_backup" || return 1
  clear_journal || return 1
  recovering=0
  echo "Recovered interrupted deployment promotion: $recovery_backup"
}

cleanup_active_temps() {
  local relative_path
  for relative_path in "${paths[@]:-}"; do
    [ -n "$relative_path" ] || continue
    rm -f -- "$active_root/$relative_path.aegisops-promotion-tmp"
  done
}

finish() {
  local status=$?
  trap - EXIT INT TERM
  set +e
  if [ "$status" -ne 0 ] && [ "$recovering" = "0" ] && [ -f "$journal_file" ]; then
    if ! recover_journal; then
      echo "Automatic promotion recovery failed; run --recover-only before deployment." >&2
    fi
  fi
  cleanup_active_temps
  if [ -n "$backup_temp" ] && [ -d "$backup_temp" ]; then
    rm -rf -- "$backup_temp"
  fi
  if [ -n "$lock_temp" ]; then
    rm -f -- "$lock_temp"
  fi
  release_lock
  exit "$status"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

acquire_lock
recover_journal
if [ "$recover_only" = "1" ]; then
  exit 0
fi

if [ ! -d "$candidate_root" ] || [ ! -f "$manifest" ]; then
  echo "Candidate root or manifest is missing." >&2
  exit 1
fi
candidate_root="$(cd "$candidate_root" && pwd -P)"
manifest="$(cd "$(dirname "$manifest")" && pwd -P)/$(basename "$manifest")"
case "$manifest" in
  "$candidate_root"/*) ;;
  *)
    echo "Manifest must be inside the candidate root." >&2
    exit 1
    ;;
esac

backup_name="$(basename "$backup_dir")"
backup_parent="$(dirname "$backup_dir")"
if [ ! -d "$backup_parent" ]; then
  echo "Promotion backup parent is missing: $backup_parent" >&2
  exit 1
fi
backup_parent="$(cd "$backup_parent" && pwd -P)"
case "$backup_name" in
  ''|.*|*[!A-Za-z0-9._-]*)
    echo "Promotion backup name is unsafe: $backup_name" >&2
    exit 1
    ;;
esac
if [ "$backup_parent" != "$backup_root" ]; then
  echo "Backup must be a direct child of $backup_root." >&2
  exit 1
fi
backup_dir="$backup_root/$backup_name"
if [ -e "$backup_dir" ]; then
  echo "Promotion backup already exists: $backup_dir" >&2
  exit 1
fi

load_manifest_paths "$manifest"
for relative_path in "${paths[@]}"; do
  if [ ! -f "$candidate_root/$relative_path" ] \
    || [ -L "$candidate_root/$relative_path" ]; then
    echo "Candidate file is missing or not a regular file: $relative_path" >&2
    exit 1
  fi
done
(
  cd "$candidate_root"
  sha256sum -c "${manifest#"$candidate_root"/}"
)

backup_temp="$(mktemp -d "$backup_root/.promotion-backup.XXXXXX")"
chmod 0700 "$backup_temp"
mkdir -p "$backup_temp/CANDIDATE"
: > "$backup_temp/MISSING_BEFORE"
: > "$backup_temp/ACTIVE_BEFORE.sha256"
for relative_path in "${paths[@]}"; do
  mkdir -p "$(dirname "$backup_temp/CANDIDATE/$relative_path")"
  cp -p -- "$candidate_root/$relative_path" "$backup_temp/CANDIDATE/$relative_path"
  if [ -f "$active_root/$relative_path" ] && [ ! -L "$active_root/$relative_path" ]; then
    mkdir -p "$(dirname "$backup_temp/$relative_path")"
    cp -p -- "$active_root/$relative_path" "$backup_temp/$relative_path"
    (
      cd "$backup_temp"
      sha256sum "$relative_path" >> ACTIVE_BEFORE.sha256
    )
  elif [ ! -e "$active_root/$relative_path" ] && [ ! -L "$active_root/$relative_path" ]; then
    printf '%s\n' "$relative_path" >> "$backup_temp/MISSING_BEFORE"
  else
    echo "Active deployment path is not a regular file: $relative_path" >&2
    exit 1
  fi
done
cp -p -- "$manifest" "$backup_temp/deployment-manifest.sha256"
(
  cd "$backup_temp"
  sha256sum \
    deployment-manifest.sha256 \
    ACTIVE_BEFORE.sha256 \
    MISSING_BEFORE \
    > RECOVERY_METADATA.sha256
)
verify_recovery_backup "$backup_temp"
sync
mv -- "$backup_temp" "$backup_dir"
backup_temp=""
sync

journal_temp="$(mktemp "$backup_root/.promotion-journal.XXXXXX")"
printf 'deploy/backups/%s\n' "$backup_name" > "$journal_temp"
chmod 0600 "$journal_temp"
sync
mv -- "$journal_temp" "$journal_file"
sync

for relative_path in "${paths[@]}"; do
  target_path="$active_root/$relative_path"
  temporary_path="${target_path}.aegisops-promotion-tmp"
  mkdir -p "$(dirname "$target_path")"
  rm -f -- "$temporary_path"
  cp -p -- "$backup_dir/CANDIDATE/$relative_path" "$temporary_path"
done
sync
for relative_path in "${paths[@]}"; do
  target_path="$active_root/$relative_path"
  mv -- "${target_path}.aegisops-promotion-tmp" "$target_path"
done
sync
verify_active_candidate "$backup_dir"
write_marker "$backup_dir/PROMOTION_COMPLETE" "completed"
clear_journal
printf '%s\n' "$backup_dir"
