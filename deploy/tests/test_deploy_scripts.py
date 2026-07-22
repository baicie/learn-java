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


def test_server_image_splits_spring_boot_dependencies_for_proxy_uploads():
    dockerfile = ROOT / "apps/aiops-server/Dockerfile"
    text = dockerfile.read_text(encoding="utf-8")

    assert "extract --layers --launcher" in text
    assert text.count("COPY --from=build /tmp/dependency-layers/") == 4
    assert "org.springframework.boot.loader.launch.JarLauncher" in text


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


def test_worker_uses_agent_for_ai_generation():
    compose_file = ROOT / "deploy/docker-compose.app.yml"
    compose = yaml.safe_load(compose_file.read_text(encoding="utf-8"))
    worker = compose["services"]["aiops-worker"]

    assert worker["environment"]["AIOPS_AGENT_BASE_URL"] == "http://aiops-agent:9008"
    assert worker["environment"]["AIOPS_AGENT_INTERNAL_TOKEN"] == "${AIOPS_AGENT_INTERNAL_TOKEN:?AIOPS_AGENT_INTERNAL_TOKEN is required}"
    assert worker["depends_on"]["aiops-agent"]["condition"] == "service_healthy"


def test_compose_dify_secrets_are_only_exposed_to_agent():
    for relative_path in ("deploy/docker-compose.app.yml", "infra/docker-compose.yml"):
        compose_file = ROOT / relative_path
        compose = yaml.safe_load(compose_file.read_text(encoding="utf-8"))
        services = compose["services"]

        agent_environment = services["aiops-agent"]["environment"]
        assert "AIOPS_AGENT_DIFY_WORK_RECORD_API_KEY" in agent_environment
        assert "AIOPS_AGENT_DIFY_USER_HMAC_SECRET" in agent_environment

        for service_name in ("aiops-server", "aiops-worker", "aiops-runner"):
            if service_name not in services:
                continue
            environment = services[service_name]["environment"]
            assert "AIOPS_AGENT_DIFY_WORK_RECORD_API_KEY" not in environment
            assert "AIOPS_AGENT_DIFY_USER_HMAC_SECRET" not in environment


def test_vm_compose_bounds_core_service_memory():
    compose_file = ROOT / "deploy/docker-compose.app.yml"
    compose = yaml.safe_load(compose_file.read_text(encoding="utf-8"))
    services = compose["services"]

    expected_limits = {
        "postgres": "${AIOPS_POSTGRES_MEMORY_LIMIT:-384m}",
        "redis": "${AIOPS_REDIS_MEMORY_LIMIT:-128m}",
        "aiops-server": "${AIOPS_SERVER_MEMORY_LIMIT:-640m}",
        "aiops-agent": "${AIOPS_AGENT_MEMORY_LIMIT:-256m}",
        "aiops-worker": "${AIOPS_WORKER_MEMORY_LIMIT:-576m}",
        "aiops-runner": "${AIOPS_RUNNER_MEMORY_LIMIT:-512m}",
    }

    for service_name, expected_limit in expected_limits.items():
        assert services[service_name]["mem_limit"] == expected_limit


def test_vm_compose_uses_per_app_bounded_java_options():
    compose_file = ROOT / "deploy/docker-compose.app.yml"
    compose = yaml.safe_load(compose_file.read_text(encoding="utf-8"))
    services = compose["services"]

    expected_heap_caps = {
        "aiops-server": "-Xmx320m",
        "aiops-worker": "-Xmx256m",
        "aiops-runner": "-Xmx192m",
    }

    for service_name, heap_cap in expected_heap_caps.items():
        java_opts = services[service_name]["environment"]["JAVA_OPTS"]
        assert heap_cap in java_opts
        assert "MaxRAMPercentage" not in java_opts
        assert "MaxMetaspaceSize" in java_opts
        assert "MaxDirectMemorySize" in java_opts


def test_vm_compose_bounds_java_database_pools():
    compose_file = ROOT / "deploy/docker-compose.app.yml"
    compose = yaml.safe_load(compose_file.read_text(encoding="utf-8"))
    services = compose["services"]

    for service_name in ("aiops-server", "aiops-worker", "aiops-runner"):
        environment = services[service_name]["environment"]
        assert environment["SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE"] == 6
        assert environment["SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE"] == 2


def test_optional_zabbix_compose_has_low_memory_defaults():
    compose_file = ROOT / "deploy/docker-compose.zabbix.yml"
    compose = yaml.safe_load(compose_file.read_text(encoding="utf-8"))
    services = compose["services"]

    for service_name in (
        "zabbix-postgres",
        "zabbix-server",
        "zabbix-web",
        "zabbix-agent2",
    ):
        assert "mem_limit" in services[service_name]

    assert services["zabbix-server"]["environment"]["ZBX_CACHESIZE"] == "${ZABBIX_CACHESIZE:-64M}"


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
