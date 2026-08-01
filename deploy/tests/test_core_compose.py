from __future__ import annotations

import shutil
import subprocess
from pathlib import Path

import pytest
import yaml


ROOT = Path(__file__).resolve().parents[2]
COMPOSE_FILE = ROOT / "deploy/docker-compose.core.yml"


def compose() -> dict[str, object]:
    return yaml.safe_load(COMPOSE_FILE.read_text(encoding="utf-8"))


def test_default_core_contains_exactly_three_unprofiled_services():
    services = compose()["services"]
    unprofiled = {name for name, service in services.items() if "profiles" not in service}

    assert unprofiled == {"postgres", "aiops-server", "aiops-worker"}
    assert set(services["aiops-server"]["depends_on"]) == {"postgres"}
    assert set(services["aiops-worker"]["depends_on"]) == {"postgres"}


def test_core_feature_switches_are_disabled_by_default():
    services = compose()["services"]

    for component in ("aiops-server", "aiops-worker"):
        environment = services[component]["environment"]
        assert environment["AIOPS_AGENT_ENABLED"] == "${AIOPS_AGENT_ENABLED:-false}"
        assert environment["AIOPS_OBJECT_STORAGE_ENABLED"] == (
            "${AIOPS_OBJECT_STORAGE_ENABLED:-false}"
        )
        assert environment["AIOPS_EVIDENCE_VICTORIA_ENABLED"] == (
            "${AIOPS_EVIDENCE_VICTORIA_ENABLED:-false}"
        )
        assert environment["AIOPS_QUOTA_BACKEND"] == "${AIOPS_QUOTA_BACKEND:-memory}"

    assert services["aiops-server"]["environment"][
        "AIOPS_INTERNAL_AGENT_API_ENABLED"
    ] == "${AIOPS_INTERNAL_AGENT_API_ENABLED:-false}"
    assert services["aiops-worker"]["environment"][
        "AIOPS_INTERNAL_AGENT_API_ENABLED"
    ] == "false"


def test_optional_services_are_assigned_to_explicit_profiles():
    services = compose()["services"]

    assert set(services["aiops-agent"]["profiles"]) == {"ai", "demo"}
    assert set(services["keycloak"]["profiles"]) == {"ai", "demo"}
    assert set(services["aiops-runner"]["profiles"]) == {"automation", "demo"}
    assert set(services["redis"]["profiles"]) == {"distributed-cache", "demo"}
    assert set(services["minio"]["profiles"]) == {"object-storage", "demo"}
    assert set(services["minio-init"]["profiles"]) == {"object-storage", "demo"}
    assert set(services["victoriametrics"]["profiles"]) == {"observability", "demo"}
    for component in (
        "zabbix-postgres",
        "zabbix-server",
        "zabbix-web",
        "zabbix-agent2",
    ):
        assert set(services[component]["profiles"]) == {"demo-zabbix", "demo"}


def test_docker_compose_resolves_only_core_services_without_profiles():
    if shutil.which("docker") is None:
        pytest.skip("docker compose is required")

    result = subprocess.run(
        ["docker", "compose", "-f", str(COMPOSE_FILE), "config", "--services"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )

    assert set(result.stdout.splitlines()) == {"postgres", "aiops-server", "aiops-worker"}


def test_full_release_compose_explicitly_enables_existing_ai_topology():
    full_compose = yaml.safe_load(
        (ROOT / "deploy/docker-compose.app.yml").read_text(encoding="utf-8")
    )

    server_environment = full_compose["services"]["aiops-server"]["environment"]
    worker_environment = full_compose["services"]["aiops-worker"]["environment"]
    assert server_environment["AIOPS_AGENT_ENABLED"] == "true"
    assert server_environment["AIOPS_INTERNAL_AGENT_API_ENABLED"] == "true"
    assert server_environment["AIOPS_OBJECT_STORAGE_ENABLED"] == "true"
    assert server_environment["AIOPS_REDIS_HEALTH_ENABLED"] == "true"
    assert worker_environment["AIOPS_AGENT_ENABLED"] == "true"
    assert worker_environment["AIOPS_INTERNAL_AGENT_API_ENABLED"] == "false"
    assert worker_environment["AIOPS_OBJECT_STORAGE_ENABLED"] == "true"
    assert worker_environment["AIOPS_REDIS_HEALTH_ENABLED"] == "true"
