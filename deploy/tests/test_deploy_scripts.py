from __future__ import annotations

from pathlib import Path

import yaml


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
    # server 用专用 Dockerfile（含 portal-build 阶段）
    # runner  用专用 Dockerfile（要装 ansible / sshpass / tini）
    # worker  走 deploy/docker/java-app.Dockerfile + ARG 通用模板
    script = ROOT / "scripts/deploy/build-images.sh"
    text = script.read_text(encoding="utf-8")

    assert "apps/aiops-server/Dockerfile" in text
    assert "apps/aiops-runner/Dockerfile" in text
    assert "--build-arg APP_PORT=8081" in text  # worker 走通用模板，必须显式传 port


def test_worker_uses_compose_redis_service():
    compose_file = ROOT / "deploy/docker-compose.app.yml"
    compose = yaml.safe_load(compose_file.read_text(encoding="utf-8"))
    worker = compose["services"]["aiops-worker"]

    assert worker["environment"]["SPRING_DATA_REDIS_HOST"] == "redis"
    assert worker["environment"]["SPRING_DATA_REDIS_PORT"] == 6379
    assert worker["depends_on"]["redis"]["condition"] == "service_healthy"


def test_package_offline_uses_split_for_volume_packaging():
    script = ROOT / "scripts/deploy/package-offline.sh"
    text = script.read_text(encoding="utf-8")

    assert "split -b" in text, "缺少 split 分卷命令"
    assert "manifest.txt" in text, "缺少 manifest.txt 生成"
    assert "NUM_VOLUMES=" in text, "缺少 NUM_VOLUMES 变量记录"
    assert "merge-volumes.sh" in text, "缺少 merge-volumes.sh 脚本生成"


def test_load_offline_supports_volumes_and_single_tar():
    script = ROOT / "scripts/deploy/load-offline-images.sh"
    text = script.read_text(encoding="utf-8")

    assert "manifest.txt" in text, "load 脚本缺少 manifest 读取逻辑"
    assert "tar.vol" in text, "load 脚本缺少分卷扩展名检测"
    assert "docker load -i" in text, "load 脚本缺少 docker load"
