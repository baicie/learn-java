from __future__ import annotations

from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]


def load_yaml(name: str) -> dict:
    with (ROOT / name).open("r", encoding="utf-8") as file:
        return yaml.safe_load(file)


def test_values_has_required_apps():
    values = load_yaml("values.yaml")

    apps = values["apps"]

    assert "server" in apps
    assert "worker" in apps
    assert "runner" in apps
    assert "agent" in apps

    for name in ["server", "worker", "runner", "agent"]:
        assert apps[name]["enabled"] is True
        assert apps[name]["image"]["repository"]
        assert apps[name]["image"]["tag"]
        assert apps[name]["port"] > 0


def test_security_defaults_do_not_contain_real_secret():
    values = load_yaml("values.yaml")

    assert values["security"]["internalAgentToken"] == ""
    assert values["security"]["jwtSecret"] == ""


def test_external_dependencies_have_hosts():
    values = load_yaml("values.yaml")
    external = values["external"]

    assert external["postgres"]["host"]
    assert external["redis"]["host"]
    assert external["clickhouse"]["host"]
    assert external["minio"]["endpoint"]
    assert external["victoriaMetrics"]["baseUrl"]


def test_values_ports_match_application_defaults():
    values = load_yaml("values.yaml")

    assert values["apps"]["server"]["port"] == 8080
    assert values["apps"]["worker"]["port"] == 8081
    assert values["apps"]["runner"]["port"] == 8092
    assert values["apps"]["agent"]["port"] == 8000


def test_configmap_contains_application_env_names():
    configmap = (ROOT / "templates" / "configmap.yaml").read_text(encoding="utf-8")
    secret = (ROOT / "templates" / "secret.yaml").read_text(encoding="utf-8")

    assert "AIOPS_DB_URL" in configmap
    assert "AIOPS_DB_USERNAME" in configmap
    assert "AIOPS_AGENT_BASE_URL" in configmap
    assert "AIOPS_EVIDENCE_VICTORIA_BASE_URL" in configmap

    assert "AIOPS_DB_PASSWORD" in secret
    assert "AIOPS_AGENT_INTERNAL_TOKEN" in secret
