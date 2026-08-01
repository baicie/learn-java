from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def test_image_build_and_manual_publish_use_three_business_processes():
    build_script = (ROOT / "scripts/deploy/build-images.sh").read_text(encoding="utf-8")
    workflow = (ROOT / ".github/workflows/manual-docker-build.yml").read_text(
        encoding="utf-8"
    )

    assert '"${REGISTRY}/aegisops-app:${VERSION}"' in build_script
    assert '"${REGISTRY}/aiops-agent:${VERSION}"' in build_script
    assert '"${REGISTRY}/aiops-runner:${VERSION}"' in build_script
    assert "APP_MODULE=apps/aiops-worker" not in build_script
    assert "for component in app agent runner" in workflow
    assert "for component in server agent worker runner" not in workflow


def test_release_workflow_uses_mtls_compose_without_internal_oauth_or_worker():
    workflow = (ROOT / ".github/workflows/release-verify.yml").read_text(
        encoding="utf-8"
    )
    lowered = workflow.lower()

    assert "deploy/docker-compose.core.yml" in workflow
    assert "deploy/install.sh" in workflow
    assert "verify-internal-mtls.py" in workflow
    assert "docker-compose.app.yml" not in workflow
    assert "docker-compose.idp.yml" not in workflow
    assert "aiops-worker" not in workflow
    assert "oauth" not in lowered
    assert "jwks" not in lowered
    assert "keycloak" not in lowered


def test_remote_deployer_reuses_generated_mtls_runtime():
    script = (ROOT / "deploy/scripts/deploy-app.sh").read_text(encoding="utf-8")
    lowered = script.lower()

    assert "docker-compose.core.yml" in script
    assert "deploy/install.sh" in script
    assert "verify-internal-mtls.py" in script
    assert "AIOPS_APP_IMAGE" in script
    assert "AIOPS_AGENT_IMAGE" in script
    assert "AIOPS_RUNNER_IMAGE" in script
    assert "AIOPS_WORKER_IMAGE" not in script
    assert "oauth" not in lowered
    assert "jwks" not in lowered
    assert "keycloak" not in lowered
