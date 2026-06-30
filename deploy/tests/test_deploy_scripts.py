from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def test_deploy_scripts_exist_and_are_shell_safe():
    scripts = [
        "scripts/deploy/build-images.sh",
        "scripts/deploy/generate-secrets.sh",
        "scripts/deploy/package-offline.sh",
        "scripts/deploy/load-offline-images.sh",
        "scripts/deploy/render-helm.sh",
        "scripts/deploy/verify-offline-package.sh",
    ]

    for script in scripts:
        path = ROOT / script
        assert path.exists(), f"missing {script}"
        text = path.read_text(encoding="utf-8")
        assert "set -euo pipefail" in text


def test_offline_image_list_contains_required_images():
    image_list = ROOT / "deploy/offline/images.txt"
    assert image_list.exists()

    text = image_list.read_text(encoding="utf-8")
    assert "aegisops/aiops-server:0.1.0" in text
    assert "aegisops/aiops-worker:0.1.0" in text
    assert "aegisops/aiops-runner:0.1.0" in text
    assert "aegisops/aiops-agent:0.1.0" in text


def test_java_dockerfile_declares_app_module_in_build_stage():
    dockerfile = ROOT / "deploy/docker/java-app.Dockerfile"
    text = dockerfile.read_text(encoding="utf-8")

    assert "ARG APP_MODULE=apps/aiops-server" in text
    assert "FROM ${MAVEN_IMAGE} AS build" in text
    assert "ARG APP_MODULE" in text
    assert 'mvn -pl "${APP_MODULE}"' in text


def test_build_images_passes_app_ports():
    script = ROOT / "scripts/deploy/build-images.sh"
    text = script.read_text(encoding="utf-8")

    assert "--build-arg APP_PORT=8080" in text
    assert "--build-arg APP_PORT=8081" in text
    assert "--build-arg APP_PORT=8092" in text
