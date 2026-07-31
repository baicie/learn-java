from __future__ import annotations

import json
import os
import subprocess
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/ci/capture-runtime-diagnostics.sh"
REDACTOR = ROOT / "deploy/scripts/redact-runtime-output.py"


def run_redactor(source: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["python3", str(REDACTOR)],
        input=source,
        text=True,
        capture_output=True,
        check=False,
    )


def test_runtime_redactor_removes_plain_basic_authorization_value():
    credential = "c2VydmVyOnN1cGVyLXNlY3JldA=="

    result = run_redactor(f"Authorization: Basic {credential}\n")

    assert result.returncode == 0, result.stderr
    assert credential not in result.stdout
    assert result.stdout == "Authorization: [REDACTED]\n"


def test_runtime_redactor_removes_access_key_assignments():
    access_key = "minio-access-secret"

    result = run_redactor(f"AIOPS_MINIO_ACCESS_KEY={access_key}\n")

    assert result.returncode == 0, result.stderr
    assert access_key not in result.stdout
    assert result.stdout == "AIOPS_MINIO_ACCESS_KEY=[REDACTED]\n"


def test_runtime_redactor_handles_structured_and_plain_credentials():
    assert REDACTOR.exists(), "shared runtime redactor is missing"
    source = textwrap.dedent(
        """\
        {"Authorization":"Bearer dynamic-json-oauth-token","X-AegisOps-Diagnosis-Grant":"dynamic-json-grant","access_token":"dynamic-json-access-token","status":"healthy"}
        {'Authorization': 'Bearer dynamic-python-oauth-token', 'X-AegisOps-Diagnosis-Grant': 'dynamic-python-grant', 'client_secret': 'dynamic-python-secret'}
        Authorization: Bearer dynamic-plain-oauth-token
        X-AegisOps-Diagnosis-Grant=dynamic-plain-grant
        AIOPS_TEST_SECRET=canary-runtime-client-secret
        """
    )
    result = subprocess.run(
        ["python3", str(REDACTOR)],
        input=source,
        text=True,
        capture_output=True,
        env={**os.environ, "AIOPS_TEST_SECRET": "canary-runtime-client-secret"},
        check=False,
    )

    assert result.returncode == 0, result.stderr
    for credential in (
        "dynamic-json-oauth-token",
        "dynamic-json-grant",
        "dynamic-json-access-token",
        "dynamic-python-oauth-token",
        "dynamic-python-grant",
        "dynamic-python-secret",
        "dynamic-plain-oauth-token",
        "dynamic-plain-grant",
        "canary-runtime-client-secret",
    ):
        assert credential not in result.stdout
    assert result.stdout.count("[REDACTED]") == 9
    assert '"status":"healthy"' in result.stdout


def test_runtime_diagnostics_never_persist_secrets_or_full_container_config(tmp_path):
    fake_bin = tmp_path / "bin"
    fake_bin.mkdir()
    fake_docker = fake_bin / "docker"
    fake_docker.write_text(
        textwrap.dedent(
            """\
            #!/usr/bin/env bash
            set -eu
            args="$*"

            case "$args" in
              *"compose"*"config --services"*)
                printf 'aiops-server\\naiops-agent\\n'
                ;;
              *"compose"*"config --images"*)
                printf 'local/aegisops:server\\nlocal/aegisops:agent\\n'
                ;;
              *"compose"*"config"*)
                printf 'AIOPS_TEST_SECRET=%s\\n' "$AIOPS_TEST_SECRET"
                ;;
              *"compose"*"logs"*)
                printf 'AIOPS_TEST_SECRET=%s\\n' "$AIOPS_TEST_SECRET"
                printf 'PASSWORD=%s\\n' "$AIOPS_TEST_PASSWORD"
                printf 'Authorization: Bearer %s\\n' "$AIOPS_TEST_TOKEN"
                printf 'Authorization: Bearer dynamic-runtime-oauth-token\\n'
                printf 'X-AegisOps-Diagnosis-Grant: dynamic-runtime-diagnosis-grant\\n'
                printf '%s\\n' '{"Authorization":"Bearer dynamic-json-oauth-token","X-AegisOps-Diagnosis-Grant":"dynamic-json-diagnosis-grant","access_token":"dynamic-json-access-token"}'
                printf "%s\\n" "{'Authorization': 'Bearer dynamic-python-oauth-token', 'X-AegisOps-Diagnosis-Grant': 'dynamic-python-diagnosis-grant'}"
                ;;
              *"compose"*"ps -q -a"*)
                printf 'compose-container-one\\ncompose-container-two\\n'
                ;;
              "inspect --format "*".NetworkSettings.Networks"*)
                printf 'deploy_default\\n'
                ;;
              "inspect --format "*)
                printf '{"name":"aegisops","image":"local/aegisops:test","status":"running","running":true,"exitCode":0,"health":"healthy"}\\n'
                ;;
              "inspect "*)
                printf '[{"Config":{"Env":["AIOPS_TEST_SECRET=%s"]},"State":{"Status":"running"}}]\\n' "$AIOPS_TEST_SECRET"
                ;;
              "network ls --filter "*)
                printf 'network name filters are forbidden\\n' >&2
                exit 2
                ;;
              "network inspect "*)
                printf '{"name":"deploy_default","driver":"bridge","scope":"local","internal":false,"attachable":false,"ingress":false,"containers":{}}\\n'
                ;;
              "network ls")
                printf 'NETWORK ID NAME\\nnetwork-id runtime-network\\n'
                ;;
              *"compose"*"ps -a"*)
                printf 'aegisops-server running\\n'
                ;;
              *)
                printf 'unexpected docker invocation: %s\\n' "$args" >&2
                exit 2
                ;;
            esac
            """
        ),
        encoding="utf-8",
    )
    fake_docker.chmod(0o755)

    output_dir = tmp_path / "runtime-diagnostics"
    canaries = {
        "AIOPS_TEST_SECRET": "canary-runtime-client-secret",
        "AIOPS_TEST_PASSWORD": "canary-runtime-password",
        "AIOPS_TEST_TOKEN": "canary-runtime-token",
    }
    env = {
        **os.environ,
        **canaries,
        "PATH": f"{fake_bin}{os.pathsep}{os.environ['PATH']}",
        "OUTPUT_DIR": str(output_dir),
        "COMPOSE_FILE": "deploy/docker-compose.app.yml",
    }

    subprocess.run(["bash", str(SCRIPT)], cwd=ROOT, env=env, check=True)

    assert not (output_dir / "compose-config.yml").exists()
    assert (output_dir / "compose-services.txt").read_text(encoding="utf-8") == (
        "aiops-server\naiops-agent\n"
    )
    assert (output_dir / "compose-images.txt").read_text(encoding="utf-8") == (
        "local/aegisops:server\nlocal/aegisops:agent\n"
    )

    artifact_text = "\n".join(
        path.read_text(encoding="utf-8", errors="replace")
        for path in output_dir.rglob("*")
        if path.is_file()
    )
    for canary in canaries.values():
        assert canary not in artifact_text
    assert "dynamic-runtime-oauth-token" not in artifact_text
    assert "dynamic-runtime-diagnosis-grant" not in artifact_text
    assert "dynamic-json-oauth-token" not in artifact_text
    assert "dynamic-json-diagnosis-grant" not in artifact_text
    assert "dynamic-json-access-token" not in artifact_text
    assert "dynamic-python-oauth-token" not in artifact_text
    assert "dynamic-python-diagnosis-grant" not in artifact_text
    assert "Config" not in artifact_text
    assert "Env" not in artifact_text

    expected_keys = {"name", "image", "status", "running", "exitCode", "health"}
    for inspect_file in (output_dir / "inspect").glob("*.json"):
        assert set(json.loads(inspect_file.read_text(encoding="utf-8"))) == expected_keys

    network_inspects = list((output_dir / "network").glob("*.json"))
    assert len(network_inspects) == 1
    network = json.loads(network_inspects[0].read_text(encoding="utf-8"))
    assert network == {
        "name": "deploy_default",
        "driver": "bridge",
        "scope": "local",
        "internal": False,
        "attachable": False,
        "ingress": False,
        "containers": {},
    }
    script_text = SCRIPT.read_text(encoding="utf-8")
    assert ".NetworkSettings.Networks" in script_text
    assert "network ls --filter name=ai-ops" not in script_text
