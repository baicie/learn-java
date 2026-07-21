from __future__ import annotations

from pathlib import Path

import subprocess

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
    assert values["dify"]["workRecordApiKey"] == ""
    assert values["dify"]["userHmacSecret"] == ""


def test_rendered_dify_secrets_are_only_exposed_to_agent():
    result = subprocess.run(
        [
            "helm",
            "template",
            "test",
            str(ROOT),
            "--set-string",
            "security.internalAgentToken=test-internal-token",
            "--set-string",
            "security.jwtSecret=test-jwt-secret",
            "--set-string",
            "external.postgres.password=test-db-password",
            "--set-string",
            "dify.workRecordApiKey=test-dify-key",
            "--set-string",
            "dify.userHmacSecret=test-hmac-secret",
            "--set-string",
            "dify.workRecordProvider=dify",
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    documents = [document for document in yaml.safe_load_all(result.stdout) if document]
    deployments = {
        document["metadata"]["name"].rsplit("-", 1)[-1]: document
        for document in documents
        if document["kind"] == "Deployment"
    }
    secret_keys = {
        document["metadata"]["name"]: set(document.get("stringData", {}))
        for document in documents
        if document["kind"] == "Secret"
    }

    for app_name, deployment in deployments.items():
        container = deployment["spec"]["template"]["spec"]["containers"][0]
        env_names = {entry["name"] for entry in container.get("env", [])}
        secret_refs = {
            entry["secretRef"]["name"]
            for entry in container.get("envFrom", [])
            if "secretRef" in entry
        }
        effective_env_names = env_names | {
            key for name in secret_refs for key in secret_keys[name]
        }

        if app_name == "agent":
            assert "AIOPS_AGENT_DIFY_WORK_RECORD_API_KEY" in effective_env_names
            assert "AIOPS_AGENT_DIFY_USER_HMAC_SECRET" in effective_env_names
        else:
            assert "AIOPS_AGENT_DIFY_WORK_RECORD_API_KEY" not in effective_env_names
            assert "AIOPS_AGENT_DIFY_USER_HMAC_SECRET" not in effective_env_names

        assert all("agent-dify" not in name for name in secret_refs)


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
