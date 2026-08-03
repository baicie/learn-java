from __future__ import annotations

import os
import shutil
import subprocess
from pathlib import Path

import pytest
import yaml


ROOT = Path(__file__).resolve().parents[2]
COMPOSE_FILE = ROOT / "deploy/docker-compose.core.yml"
DEPLOY_SCRIPT = ROOT / "deploy/scripts/deploy-app.sh"
BACKUP_SCRIPT = ROOT / "deploy/scripts/backup-core.sh"
RESTORE_SCRIPT = ROOT / "deploy/scripts/restore-core.sh"
RELEASE_WORKFLOW = ROOT / ".github/workflows/release-verify.yml"

POSTGRES_IMAGE = (
    "postgres:16.14-alpine@sha256:"
    "57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777"
)


def test_core_postgres_runtime_images_are_digest_pinned():
    services = yaml.safe_load(COMPOSE_FILE.read_text(encoding="utf-8"))["services"]

    assert services["postgres"]["image"] == POSTGRES_IMAGE
    assert services["runner-db-permissions"]["image"] == POSTGRES_IMAGE


def test_core_backup_and_restore_entrypoints_are_fail_closed():
    assert BACKUP_SCRIPT.is_file()
    assert RESTORE_SCRIPT.is_file()
    backup = BACKUP_SCRIPT.read_text(encoding="utf-8")
    restore = RESTORE_SCRIPT.read_text(encoding="utf-8")

    for script in (backup, restore):
        assert "set -Eeuo pipefail" in script
        assert "umask 077" in script

    assert "pg_dump" in backup
    assert "pg_restore --list" in backup
    assert "SHA256SUMS" in backup
    assert ".env" not in "\n".join(
        line for line in backup.splitlines() if "cp " in line
    )
    assert "sha256sum -c" in restore
    assert "--single-transaction" in restore
    assert "--exit-on-error" in restore
    assert 'CORE_RESTORE_PULL_POLICY:-always' in restore
    assert '"$execute" != "1"' in restore
    assert '"$confirmation" != "RESTORE-CORE"' in restore


def test_core_backup_probe_defers_known_legacy_topology_to_migration(tmp_path: Path):
    bash = shutil.which("bash")
    if bash is None:
        pytest.skip("bash is required")

    fake_bin = tmp_path / "bin"
    fake_bin.mkdir()
    docker_log = tmp_path / "docker.log"
    fake_docker = fake_bin / "docker"
    fake_docker.write_text(
        """#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "$MOCK_DOCKER_LOG"
if [ "$1" = "inspect" ]; then
  case "$*" in
    *State.Running*) printf 'true\n' ;;
    *com.docker.compose.project*) printf 'deploy\n' ;;
    *com.docker.compose.service*) printf 'postgres\n' ;;
    *Config.Env*) printf 'POSTGRES_USER=aegisops\nPOSTGRES_DB=aegisops\n' ;;
    *) exit 90 ;;
  esac
else
  exit 91
fi
""",
        encoding="utf-8",
    )
    fake_docker.chmod(0o755)

    result = subprocess.run(
        [bash, str(BACKUP_SCRIPT), "--probe-only"],
        cwd=ROOT,
        capture_output=True,
        text=True,
        env={
            **os.environ,
            "PATH": f"{fake_bin}{os.pathsep}{os.environ['PATH']}",
            "MOCK_DOCKER_LOG": str(docker_log),
        },
        check=False,
    )

    assert result.returncode == 0, result.stderr
    assert result.stdout.strip() == "legacy"
    assert "aegisops-app" not in docker_log.read_text(encoding="utf-8")


def test_core_restore_pulls_rollback_images_before_active_descriptor_or_downtime():
    restore = RESTORE_SCRIPT.read_text(encoding="utf-8")

    candidate_config_index = restore.index('"${candidate_compose[@]}" config --quiet')
    candidate_pull_index = restore.index(
        '"${candidate_compose[@]}" pull --policy "$PULL_POLICY"'
    )
    activate_compose_index = restore.index(
        'mv -- "$compose_temp" "$COMPOSE_FILE"'
    )
    stop_stack_index = restore.index('"${compose[@]}" down --remove-orphans')

    assert (
        candidate_config_index
        < candidate_pull_index
        < activate_compose_index
        < stop_stack_index
    )


def test_core_restore_rescue_backup_inherits_runtime_identity_overrides():
    restore = RESTORE_SCRIPT.read_text(encoding="utf-8")

    for assignment in (
        'COMPOSE_FILE="$COMPOSE_FILE"',
        'CORE_BACKUP_ROOT="$BACKUP_ROOT"',
        'CORE_POSTGRES_CONTAINER="$POSTGRES_CONTAINER"',
        'CORE_APP_CONTAINER="$APP_CONTAINER"',
        'CORE_AGENT_CONTAINER="$AGENT_CONTAINER"',
        'CORE_RUNNER_CONTAINER="$RUNNER_CONTAINER"',
        'CORE_POSTGRES_VOLUME="$POSTGRES_VOLUME"',
    ):
        assert assignment in restore


def test_core_restore_reapplies_app_ownership_after_no_owner_restore():
    restore = RESTORE_SCRIPT.read_text(encoding="utf-8")

    ownership_sql = 'deploy/init/003-migrate-legacy-owner.sql'
    assert ownership_sql in restore
    assert '"$POSTGRES_USER"' in restore
    assert "rolname = '\\''aegisops_app'\\''" in restore
    assert 'Required Core database role is missing: aegisops_app' in restore
    assert '--set=database_name="$POSTGRES_DB"' in restore
    ownership_apply_index = restore.index('echo "Applying Core application ownership normalization"')
    ownership_apply = restore[ownership_apply_index:]
    assert "--single-transaction" in ownership_apply
    assert "--set=ON_ERROR_STOP=1" in ownership_apply
    preflight_index = restore.index('"$APP_OWNERSHIP_SQL"; do')
    network_index = restore.index('bash "$NETWORK_SCRIPT"')
    assert preflight_index < network_index
    apply_sql_index = restore.index('< "$APP_OWNERSHIP_SQL"')
    assert restore.index("pg_restore") < apply_sql_index
    assert apply_sql_index < restore.index(
        '"${compose[@]}" up -d --remove-orphans --wait'
    )


def test_core_deploy_backs_up_database_before_migration_pull_and_up():
    deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")

    backup_index = deploy.index('bash "$CORE_BACKUP_SCRIPT"')
    migration_index = deploy.index('bash "$LEGACY_MIGRATION_SCRIPT"')
    pull_index = deploy.index("compose pull")
    up_index = deploy.index("compose up")

    assert backup_index < migration_index < pull_index < up_index
    assert 'CORE_PREDEPLOY_BACKUP_DIR="${CORE_PREDEPLOY_BACKUP_DIR:-}"' in deploy
    assert 'echo "Rollback command:' in deploy


def test_core_deploy_defers_supported_legacy_state_to_migration_backup():
    deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
    workflow = yaml.safe_load(RELEASE_WORKFLOW.read_text(encoding="utf-8"))
    remote = next(
        step
        for step in workflow["jobs"]["deploy"]["steps"]
        if step.get("name") == "Apply staged immutable release"
    )["with"]["script"]

    assert "legacy)" in deploy
    assert deploy.index("legacy)") < deploy.index('bash "$LEGACY_MIGRATION_SCRIPT"')
    assert 'elif [ "$core_state" = "legacy" ]; then' in remote
    assert remote.index('elif [ "$core_state" = "legacy" ]; then') < remote.rindex(
        'bash "$PROMOTE_SCRIPT"'
    )


def test_core_workflow_backs_up_active_database_before_candidate_promotion():
    workflow = yaml.safe_load(RELEASE_WORKFLOW.read_text(encoding="utf-8"))
    deploy_step = next(
        step
        for step in workflow["jobs"]["deploy"]["steps"]
        if step.get("name") == "Apply staged immutable release"
    )
    script = deploy_step["with"]["script"]

    recovery_index = script.index("--recover-only")
    probe_index = script.index("--probe-only")
    backup_index = script.index("backup-core.sh", probe_index)
    promotion_index = script.index('bash "$PROMOTE_SCRIPT"', backup_index)
    deploy_index = script.index('bash "$DEPLOY_SCRIPT"')

    assert recovery_index < probe_index < backup_index < promotion_index < deploy_index
    assert 'CORE_PREDEPLOY_BACKUP_DIR="$predeploy_backup"' in script
