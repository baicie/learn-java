from __future__ import annotations

import os
import shutil
import stat
import subprocess
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[2]
INSTALL = ROOT / "deploy/install.sh"


def test_install_script_is_fail_closed_and_supports_no_start():
    text = INSTALL.read_text(encoding="utf-8")

    assert "set -Eeuo pipefail" in text
    assert "umask 077" in text
    assert "--no-start" in text
    assert "openssl genpkey -algorithm ED25519" in text
    assert "spiffe://aegisops.local/service/aegisops-app" in text
    assert "spiffe://aegisops.local/service/aiops-agent" in text


def test_install_generates_private_runtime_material(tmp_path):
    if os.name == "nt":
        pytest.skip("deployment installer targets POSIX hosts")
    if shutil.which("openssl") is None or shutil.which("bash") is None:
        pytest.skip("bash and openssl are required")

    runtime = tmp_path / "runtime"
    subprocess.run(
        [
            "bash",
            str(INSTALL),
            "--no-start",
            "--runtime-dir",
            str(runtime),
        ],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )

    expected = {
        ".env",
        "secrets/postgres_admin_password",
        "secrets/app_db_password",
        "secrets/runner_db_password",
        "secrets/root_ca.crt",
        "secrets/root_ca.key",
        "secrets/control_plane_ca.crt",
        "secrets/control_plane_ca.key",
        "secrets/agent_ca.crt",
        "secrets/agent_ca.key",
        "secrets/app.crt",
        "secrets/app.key",
        "secrets/agent.crt",
        "secrets/agent.key",
        "secrets/grant-private.pem",
        "secrets/grant-public.pem",
    }
    assert expected.issubset(
        {path.relative_to(runtime).as_posix() for path in runtime.rglob("*") if path.is_file()}
    )
    for relative_path in expected:
        path = runtime / relative_path
        assert stat.S_IMODE(path.stat().st_mode) in {0o600, 0o644}
    for private_name in (
        ".env",
        "secrets/root_ca.key",
        "secrets/control_plane_ca.key",
        "secrets/agent_ca.key",
        "secrets/app.key",
        "secrets/agent.key",
        "secrets/grant-private.pem",
        "secrets/app_db_password",
        "secrets/runner_db_password",
    ):
        assert stat.S_IMODE((runtime / private_name).stat().st_mode) == 0o600

    app_cert = subprocess.run(
        ["openssl", "x509", "-in", str(runtime / "secrets/app.crt"), "-noout", "-text"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    agent_cert = subprocess.run(
        [
            "openssl",
            "x509",
            "-in",
            str(runtime / "secrets/agent.crt"),
            "-noout",
            "-text",
        ],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    assert "URI:spiffe://aegisops.local/service/aegisops-app" in app_cert
    assert "URI:spiffe://aegisops.local/service/aiops-agent" in agent_cert

    subprocess.run(
        [
            "openssl",
            "verify",
            "-CAfile",
            str(runtime / "secrets/root_ca.crt"),
            "-untrusted",
            str(runtime / "secrets/control_plane_ca.crt"),
            str(runtime / "secrets/app.crt"),
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    subprocess.run(
        [
            "openssl",
            "verify",
            "-CAfile",
            str(runtime / "secrets/root_ca.crt"),
            "-untrusted",
            str(runtime / "secrets/agent_ca.crt"),
            str(runtime / "secrets/agent.crt"),
        ],
        check=True,
        capture_output=True,
        text=True,
    )

    wrong_app_chain = subprocess.run(
        [
            "openssl",
            "verify",
            "-CAfile",
            str(runtime / "secrets/agent_ca.crt"),
            str(runtime / "secrets/app.crt"),
        ],
        check=False,
        capture_output=True,
        text=True,
    )
    wrong_agent_chain = subprocess.run(
        [
            "openssl",
            "verify",
            "-CAfile",
            str(runtime / "secrets/control_plane_ca.crt"),
            str(runtime / "secrets/agent.crt"),
        ],
        check=False,
        capture_output=True,
        text=True,
    )
    assert wrong_app_chain.returncode != 0
    assert wrong_agent_chain.returncode != 0


def test_install_projects_compose_secrets_without_relaxing_source_material(tmp_path):
    if os.name == "nt":
        pytest.skip("deployment installer targets POSIX hosts")
    if shutil.which("openssl") is None or shutil.which("bash") is None:
        pytest.skip("bash and openssl are required")

    runtime = tmp_path / "runtime"
    _run_install(runtime, "automation")

    source_dir = runtime / "secrets"
    compose_dir = runtime / "compose-secrets"
    projected_names = {
        "postgres_admin_password",
        "app_db_password",
        "runner_db_password",
        "control_plane_ca.crt",
        "agent_ca.crt",
        "app.crt",
        "app.key",
        "agent.crt",
        "agent.key",
        "grant-private.pem",
        "grant-public.pem",
        "grant-previous-public.pem",
    }

    assert stat.S_IMODE(source_dir.stat().st_mode) == 0o700
    assert stat.S_IMODE(compose_dir.stat().st_mode) == 0o700
    assert {path.name for path in compose_dir.iterdir()} == projected_names
    for name in projected_names:
        assert (compose_dir / name).read_bytes() == (source_dir / name).read_bytes()
        assert stat.S_IMODE((compose_dir / name).stat().st_mode) == 0o644

    for private_name in (
        "app_db_password",
        "runner_db_password",
        "app.key",
        "agent.key",
        "grant-private.pem",
    ):
        assert stat.S_IMODE((source_dir / private_name).stat().st_mode) == 0o600

    assert _env_value(runtime, "AIOPS_SECRETS_DIR") == str(compose_dir)


def test_install_is_idempotent_and_mode_can_change_without_rotation(tmp_path):
    if os.name == "nt":
        pytest.skip("deployment installer targets POSIX hosts")
    if shutil.which("openssl") is None or shutil.which("bash") is None:
        pytest.skip("bash and openssl are required")

    runtime = tmp_path / "runtime"
    _run_install(runtime, "core")
    original = _security_material(runtime)

    result = _run_install(runtime, "automation")

    assert "Reusing deployment material" in result.stdout
    assert _security_material(runtime) == original
    env = (runtime / ".env").read_text(encoding="utf-8")
    assert "AIOPS_AGENT_ENABLED=true" in env
    assert "AIOPS_INTERNAL_AGENT_API_ENABLED=true" in env


def test_force_rotation_preserves_database_passwords(tmp_path):
    if os.name == "nt":
        pytest.skip("deployment installer targets POSIX hosts")
    if shutil.which("openssl") is None or shutil.which("bash") is None:
        pytest.skip("bash and openssl are required")

    runtime = tmp_path / "runtime"
    _run_install(runtime, "diagnostic")
    database_passwords = {
        name: (runtime / "secrets" / name).read_text(encoding="utf-8")
        for name in (
            "postgres_admin_password",
            "app_db_password",
            "runner_db_password",
        )
    }
    old_grant = (runtime / "secrets/grant-private.pem").read_text(encoding="utf-8")

    subprocess.run(
        [
            "bash",
            str(INSTALL),
            "--no-start",
            "--force",
            "--runtime-dir",
            str(runtime),
        ],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )

    assert {
        name: (runtime / "secrets" / name).read_text(encoding="utf-8")
        for name in database_passwords
    } == database_passwords
    assert (runtime / "secrets/grant-private.pem").read_text(encoding="utf-8") != old_grant
    assert list(runtime.glob("secrets.backup.*"))


def test_start_builds_latest_sources_and_waits_for_health(tmp_path):
    if os.name == "nt":
        pytest.skip("deployment installer targets POSIX hosts")
    if shutil.which("openssl") is None or shutil.which("bash") is None:
        pytest.skip("bash and openssl are required")

    fake_bin = tmp_path / "bin"
    fake_bin.mkdir()
    docker_log = tmp_path / "docker.log"
    fake_docker = fake_bin / "docker"
    fake_docker.write_text(
        """#!/bin/sh
printf '%s\\n' "$*" >> "$AIOPS_TEST_DOCKER_LOG"
# A successful Docker CLI invocation with no inspect payload means no such legacy
# container to the migration script.
exit 0
""",
        encoding="utf-8",
    )
    fake_docker.chmod(0o755)

    runtime = tmp_path / "runtime"
    subprocess.run(
        [
            "bash",
            str(INSTALL),
            "--mode",
            "automation",
            "--runtime-dir",
            str(runtime),
        ],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
        env={
            **os.environ,
            "PATH": f"{fake_bin}{os.pathsep}{os.environ['PATH']}",
            "AIOPS_TEST_DOCKER_LOG": str(docker_log),
        },
    )

    calls = docker_log.read_text(encoding="utf-8").splitlines()
    up = next(line for line in calls if " up " in f" {line} ")
    assert "--profile ai" in up
    assert "--profile automation" in up
    assert "up -d --build --wait" in up

    install_text = INSTALL.read_text(encoding="utf-8")
    assert install_text.index('up -d --build --wait') < install_text.index(
        'AIOPS_TARGET_STACK_HEALTHY=true'
    )
    assert '--finalize' in install_text


def test_force_rotation_preserves_previous_grant_verification_key(tmp_path):
    if os.name == "nt":
        pytest.skip("deployment installer targets POSIX hosts")
    if shutil.which("openssl") is None or shutil.which("bash") is None:
        pytest.skip("bash and openssl are required")

    runtime = tmp_path / "runtime"
    _run_install(runtime, "diagnostic")
    previous_public_key = (runtime / "secrets/grant-public.pem").read_bytes()
    previous_key_id = _env_value(runtime, "AIOPS_TASK_GRANT_KEY_ID")

    subprocess.run(
        [
            "bash",
            str(INSTALL),
            "--no-start",
            "--force",
            "--runtime-dir",
            str(runtime),
        ],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )

    assert (runtime / "secrets/grant-previous-public.pem").read_bytes() == previous_public_key
    assert _env_value(runtime, "AIOPS_TASK_GRANT_PREVIOUS_KEY_ID") == previous_key_id
    assert _env_value(runtime, "AIOPS_TASK_GRANT_KEY_ID") != previous_key_id


def _run_install(runtime: Path, mode: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            "bash",
            str(INSTALL),
            "--no-start",
            "--mode",
            mode,
            "--runtime-dir",
            str(runtime),
        ],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )


def _security_material(runtime: Path) -> dict[str, bytes]:
    return {
        path.name: path.read_bytes()
        for path in (runtime / "secrets").iterdir()
        if path.is_file()
    }


def _env_value(runtime: Path, key: str) -> str:
    for line in (runtime / ".env").read_text(encoding="utf-8").splitlines():
        name, _, value = line.partition("=")
        if name == key:
            return value
    raise AssertionError(f"missing env value: {key}")


def test_generated_runtime_directory_is_git_ignored():
    subprocess.run(
        ["git", "check-ignore", "--quiet", "deploy/runtime/secrets/app.key"],
        cwd=ROOT,
        check=True,
    )
