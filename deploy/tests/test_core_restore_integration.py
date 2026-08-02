from __future__ import annotations

import hashlib
import os
import shutil
import subprocess
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[2]
BACKUP_SCRIPT = ROOT / "deploy/scripts/backup-core.sh"
RESTORE_SCRIPT = ROOT / "deploy/scripts/restore-core.sh"
POSTGRES_IMAGE = "postgres:16-alpine"


@pytest.mark.skipif(
    os.environ.get("AIOPS_RUN_CORE_RESTORE_TEST") != "1",
    reason="set AIOPS_RUN_CORE_RESTORE_TEST=1 to run destructive isolated restore test",
)
def test_core_backup_restore_round_trip(tmp_path: Path):
    if shutil.which("docker") is None:
        pytest.skip("docker is required")

    suffix = hashlib.sha256(str(tmp_path).encode()).hexdigest()[:10]
    project = f"aegisops-core-restore-{suffix}"
    postgres_container = f"{project}-postgres"
    app_container = f"{project}-app"
    agent_container = f"{project}-agent"
    runner_container = f"{project}-runner"
    postgres_volume = f"{project}-data"
    app_dir = tmp_path / "aegisops"
    deploy_dir = app_dir / "deploy"
    runtime_dir = deploy_dir / "runtime"
    scripts_dir = deploy_dir / "scripts"
    backup_root = deploy_dir / "backups"
    deploy_dir.mkdir(parents=True)
    runtime_dir.mkdir()
    scripts_dir.mkdir()
    (runtime_dir / ".env").write_text("CORE_TEST=1\n", encoding="utf-8")
    network_guard = scripts_dir / "network-guard.sh"
    network_guard.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
    network_guard.chmod(0o700)
    compose_file = deploy_dir / "docker-compose.core.yml"
    compose_file.write_text(
        f"""name: {project}
services:
  postgres:
    image: {POSTGRES_IMAGE}
    container_name: {postgres_container}
    environment:
      POSTGRES_DB: coredb
      POSTGRES_USER: core_admin
      POSTGRES_PASSWORD: integration-only
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U core_admin -d coredb"]
      interval: 1s
      timeout: 2s
      retries: 30
    volumes:
      - core_data:/var/lib/postgresql/data
  aegisops-app:
    image: {POSTGRES_IMAGE}
    container_name: {app_container}
    entrypoint: ["/bin/sh", "-ec"]
    command: ["exec tail -f /dev/null"]
    depends_on:
      postgres:
        condition: service_healthy
volumes:
  core_data:
    name: {postgres_volume}
""",
        encoding="utf-8",
    )
    compose = ["docker", "compose", "-f", str(compose_file)]
    environment = {
        **os.environ,
        "APP_DIR": str(app_dir),
        "COMPOSE_FILE": str(compose_file),
        "AIOPS_RUNTIME_DIR": str(runtime_dir),
        "CORE_BACKUP_ROOT": str(backup_root),
        "CORE_POSTGRES_CONTAINER": postgres_container,
        "CORE_APP_CONTAINER": app_container,
        "CORE_AGENT_CONTAINER": agent_container,
        "CORE_RUNNER_CONTAINER": runner_container,
        "CORE_POSTGRES_VOLUME": postgres_volume,
        "CORE_RESTORE_PULL_POLICY": "missing",
        "CORE_BACKUP_SCRIPT": str(BACKUP_SCRIPT),
        "ZABBIX_NETWORK_SCRIPT": str(network_guard),
    }

    try:
        subprocess.run([*compose, "up", "-d", "--wait"], check=True, cwd=ROOT)
        _psql(
            postgres_container,
            "create table restore_probe(value text not null); "
            "insert into restore_probe values ('before');",
        )
        backup_result = subprocess.run(
            ["bash", str(BACKUP_SCRIPT)],
            check=True,
            cwd=ROOT,
            env=environment,
            capture_output=True,
            text=True,
        )
        backup_dir = Path(backup_result.stdout.strip())
        assert backup_dir.is_dir()

        _psql(postgres_container, "update restore_probe set value = 'after';")
        assert _query(postgres_container) == "after"

        subprocess.run(
            [
                "bash",
                str(RESTORE_SCRIPT),
                "--backup-dir",
                str(backup_dir),
                "--execute",
                "--confirm",
                "RESTORE-CORE",
            ],
            check=True,
            cwd=ROOT,
            env=environment,
        )
        assert _query(postgres_container) == "before"
    finally:
        subprocess.run(
            [*compose, "down", "--volumes", "--remove-orphans"],
            cwd=ROOT,
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )


def _psql(container: str, sql: str) -> None:
    subprocess.run(
        [
            "docker",
            "exec",
            "--env",
            "CORE_TEST_SQL",
            container,
            "sh",
            "-ec",
            'exec psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" '
            '--set=ON_ERROR_STOP=1 --command "$CORE_TEST_SQL"',
        ],
        check=True,
        env={**os.environ, "CORE_TEST_SQL": sql},
    )


def _query(container: str) -> str:
    result = subprocess.run(
        [
            "docker",
            "exec",
            container,
            "sh",
            "-ec",
            'exec psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" '
            "--tuples-only --no-align --command 'select value from restore_probe'",
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()
