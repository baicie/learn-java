from __future__ import annotations

import os
import shutil
import subprocess
import uuid
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[2]
BACKUP_SCRIPT = ROOT / "deploy/scripts/backup-zabbix.sh"
RESTORE_SCRIPT = ROOT / "deploy/scripts/restore-zabbix.sh"


def _run(
    command: list[str],
    *,
    env: dict[str, str] | None = None,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        command,
        cwd=ROOT,
        env=env,
        capture_output=True,
        text=True,
        check=False,
    )
    if check and result.returncode != 0:
        pytest.fail(
            f"command failed ({result.returncode}): {' '.join(command)}\n"
            f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}"
        )
    return result


def _compose_text(
    postgres_image: str,
    sidecar_image: str,
    containers: dict[str, str],
    project_name: str,
) -> str:
    sidecars = "\n".join(
        f"""  {service}:
    image: {sidecar_image}
    container_name: {container}
    command: [\"sh\", \"-c\", \"sleep 3600\"]
"""
        for service, container in (
            ("zabbix-server", containers["zabbix-server"]),
            ("zabbix-web", containers["zabbix-web"]),
            ("zabbix-agent2", containers["zabbix-agent2"]),
        )
    )
    return f"""name: {project_name}

services:
  zabbix-postgres:
    image: {postgres_image}
    container_name: {containers["zabbix-postgres"]}
    environment:
      POSTGRES_DB: zabbix
      POSTGRES_USER: zabbix
      POSTGRES_PASSWORD: ${{ZABBIX_DB_PASSWORD}}
    healthcheck:
      test: [\"CMD-SHELL\", \"pg_isready -U zabbix -d zabbix\"]
      interval: 2s
      timeout: 2s
      retries: 30
    volumes:
      - zabbix_postgres_data:/var/lib/postgresql/data
{sidecars}
volumes:
  zabbix_postgres_data:
"""


@pytest.mark.skipif(
    os.environ.get("AIOPS_RUN_ZABBIX_RESTORE_TEST") != "1",
    reason="set AIOPS_RUN_ZABBIX_RESTORE_TEST=1 for the destructive isolated Docker test",
)
def test_zabbix_backup_restores_old_database_and_exact_images(tmp_path: Path):
    docker = shutil.which("docker")
    bash = shutil.which("bash")
    if docker is None or bash is None:
        pytest.skip("Docker and Bash are required")
    if _run([docker, "info"], check=False).returncode != 0:
        pytest.skip("Docker daemon is unavailable")

    suffix = uuid.uuid4().hex[:8]
    project_name = f"aegisops-zabbix-restore-{suffix}"
    containers = {
        service: f"aegisops-zabbix-restore-test-{suffix}-{short_name}"
        for service, short_name in (
            ("zabbix-postgres", "postgres"),
            ("zabbix-server", "server"),
            ("zabbix-web", "web"),
            ("zabbix-agent2", "agent2"),
        )
    }
    existing = set(
        _run([docker, "ps", "-a", "--format", "{{.Names}}"]).stdout.splitlines()
    )
    conflicts = existing.intersection(containers.values())
    if conflicts:
        pytest.skip(f"reserved Zabbix containers already exist: {sorted(conflicts)}")

    app_dir = tmp_path / "aegisops"
    deploy_dir = app_dir / "deploy"
    deploy_dir.mkdir(parents=True)
    compose_file = deploy_dir / "docker-compose.zabbix.yml"
    env_file = deploy_dir / ".env.zabbix"
    network_script = deploy_dir / "noop-network-guard.sh"
    compose_file.write_text(
        _compose_text(
            "postgres:16-alpine", "alpine:3.20", containers, project_name
        ),
        encoding="utf-8",
    )
    env_file.write_text("ZABBIX_DB_PASSWORD=restore-test-only\n", encoding="utf-8")
    env_file.chmod(0o600)
    network_script.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
    network_script.chmod(0o700)
    compose = [
        docker,
        "compose",
        "--env-file",
        str(env_file),
        "-f",
        str(compose_file),
    ]
    script_env = {
        **os.environ,
        "APP_DIR": str(app_dir),
        "COMPOSE_FILE": str(compose_file),
        "ZABBIX_ENV_FILE": str(env_file),
        "ZABBIX_NETWORK_SCRIPT": str(network_script),
        "ZABBIX_POSTGRES_CONTAINER": containers["zabbix-postgres"],
        "ZABBIX_SERVER_CONTAINER": containers["zabbix-server"],
        "ZABBIX_WEB_CONTAINER": containers["zabbix-web"],
        "ZABBIX_AGENT2_CONTAINER": containers["zabbix-agent2"],
        "ZABBIX_RESTORE_PULL_POLICY": "missing",
    }

    try:
        _run([*compose, "up", "-d", "--wait", "--wait-timeout", "120"])
        _run(
            [
                docker,
                "exec",
                containers["zabbix-postgres"],
                "psql",
                "-v",
                "ON_ERROR_STOP=1",
                "--username",
                "zabbix",
                "--dbname",
                "zabbix",
                "-c",
                "create table rollback_marker(value text); "
                "insert into rollback_marker values ('before');",
            ]
        )
        backup_result = _run([bash, str(BACKUP_SCRIPT)], env=script_env)
        backup_dir = Path(backup_result.stdout.strip())
        expected_images = {
            line.split("\t")[0]: line.split("\t")[2]
            for line in (backup_dir / "images.before.tsv")
            .read_text(encoding="utf-8")
            .splitlines()
        }
        assert set(expected_images) == set(containers.values())

        compose_file.write_text(
            _compose_text("postgres:16", "nginx:alpine", containers, project_name),
            encoding="utf-8",
        )
        _run(
            [
                *compose,
                "up",
                "-d",
                "--force-recreate",
                "--wait",
                "--wait-timeout",
                "120",
            ]
        )
        _run(
            [
                docker,
                "exec",
                containers["zabbix-postgres"],
                "psql",
                "-v",
                "ON_ERROR_STOP=1",
                "--username",
                "zabbix",
                "--dbname",
                "zabbix",
                "-c",
                "update rollback_marker set value = 'after'; "
                "create table migration_only(id integer);",
            ]
        )

        restore_result = _run(
            [
                bash,
                str(RESTORE_SCRIPT),
                "--backup-dir",
                str(backup_dir),
                "--execute",
                "--confirm",
                "RESTORE-ZABBIX",
            ],
            env=script_env,
        )

        marker = _run(
            [
                docker,
                "exec",
                containers["zabbix-postgres"],
                "psql",
                "--tuples-only",
                "--no-align",
                "--username",
                "zabbix",
                "--dbname",
                "zabbix",
                "-c",
                "select value from rollback_marker;",
            ]
        ).stdout.strip()
        migration_table = _run(
            [
                docker,
                "exec",
                containers["zabbix-postgres"],
                "psql",
                "--tuples-only",
                "--no-align",
                "--username",
                "zabbix",
                "--dbname",
                "zabbix",
                "-c",
                "select to_regclass('public.migration_only');",
            ]
        ).stdout.strip()

        assert marker == "before"
        assert migration_table == ""
        for container in containers.values():
            actual_image = _run(
                [docker, "inspect", "--format", "{{.Image}}", container]
            ).stdout.strip()
            assert actual_image == expected_images[container]
        assert "Current Zabbix state preserved at" in restore_result.stdout
    finally:
        _run([*compose, "down", "--volumes", "--remove-orphans"], check=False)
