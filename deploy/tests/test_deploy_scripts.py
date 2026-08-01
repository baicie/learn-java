from __future__ import annotations

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
        "deploy/scripts/migrate-legacy-compose.sh",
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
