from __future__ import annotations

from pathlib import Path

import subprocess

import yaml


ROOT = Path(__file__).resolve().parents[1]
HELM_SECURITY_ARGS = [
    "--set-string",
    "security.serviceAuth.issuerUri=https://idp.example.com/realms/aegisops",
    "--set-string",
    "security.serviceAuth.jwkSetUri=https://idp.example.com/realms/aegisops/certs",
    "--set-string",
    "security.serviceAuth.tokenUri=https://idp.example.com/realms/aegisops/token",
    "--set-string",
    "security.diagnosisGrantSecret=test-diagnosis-grant-secret",
    "--set-string",
    "security.serviceAuth.clients.server.clientSecret=test-server-client-secret",
    "--set-string",
    "security.serviceAuth.clients.worker.clientSecret=test-worker-client-secret",
    "--set-string",
    "security.serviceAuth.clients.agent.clientSecret=test-agent-client-secret",
]


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

    assert values["security"]["jwtSecret"] == ""
    assert values["security"]["zabbixWebhookSigningSecret"] == ""
    assert values["security"]["diagnosisGrantSecret"] == ""
    assert values["security"]["serviceAuth"]["clients"]["server"]["clientSecret"] == ""
    assert values["security"]["serviceAuth"]["clients"]["worker"]["clientSecret"] == ""
    assert values["security"]["serviceAuth"]["clients"]["agent"]["clientSecret"] == ""
    assert values["dify"]["workRecordApiKey"] == ""
    assert values["dify"]["userHmacSecret"] == ""


def test_oauth2_render_requires_identity_provider_endpoints():
    result = subprocess.run(
        [
            "helm",
            "template",
            "test",
            str(ROOT),
            "--set-string",
            "security.jwtSecret=test-jwt-secret",
            "--set-string",
            "security.zabbixWebhookSigningSecret=test-webhook-secret",
            "--set-string",
            "security.diagnosisGrantSecret=test-diagnosis-grant-secret",
            "--set-string",
            "security.serviceAuth.clients.server.clientSecret=test-server-client-secret",
            "--set-string",
            "security.serviceAuth.clients.worker.clientSecret=test-worker-client-secret",
            "--set-string",
            "security.serviceAuth.clients.agent.clientSecret=test-agent-client-secret",
            "--set-string",
            "external.postgres.password=test-db-password",
        ],
        check=False,
        capture_output=True,
        text=True,
    )

    assert result.returncode != 0
    assert "security.serviceAuth.issuerUri is required in oauth2 mode" in result.stderr


def test_rendered_dify_secrets_are_only_exposed_to_agent():
    result = subprocess.run(
        [
            "helm",
            "template",
            "test",
            str(ROOT),
            *HELM_SECURITY_ARGS,
            "--set-string",
            "security.jwtSecret=test-jwt-secret",
            "--set-string",
            "security.zabbixWebhookSigningSecret=test-zabbix-signing-secret",
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

        if app_name == "server":
            assert "AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN" in effective_env_names
        else:
            assert "AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN" not in effective_env_names

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
    assert values["apps"]["agent"]["port"] == 9008


def test_rendered_agent_contract_uses_registered_port():
    result = subprocess.run(
        [
            "helm",
            "template",
            "test",
            str(ROOT),
            *HELM_SECURITY_ARGS,
            "--set-string",
            "security.jwtSecret=test-jwt-secret",
            "--set-string",
            "security.zabbixWebhookSigningSecret=test-zabbix-signing-secret",
            "--set-string",
            "external.postgres.password=test-db-password",
            "--set",
            "networkPolicy.enabled=true",
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    documents = [document for document in yaml.safe_load_all(result.stdout) if document]
    agent_deployment = next(
        document
        for document in documents
        if document["kind"] == "Deployment"
        and document["metadata"]["labels"]["app.kubernetes.io/component"] == "agent"
    )
    agent_service = next(
        document
        for document in documents
        if document["kind"] == "Service"
        and document["metadata"]["labels"]["app.kubernetes.io/component"] == "agent"
    )
    configmap = next(
        document
        for document in documents
        if document["kind"] == "ConfigMap"
        and document["metadata"]["name"].endswith("-config")
    )
    agent_network_policy = next(
        document
        for document in documents
        if document["kind"] == "NetworkPolicy"
        and document["metadata"]["name"].endswith("-agent-ingress")
    )
    server_network_policy = next(
        document
        for document in documents
        if document["kind"] == "NetworkPolicy"
        and document["metadata"]["name"].endswith("-server-ingress")
    )

    container = agent_deployment["spec"]["template"]["spec"]["containers"][0]
    assert container["ports"][0]["containerPort"] == 9008
    assert agent_service["spec"]["ports"][0]["port"] == 9008
    assert configmap["data"]["AIOPS_AGENT_BASE_URL"].endswith("-agent:9008")
    agent_ingress_ports = agent_network_policy["spec"]["ingress"][0]["ports"]
    server_ingress_ports = server_network_policy["spec"]["ingress"][0]["ports"]
    assert {entry["port"] for entry in agent_ingress_ports} == {9008}
    assert {entry["port"] for entry in server_ingress_ports} == {8080}


def test_configmap_contains_application_env_names():
    configmap = (ROOT / "templates" / "configmap.yaml").read_text(encoding="utf-8")
    secret = (ROOT / "templates" / "secret.yaml").read_text(encoding="utf-8")
    component_auth_secrets = (
        ROOT / "templates" / "component-auth-secrets.yaml"
    ).read_text(encoding="utf-8")
    server_secret = (ROOT / "templates" / "server-secret.yaml").read_text(
        encoding="utf-8"
    )

    assert "AIOPS_DB_URL" in configmap
    assert "AIOPS_DB_USERNAME" in configmap
    assert "AIOPS_AGENT_BASE_URL" in configmap
    assert "AIOPS_EVIDENCE_VICTORIA_BASE_URL" in configmap

    assert "AIOPS_DB_PASSWORD" in secret
    assert "AIOPS_AGENT_INTERNAL_TOKEN" not in secret
    assert "AIOPS_AGENT_OAUTH2_CLIENT_SECRET" in component_auth_secrets
    assert "AIOPS_AGENT_OUTBOUND_OAUTH2_CLIENT_SECRET" in component_auth_secrets
    assert "AIOPS_DIAGNOSIS_GRANT_SECRET" in component_auth_secrets
    assert "AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN" not in secret
    assert "AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN" in server_secret


def test_private_and_offline_values_do_not_ship_a_public_zabbix_signing_secret():
    for name in ("values-private.yaml", "values-offline.yaml"):
        values = load_yaml(name)
        assert values["security"]["zabbixWebhookSigningSecret"] == ""


def test_server_requires_zabbix_webhook_signing_secret():
    result = subprocess.run(
        [
            "helm",
            "template",
            "test",
            str(ROOT),
            *HELM_SECURITY_ARGS,
            "--set-string",
            "security.jwtSecret=test-jwt-secret",
            "--set-string",
            "external.postgres.password=test-db-password",
        ],
        check=False,
        capture_output=True,
        text=True,
    )

    assert result.returncode != 0
    assert "security.zabbixWebhookSigningSecret is required" in result.stderr


def test_disabled_server_does_not_require_zabbix_webhook_signing_secret():
    result = subprocess.run(
        [
            "helm",
            "template",
            "test",
            str(ROOT),
            *HELM_SECURITY_ARGS,
            "--set-string",
            "security.jwtSecret=test-jwt-secret",
            "--set-string",
            "external.postgres.password=test-db-password",
            "--set",
            "apps.server.enabled=false",
        ],
        check=False,
        capture_output=True,
        text=True,
    )

    assert result.returncode == 0, result.stderr
    assert "AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN" not in result.stdout
