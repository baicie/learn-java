from __future__ import annotations

import os
import shutil
import stat
import subprocess
from pathlib import Path

import pytest
import yaml


ROOT = Path(__file__).resolve().parents[2]


@pytest.mark.parametrize(
    "relative_path",
    [
        "scripts/deploy/build-images.sh",
        "scripts/deploy/generate-secrets.sh",
        "scripts/deploy/package-offline.sh",
        "scripts/deploy/load-offline-images.sh",
        "scripts/deploy/render-helm.sh",
        "scripts/deploy/verify-offline-package.sh",
        "deploy/install.sh",
        "deploy/scripts/backup-zabbix.sh",
        "deploy/scripts/ensure-zabbix-api-network.sh",
        "deploy/scripts/migrate-legacy-compose.sh",
        "deploy/scripts/promote-deployment-candidate.sh",
        "deploy/scripts/restore-zabbix.sh",
    ],
)
def test_deploy_scripts_exist_and_use_strict_shell_mode(relative_path: str):
    text = (ROOT / relative_path).read_text(encoding="utf-8")

    assert "set -Eeuo pipefail" in text or "set -euo pipefail" in text


def test_generate_secrets_emits_mtls_task_grants_and_separate_db_passwords(
    tmp_path: Path,
):
    bash = shutil.which("bash")
    openssl = shutil.which("openssl")
    if bash is None or openssl is None:
        pytest.skip("bash and openssl are required")

    output = tmp_path / "generated-secrets.values.yaml"
    subprocess.run(
        [bash, str(ROOT / "scripts/deploy/generate-secrets.sh"), str(output)],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )

    values = yaml.safe_load(output.read_text(encoding="utf-8"))
    security = values["security"]
    task_grant = security["taskGrant"]
    mtls = security["mtls"]
    postgres = values["external"]["postgres"]

    assert stat.S_IMODE(output.stat().st_mode) == 0o600
    assert values["fullnameOverride"] == "aegisops"
    assert task_grant["privateKey"].startswith("-----BEGIN PRIVATE KEY-----")
    assert task_grant["publicKey"].startswith("-----BEGIN PUBLIC KEY-----")
    assert mtls["appCertificate"].startswith("-----BEGIN CERTIFICATE-----")
    assert mtls["agentCertificate"].startswith("-----BEGIN CERTIFICATE-----")
    assert mtls["controlPlaneCaCertificate"] != mtls["agentCaCertificate"]
    assert postgres["appPassword"] != postgres["runnerPassword"]
    assert "serviceAuth" not in security
    assert "diagnosisGrantSecret" not in security


@pytest.mark.parametrize(
    "relative_path",
    [
        "deploy/generated-secrets.values.yaml",
        "deploy/helm/aegisops/generated-secrets.values.yaml",
        "deploy/runtime/.env",
        "deploy/backups/zabbix-example/zabbix.dump",
        "deploy/staging/zabbix-example/deployment-manifest.sha256",
    ],
)
def test_generated_security_material_is_ignored_by_git(relative_path: str):
    subprocess.run(
        ["git", "check-ignore", "--quiet", relative_path],
        cwd=ROOT,
        check=True,
    )


def test_offline_image_list_contains_only_three_business_images():
    images = {
        line.strip()
        for line in (ROOT / "deploy/offline/images.txt")
        .read_text(encoding="utf-8")
        .splitlines()
        if line.strip()
    }

    assert images == {
        "aegisops/aegisops-app:0.1.0",
        "aegisops/aiops-agent:0.1.0",
        "aegisops/aiops-runner:0.1.0",
    }


def test_offline_scripts_package_and_retag_only_current_topology():
    package = (ROOT / "scripts/deploy/package-offline.sh").read_text(
        encoding="utf-8"
    )
    loader = (ROOT / "scripts/deploy/load-offline-images.sh").read_text(
        encoding="utf-8"
    )

    for image in ("aegisops-app", "aiops-agent", "aiops-runner"):
        assert image in package
        assert image in loader
    assert "aiops-worker" not in package
    assert "aiops-worker" not in loader
    assert "001-create-roles.sql" in package
    assert "002-grant-runner.sql" in package


def test_build_images_builds_app_agent_and_runner_without_worker():
    script = (ROOT / "scripts/deploy/build-images.sh").read_text(encoding="utf-8")

    assert "apps/aiops-server/Dockerfile" in script
    assert 'aegisops-app:${VERSION}' in script
    assert "apps/aiops-agent/Dockerfile" in script
    assert "apps/aiops-runner/Dockerfile" in script
    assert "aiops-worker" not in script


def test_build_images_uses_dockerfile_compatible_build_contexts(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
):
    bash = shutil.which("bash")
    if bash is None:
        pytest.skip("bash is required")

    calls_file = tmp_path / "docker-calls"
    fake_bin = tmp_path / "bin"
    fake_bin.mkdir()
    fake_docker = fake_bin / "docker"
    fake_docker.write_text(
        "#!/usr/bin/env bash\n"
        'printf \'%s\\t\' "$@" >> "$DOCKER_CALLS_FILE"\n'
        "printf '\\n' >> \"$DOCKER_CALLS_FILE\"\n",
        encoding="utf-8",
    )
    fake_docker.chmod(0o755)
    monkeypatch.setenv("DOCKER_CALLS_FILE", str(calls_file))
    monkeypatch.setenv("PATH", f"{fake_bin}{os.pathsep}{os.environ['PATH']}")

    subprocess.run(
        [bash, str(ROOT / "scripts/deploy/build-images.sh")],
        cwd=ROOT,
        check=True,
        env=os.environ.copy(),
    )

    calls = [
        line.rstrip("\t").split("\t")
        for line in calls_file.read_text(encoding="utf-8").splitlines()
    ]
    assert calls == [
        [
            "build",
            "-f",
            "apps/aiops-server/Dockerfile",
            "--build-arg",
            "BUILD_VERSION=0.1.0",
            "-t",
            "aegisops/aegisops-app:0.1.0",
            ".",
        ],
        [
            "build",
            "-f",
            "apps/aiops-runner/Dockerfile",
            "-t",
            "aegisops/aiops-runner:0.1.0",
            ".",
        ],
        [
            "build",
            "-f",
            "apps/aiops-agent/Dockerfile",
            "-t",
            "aegisops/aiops-agent:0.1.0",
            "apps/aiops-agent",
        ],
    ]


def test_legacy_oauth_release_entrypoints_are_removed():
    removed = [
        "deploy/docker-compose.app.yml",
        "deploy/docker-compose.idp.yml",
        "deploy/scripts/verify-service-auth-oauth.py",
        "scripts/ci/verify-service-auth-runtime.py",
        "scripts/ci/test-deploy-app.sh",
        "infra/keycloak/aegisops-realm.json",
    ]

    assert all(not (ROOT / path).exists() for path in removed)


def test_local_infra_contains_dependencies_not_internal_auth_or_business_processes():
    compose = yaml.safe_load(
        (ROOT / "infra/docker-compose.yml").read_text(encoding="utf-8")
    )
    services = compose["services"]

    assert "postgres" in services
    assert "keycloak" not in services
    assert "aiops-agent" not in services
    serialized = (ROOT / "infra/docker-compose.yml").read_text(encoding="utf-8").lower()
    assert "oauth" not in serialized
    assert "jwks" not in serialized


def test_zabbix_webhook_signing_secret_has_no_development_default():
    application = (
        ROOT / "apps/aiops-server/src/main/resources/application.yml"
    ).read_text(encoding="utf-8")

    assert "dev-zabbix-webhook-token" not in application


def test_runner_image_contains_required_execution_tools_and_non_root_runtime():
    dockerfile = (ROOT / "apps/aiops-runner/Dockerfile").read_text(encoding="utf-8")

    assert "ansible" in dockerfile
    assert "sshpass" in dockerfile
    assert "tini" in dockerfile
    assert "USER aiops" in dockerfile


def test_release_workflow_uses_new_installer_and_mtls_probe():
    workflow = (ROOT / ".github/workflows/release-verify.yml").read_text(
        encoding="utf-8"
    )
    lowered = workflow.lower()

    assert "deploy/install.sh" in workflow
    assert "verify-internal-mtls.py" in workflow
    assert "docker-compose.core.yml" in workflow
    assert "vars.AIOPS_DEPLOY_MODE || 'diagnostic'" in workflow
    for forbidden in ("aiops-worker", "keycloak", "jwks", "oauth2"):
        assert forbidden not in lowered


def test_deployment_entrypoints_default_to_diagnostic_mode():
    release_script = (ROOT / "deploy/scripts/deploy-app.sh").read_text(
        encoding="utf-8"
    )
    operator_script = (ROOT / "scripts/deploy/deploy-app.sh").read_text(
        encoding="utf-8"
    )

    assert 'DEPLOY_MODE="${AIOPS_DEPLOY_MODE:-diagnostic}"' in release_script
    assert 'DEPLOY_MODE="${AIOPS_DEPLOY_MODE:-diagnostic}"' in operator_script


def test_deployment_entrypoints_use_the_shared_zabbix_api_network_guard():
    helper_path = "deploy/scripts/ensure-zabbix-api-network.sh"
    helper = (ROOT / helper_path).read_text(encoding="utf-8")

    assert 'NETWORK_NAME="aegisops-zabbix-api"' in helper
    assert "docker network create --driver bridge --internal" in helper
    assert "{{.Driver}} {{.Internal}}" in helper

    for relative_path in (
        "deploy/install.sh",
        "deploy/scripts/deploy-app.sh",
        "deploy/scripts/deploy-zabbix.sh",
    ):
        script = (ROOT / relative_path).read_text(encoding="utf-8")

        assert "ensure-zabbix-api-network.sh" in script
        assert "bash" in script

    release_script = (ROOT / "deploy/scripts/deploy-app.sh").read_text(
        encoding="utf-8"
    )
    assert release_script.index('bash "$ZABBIX_NETWORK_SCRIPT"') < (
        release_script.index('bash "$LEGACY_MIGRATION_SCRIPT"')
    )


def test_deploy_app_rejects_unsafe_network_before_legacy_migration(
    tmp_path: Path,
):
    bash = shutil.which("bash")
    python = shutil.which("python3")
    if bash is None or python is None:
        pytest.skip("bash and python3 are required")

    fake_bin = tmp_path / "bin"
    fake_bin.mkdir()
    fake_docker = fake_bin / "docker"
    fake_docker.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
    fake_docker.chmod(0o755)

    compose_file = tmp_path / "compose.yml"
    install_script = tmp_path / "install.sh"
    mtls_probe = tmp_path / "mtls.py"
    network_guard = tmp_path / "network-guard.sh"
    legacy_migration = tmp_path / "legacy-migration.sh"
    migration_marker = tmp_path / "legacy-migration-ran"
    for required_file in (compose_file, install_script, mtls_probe):
        required_file.touch()
    network_guard.write_text(
        "#!/usr/bin/env bash\nexit 42\n",
        encoding="utf-8",
    )
    legacy_migration.write_text(
        '#!/usr/bin/env bash\nprintf "ran\\n" > "$LEGACY_MIGRATION_MARKER"\n',
        encoding="utf-8",
    )

    result = subprocess.run(
        [bash, str(ROOT / "deploy/scripts/deploy-app.sh")],
        cwd=ROOT,
        capture_output=True,
        text=True,
        env={
            **os.environ,
            "PATH": f"{fake_bin}{os.pathsep}{os.environ['PATH']}",
            "AIOPS_APP_IMAGE": f"example/aegisops@sha256:{'a' * 64}",
            "AIOPS_AGENT_IMAGE": f"example/aegisops@sha256:{'b' * 64}",
            "AIOPS_RUNNER_IMAGE": f"example/aegisops@sha256:{'c' * 64}",
            "COMPOSE_FILE": str(compose_file),
            "INSTALL_SCRIPT": str(install_script),
            "MTLS_PROBE_SCRIPT": str(mtls_probe),
            "ZABBIX_NETWORK_SCRIPT": str(network_guard),
            "LEGACY_MIGRATION_SCRIPT": str(legacy_migration),
            "LEGACY_MIGRATION_MARKER": str(migration_marker),
            "AIOPS_RUNTIME_DIR": str(tmp_path / "runtime"),
        },
        check=False,
    )

    assert result.returncode == 42
    assert not migration_marker.exists()


def test_deploy_app_rejects_mutable_release_images_before_docker(tmp_path: Path):
    bash = shutil.which("bash")
    if bash is None:
        pytest.skip("bash is required")

    fake_bin = tmp_path / "bin"
    fake_bin.mkdir()
    docker_marker = tmp_path / "docker-called"
    fake_docker = fake_bin / "docker"
    fake_docker.write_text(
        '#!/usr/bin/env bash\nprintf "called\\n" > "$DOCKER_MARKER"\n',
        encoding="utf-8",
    )
    fake_docker.chmod(0o755)

    result = subprocess.run(
        [bash, str(ROOT / "deploy/scripts/deploy-app.sh")],
        cwd=ROOT,
        capture_output=True,
        text=True,
        env={
            **os.environ,
            "PATH": f"{fake_bin}{os.pathsep}{os.environ['PATH']}",
            "AIOPS_APP_IMAGE": "example/aegisops:mutable-app",
            "AIOPS_AGENT_IMAGE": "example/aegisops:mutable-agent",
            "AIOPS_RUNNER_IMAGE": "example/aegisops:mutable-runner",
            "DOCKER_MARKER": str(docker_marker),
        },
        check=False,
    )

    assert result.returncode != 0
    assert "digest-pinned" in result.stderr
    assert not docker_marker.exists()


def test_release_runtime_smoke_prepares_zabbix_api_network_before_compose_up():
    workflow = (ROOT / ".github/workflows/release-verify.yml").read_text(
        encoding="utf-8"
    )

    install_index = workflow.index("bash deploy/install.sh")
    network_index = workflow.index("bash deploy/scripts/ensure-zabbix-api-network.sh")
    compose_up_index = workflow.index('"${compose[@]}" up')

    assert install_index < network_index < compose_up_index
    assert "deploy/scripts/ensure-zabbix-api-network.sh" in workflow.split(
        'source: "', 1
    )[1].split('"', 1)[0]


def test_zabbix_component_workflow_packages_the_network_guard():
    helper_path = "deploy/scripts/ensure-zabbix-api-network.sh"
    workflow = (ROOT / ".github/workflows/deploy-component.yml").read_text(
        encoding="utf-8"
    )
    preflight = (ROOT / "scripts/ci/release-preflight.sh").read_text(
        encoding="utf-8"
    )

    source_line = next(
        line for line in workflow.splitlines() if line.strip().startswith("source:")
    )
    assert helper_path in source_line
    assert helper_path in preflight


def test_production_deploy_workflows_share_lock_and_verify_zabbix_membership():
    bash = shutil.which("bash")
    if bash is None:
        pytest.skip("bash is required")

    component_workflow = yaml.safe_load(
        (ROOT / ".github/workflows/deploy-component.yml").read_text(
            encoding="utf-8"
        )
    )
    release_workflow = yaml.safe_load(
        (ROOT / ".github/workflows/release-verify.yml").read_text(
            encoding="utf-8"
        )
    )

    assert component_workflow["concurrency"] == {
        "group": "aegisops-prod-deploy",
        "cancel-in-progress": False,
    }
    assert release_workflow["jobs"]["deploy"]["concurrency"] == {
        "group": "aegisops-prod-deploy",
        "cancel-in-progress": False,
    }

    deploy_script = component_workflow["jobs"]["deploy"]["steps"][-1]["with"][
        "script"
    ]
    subprocess.run(
        [bash, "-n", "-c", deploy_script],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    network_guard_index = deploy_script.index('bash "$NETWORK_SCRIPT"')
    pre_deploy_membership_index = deploy_script.index(
        '[ "$current_members" != "aegisops-app" ]'
    )
    mirror_index = deploy_script.index('bash "$MIRROR_SCRIPT"')
    deploy_index = deploy_script.index('bash "$DEPLOY_SCRIPT"')
    post_deploy_membership_index = deploy_script.index(
        '[ "$actual_members" != "$expected_members" ]'
    )
    assert (
        network_guard_index
        < pre_deploy_membership_index
        < mirror_index
        < deploy_index
        < post_deploy_membership_index
    )


def test_manual_compose_instructions_prepare_external_network_first():
    readme = (ROOT / "deploy/README.md").read_text(encoding="utf-8")

    manual_section = readme.split("## Compose", 1)[1].split("## Helm", 1)[0]
    assert manual_section.index(
        "bash deploy/scripts/ensure-zabbix-api-network.sh"
    ) < manual_section.index("docker compose")


@pytest.mark.parametrize(
    ("existing_config", "create_mode", "expected_returncode"),
    [
        (None, "success", 0),
        (None, "raced", 0),
        (None, "failed", 1),
        ("bridge true", "success", 0),
        ("bridge false", "success", 1),
        ("overlay true", "success", 1),
    ],
)
def test_zabbix_api_network_guard_is_idempotent_and_fails_closed(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    existing_config: str | None,
    create_mode: str,
    expected_returncode: int,
):
    bash = shutil.which("bash")
    if bash is None:
        pytest.skip("bash is required")

    calls_file = tmp_path / "docker-calls"
    network_state = tmp_path / "network-state"
    if existing_config is not None:
        network_state.write_text(f"{existing_config}\n", encoding="utf-8")

    fake_bin = tmp_path / "bin"
    fake_bin.mkdir()
    fake_docker = fake_bin / "docker"
    fake_docker.write_text(
        "#!/usr/bin/env bash\n"
        "set -Eeuo pipefail\n"
        'printf \'%s\\t\' "$@" >> "$DOCKER_CALLS_FILE"\n'
        "printf '\\n' >> \"$DOCKER_CALLS_FILE\"\n"
        'if [[ "$*" == *".Containers"* ]]; then\n'
        "  exit 0\n"
        'elif [ "$1 ${2:-}" = "network inspect" ]; then\n'
        '  [ -f "$MOCK_NETWORK_STATE" ] || exit 1\n'
        '  cat "$MOCK_NETWORK_STATE"\n'
        'elif [ "$1 ${2:-}" = "network create" ]; then\n'
        '  case "$MOCK_CREATE_MODE" in\n'
        "    success) printf 'bridge true\\n' > \"$MOCK_NETWORK_STATE\" ;;\n"
        "    raced) printf 'bridge true\\n' > \"$MOCK_NETWORK_STATE\"; exit 1 ;;\n"
        "    failed) exit 1 ;;\n"
        "  esac\n"
        "fi\n",
        encoding="utf-8",
    )
    fake_docker.chmod(0o755)
    monkeypatch.setenv("DOCKER_CALLS_FILE", str(calls_file))
    monkeypatch.setenv("MOCK_NETWORK_STATE", str(network_state))
    monkeypatch.setenv("MOCK_CREATE_MODE", create_mode)
    monkeypatch.setenv("PATH", f"{fake_bin}{os.pathsep}{os.environ['PATH']}")

    result = subprocess.run(
        [bash, str(ROOT / "deploy/scripts/ensure-zabbix-api-network.sh")],
        cwd=ROOT,
        capture_output=True,
        text=True,
        env=os.environ.copy(),
        check=False,
    )

    assert result.returncode == expected_returncode
    calls = calls_file.read_text(encoding="utf-8")
    if existing_config is None:
        assert (
            "network\tcreate\t--driver\tbridge\t--internal\t"
            "aegisops-zabbix-api\t"
        ) in calls
    else:
        assert "network\tcreate\t" not in calls
    if existing_config in {"bridge false", "overlay true"}:
        assert "must be an internal bridge network" in result.stderr
    if create_mode == "failed":
        assert "Unable to create Docker network" in result.stderr


@pytest.mark.parametrize(
    ("members", "expected_returncode"),
    [
        ("", 0),
        ("aegisops-app\naegisops-zabbix-web\n", 0),
        ("aegisops-app\nunexpected-member\n", 1),
    ],
)
def test_zabbix_api_network_guard_rejects_unexpected_members(
    tmp_path: Path,
    members: str,
    expected_returncode: int,
):
    bash = shutil.which("bash")
    if bash is None:
        pytest.skip("bash is required")

    members_file = tmp_path / "members"
    members_file.write_text(members, encoding="utf-8")
    fake_bin = tmp_path / "bin"
    fake_bin.mkdir()
    fake_docker = fake_bin / "docker"
    fake_docker.write_text(
        "#!/usr/bin/env bash\n"
        "set -Eeuo pipefail\n"
        'if [[ "$*" == *".Containers"* ]]; then\n'
        '  cat "$MOCK_NETWORK_MEMBERS"\n'
        "elif [ \"$1 ${2:-}\" = \"network inspect\" ]; then\n"
        "  printf 'bridge true\\n'\n"
        "else\n"
        "  exit 1\n"
        "fi\n",
        encoding="utf-8",
    )
    fake_docker.chmod(0o755)

    result = subprocess.run(
        [bash, str(ROOT / "deploy/scripts/ensure-zabbix-api-network.sh")],
        cwd=ROOT,
        capture_output=True,
        text=True,
        env={
            **os.environ,
            "PATH": f"{fake_bin}{os.pathsep}{os.environ['PATH']}",
            "MOCK_NETWORK_MEMBERS": str(members_file),
        },
        check=False,
    )

    assert result.returncode == expected_returncode
    if expected_returncode != 0:
        assert "Unexpected aegisops-zabbix-api member" in result.stderr


def test_legacy_database_migration_transfers_ownership_to_app_role():
    sql = (ROOT / "deploy/init/003-migrate-legacy-owner.sql").read_text(
        encoding="utf-8"
    ).lower()

    assert "pg_namespace" in sql
    assert "pg_class" in sql
    assert "pg_proc" in sql
    assert "pg_type" in sql
    assert "reassign owned by" not in sql
    assert "alter database" in sql
    assert "owner to aegisops_app" in sql
