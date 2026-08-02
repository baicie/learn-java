from __future__ import annotations

import os
import shutil
import subprocess
from pathlib import Path

import pytest
import yaml


ROOT = Path(__file__).resolve().parents[2]
COMPOSE_FILE = ROOT / "deploy/docker-compose.zabbix.yml"
DEPLOY_SCRIPT = ROOT / "deploy/scripts/deploy-zabbix.sh"
BACKUP_SCRIPT = ROOT / "deploy/scripts/backup-zabbix.sh"
RESTORE_SCRIPT = ROOT / "deploy/scripts/restore-zabbix.sh"
COMPONENT_WORKFLOW = ROOT / ".github/workflows/deploy-component.yml"


def test_zabbix_images_default_to_current_immutable_digests():
    services = yaml.safe_load(COMPOSE_FILE.read_text(encoding="utf-8"))["services"]

    assert services["zabbix-postgres"]["image"] == (
        "postgres:16.14-alpine@sha256:"
        "57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777"
    )
    assert services["zabbix-server"]["image"] == (
        "zabbix/zabbix-server-pgsql:alpine-7.0.29@sha256:"
        "97cac0f85f85e82cc5f584ce7958bd3de2b6eec6c5ce7b0df49626471024cad2"
    )
    assert services["zabbix-web"]["image"] == (
        "zabbix/zabbix-web-nginx-pgsql:alpine-7.0.29@sha256:"
        "f8c2638904def2b610045726d37504271f31ec2ff1093d37d133ebdc24acfb0f"
    )
    assert services["zabbix-agent2"]["image"] == (
        "zabbix/zabbix-agent2:alpine-7.0.29@sha256:"
        "953c3d229fcc0b2c481bd148a44f1c4a3df7ec5390aa3c5cdaf8de163b02ae1c"
    )
    compose_text = COMPOSE_FILE.read_text(encoding="utf-8")
    assert "latest" not in compose_text
    assert "ZABBIX_POSTGRES_IMAGE" not in compose_text
    assert "ZABBIX_SERVER_IMAGE" not in compose_text
    assert "ZABBIX_WEB_IMAGE" not in compose_text
    assert "ZABBIX_AGENT2_IMAGE" not in compose_text


def test_zabbix_deploy_takes_validated_database_backup_before_pull_and_up():
    script = DEPLOY_SCRIPT.read_text(encoding="utf-8")

    backup_index = script.index('bash "$ZABBIX_BACKUP_SCRIPT"')
    pull_index = script.index("compose pull")
    up_index = script.index("compose up")

    assert backup_index < pull_index < up_index
    assert 'ZABBIX_BACKUP_SCRIPT="${ZABBIX_BACKUP_SCRIPT:-' in script
    assert 'ZABBIX_RESTORE_SCRIPT="${ZABBIX_RESTORE_SCRIPT:-' in script
    assert 'echo "Rollback command:' in script


def test_zabbix_backup_and_restore_entrypoints_are_fail_closed():
    for script_path in (BACKUP_SCRIPT, RESTORE_SCRIPT):
        script = script_path.read_text(encoding="utf-8")
        assert "set -Eeuo pipefail" in script
        assert "umask 077" in script

    backup = BACKUP_SCRIPT.read_text(encoding="utf-8")
    restore = RESTORE_SCRIPT.read_text(encoding="utf-8")

    assert "pg_dump" in backup
    assert "pg_restore --list" in backup
    assert "SHA256SUMS" in backup
    assert "sha256sum -c" in restore
    assert "pg_restore" in restore
    assert '"$execute" != "1"' in restore
    assert '"$confirmation" != "RESTORE-ZABBIX"' in restore


def test_zabbix_restore_dry_run_never_calls_docker(tmp_path: Path):
    bash = shutil.which("bash")
    if bash is None:
        pytest.skip("bash is required")

    fake_bin = tmp_path / "bin"
    fake_bin.mkdir()
    marker = tmp_path / "docker-called"
    fake_docker = fake_bin / "docker"
    fake_docker.write_text(
        "#!/usr/bin/env bash\n"
        'printf "called\\n" > "$DOCKER_MARKER"\n'
        "exit 99\n",
        encoding="utf-8",
    )
    fake_docker.chmod(0o755)
    backup_dir = tmp_path / "zabbix-backup"
    backup_dir.mkdir()

    result = subprocess.run(
        [bash, str(RESTORE_SCRIPT), "--backup-dir", str(backup_dir)],
        cwd=ROOT,
        capture_output=True,
        text=True,
        env={
            "PATH": f"{fake_bin}:{Path('/usr/bin')}:{Path('/bin')}",
            "DOCKER_MARKER": str(marker),
        },
        check=False,
    )

    assert result.returncode == 2
    assert "--execute --confirm RESTORE-ZABBIX" in result.stderr
    assert not marker.exists()


def test_zabbix_workflow_backs_up_active_release_before_candidate_promotion():
    workflow = yaml.safe_load(COMPONENT_WORKFLOW.read_text(encoding="utf-8"))
    deploy_step = next(
        step
        for step in workflow["jobs"]["deploy"]["steps"]
        if step.get("name") == "Deploy staged Zabbix candidate"
    )
    script = deploy_step["with"]["script"]

    state_probe_index = script.index("--probe-only")
    backup_index = script.index(
        'bash "$CANDIDATE_ROOT/deploy/scripts/backup-zabbix.sh"',
        state_probe_index,
    )
    promotion_index = script.index('bash "$PROMOTE_SCRIPT"', backup_index)
    deploy_index = script.index('bash "$DEPLOY_SCRIPT"')

    assert state_probe_index < backup_index < promotion_index < deploy_index
    assert 'ZABBIX_PREDEPLOY_BACKUP_DIR="$predeploy_backup"' in script


def test_zabbix_backup_records_exact_running_images_in_compose_override(
    tmp_path: Path,
):
    bash = shutil.which("bash")
    if bash is None:
        pytest.skip("bash is required")

    app_dir = tmp_path / "aegisops"
    deploy_dir = app_dir / "deploy"
    deploy_dir.mkdir(parents=True)
    shutil.copy2(COMPOSE_FILE, deploy_dir / "docker-compose.zabbix.yml")
    (deploy_dir / ".env.zabbix").write_text(
        "ZABBIX_DB_PASSWORD=test-only\n", encoding="utf-8"
    )

    fake_bin = tmp_path / "bin"
    fake_bin.mkdir()
    fake_docker = fake_bin / "docker"
    fake_docker.write_text(
        """#!/usr/bin/env bash
set -Eeuo pipefail
if [ "$1" = "inspect" ]; then
  format=$3
  container=$4
  case "$format" in
    *State.Running*) printf 'true\\n' ;;
    *Config.Image*) printf 'floating/%s:latest\\n' "$container" ;;
    *'.Image'*) printf 'sha256:%064d\\n' "${#container}" ;;
    *) exit 90 ;;
  esac
elif [ "$1" = "image" ] && [ "$2" = "inspect" ]; then
  format=$4
  image_id=$5
  case "$format" in
    *'join .RepoDigests'*) exit 92 ;;
    *'range .RepoDigests'*) printf 'registry.invalid/zabbix@%s\\n' "$image_id" ;;
    *) exit 93 ;;
  esac
elif [ "$1" = "compose" ]; then
  exit 0
elif [ "$1" = "exec" ]; then
  if printf '%s\\n' "$*" | grep -q 'pg_dump'; then
    printf 'validated-dump\\n'
  else
    cat >/dev/null
  fi
else
  exit 91
fi
""",
        encoding="utf-8",
    )
    fake_docker.chmod(0o755)

    result = subprocess.run(
        [bash, str(BACKUP_SCRIPT)],
        cwd=ROOT,
        capture_output=True,
        text=True,
        env={
            **os.environ,
            "APP_DIR": str(app_dir),
            "PATH": f"{fake_bin}:{os.environ['PATH']}",
        },
        check=False,
    )

    assert result.returncode == 0, result.stderr
    backup_dir = Path(result.stdout.strip())
    rollback_compose = backup_dir / "docker-compose.rollback.yml"
    assert rollback_compose.is_file()
    rollback = yaml.safe_load(rollback_compose.read_text(encoding="utf-8"))
    assert set(rollback["services"]) == {
        "zabbix-postgres",
        "zabbix-server",
        "zabbix-web",
        "zabbix-agent2",
    }
    for service in rollback["services"].values():
        assert service["image"].startswith("registry.invalid/zabbix@sha256:")
    assert "docker-compose.rollback.yml" in (
        backup_dir / "SHA256SUMS"
    ).read_text(encoding="utf-8")
    assert not (backup_dir / ".env.zabbix").exists()
    assert ".env.zabbix" not in (backup_dir / "SHA256SUMS").read_text(
        encoding="utf-8"
    )


def test_zabbix_restore_uses_exact_image_override_before_database_replacement():
    restore = RESTORE_SCRIPT.read_text(encoding="utf-8")

    assert "docker-compose.rollback.yml" in restore
    assert '"$resolved_backup/docker-compose.rollback.yml"' in restore
    assert 'ZABBIX_RESTORE_PULL_POLICY:-always' in restore
    candidate_config_index = restore.index('"${candidate_compose[@]}" config --quiet')
    candidate_pull_index = restore.index(
        '"${candidate_compose[@]}" pull --policy "$PULL_POLICY"'
    )
    activate_compose_index = restore.index(
        'mv -- "$compose_temp" "$COMPOSE_FILE"'
    )
    stop_stack_index = restore.index('"${compose[@]}" down --remove-orphans')
    postgres_verify_index = restore.index(
        'verify_container_image "$POSTGRES_CONTAINER"'
    )
    drop_database_index = restore.index("dropdb")

    assert (
        candidate_config_index
        < candidate_pull_index
        < activate_compose_index
        < stop_stack_index
    )
    assert postgres_verify_index < drop_database_index
    assert 'verify_container_image "$container_name"' in restore
    assert "--single-transaction" in restore
    assert "--exit-on-error" in restore
    assert '$resolved_backup/.env.zabbix' not in restore


@pytest.mark.parametrize(
    ("container_state", "existing_volume"),
    [
        ("stopped", ""),
        ("absent", "aegisops-zabbix_zabbix_postgres_data"),
    ],
)
def test_zabbix_deploy_rejects_unbackupable_existing_state_before_pull(
    tmp_path: Path,
    container_state: str,
    existing_volume: str,
):
    bash = shutil.which("bash")
    if bash is None:
        pytest.skip("bash is required")

    app_dir = tmp_path / "aegisops"
    deploy_dir = app_dir / "deploy"
    scripts_dir = deploy_dir / "scripts"
    scripts_dir.mkdir(parents=True)
    compose_file = deploy_dir / "docker-compose.zabbix.yml"
    shutil.copy2(COMPOSE_FILE, compose_file)
    (deploy_dir / ".env.zabbix").write_text(
        "ZABBIX_DB_PASSWORD=test-only\n", encoding="utf-8"
    )
    network_guard = scripts_dir / "network-guard.sh"
    network_guard.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
    network_guard.chmod(0o700)

    fake_bin = tmp_path / "bin"
    fake_bin.mkdir()
    docker_log = tmp_path / "docker.log"
    fake_docker = fake_bin / "docker"
    fake_docker.write_text(
        """#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\\n' "$*" >> "$MOCK_DOCKER_LOG"
if [ "$1 ${2:-}" = "network inspect" ]; then
  printf 'aegisops-app\\n'
elif [ "$1" = "inspect" ]; then
  if [ "$MOCK_CONTAINER_STATE" = "absent" ]; then
    exit 1
  fi
  case "$*" in
    *State.Running*) printf 'false\\n' ;;
    *State.Status*) printf 'exited\\n' ;;
    *) printf '{}\\n' ;;
  esac
elif [ "$1 ${2:-}" = "volume inspect" ]; then
  [ -n "$MOCK_EXISTING_VOLUME" ] || exit 1
  [ "${3:-}" = "$MOCK_EXISTING_VOLUME" ] || exit 1
elif [ "$1 ${2:-}" = "volume ls" ]; then
  printf '%s\\n' "$MOCK_EXISTING_VOLUME"
elif [ "$1" = "compose" ]; then
  case "$*" in
    *" port zabbix-web 8080") printf '127.0.0.1:8083\\n' ;;
    *" port zabbix-server 10051") printf '127.0.0.1:10051\\n' ;;
  esac
fi
""",
        encoding="utf-8",
    )
    fake_docker.chmod(0o755)

    result = subprocess.run(
        [bash, str(DEPLOY_SCRIPT)],
        cwd=ROOT,
        capture_output=True,
        text=True,
        env={
            **os.environ,
            "APP_DIR": str(app_dir),
            "COMPOSE_FILE": str(compose_file),
            "ZABBIX_ENV_FILE": str(deploy_dir / ".env.zabbix"),
            "ZABBIX_NETWORK_SCRIPT": str(network_guard),
            "ZABBIX_BACKUP_SCRIPT": str(BACKUP_SCRIPT),
            "ZABBIX_RESTORE_SCRIPT": str(RESTORE_SCRIPT),
            "ZABBIX_BACKUP_ROOT": str(deploy_dir / "backups"),
            "MOCK_CONTAINER_STATE": container_state,
            "MOCK_EXISTING_VOLUME": existing_volume,
            "MOCK_DOCKER_LOG": str(docker_log),
            "PATH": f"{fake_bin}{os.pathsep}{os.environ['PATH']}",
        },
        check=False,
    )

    assert result.returncode != 0
    assert "cannot be backed up safely" in result.stderr
    docker_calls = docker_log.read_text(encoding="utf-8")
    assert " pull" not in docker_calls
    assert " up " not in docker_calls


def test_zabbix_mutating_entrypoints_guard_network_before_local_or_database_changes():
    deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
    restore = RESTORE_SCRIPT.read_text(encoding="utf-8")

    deploy_network_index = deploy.index('bash "$ZABBIX_NETWORK_SCRIPT"')
    deploy_env_write_index = deploy.index('if [ ! -f "$ENV_FILE" ]')
    restore_network_index = restore.index('bash "$ZABBIX_NETWORK_SCRIPT"')
    rescue_backup_index = restore.index('rescue_backup="$(APP_DIR=')
    drop_database_index = restore.index("dropdb")

    assert deploy_network_index < deploy_env_write_index
    assert restore_network_index < rescue_backup_index < drop_database_index
