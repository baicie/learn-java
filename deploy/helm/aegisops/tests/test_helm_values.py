from __future__ import annotations

import shutil
import subprocess
from pathlib import Path

import pytest
import yaml


ROOT = Path(__file__).resolve().parents[1]
EXISTING_SECRET_ARGS = [
    "--set-string",
    "security.existingSecrets.app=test-app-security",
    "--set-string",
    "security.existingSecrets.agent=test-agent-security",
    "--set-string",
    "security.existingSecrets.runner=test-runner-security",
]


def load_yaml(name: str) -> dict:
    return yaml.safe_load((ROOT / name).read_text(encoding="utf-8"))


def helm_template(*extra_args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["helm", "template", "test", str(ROOT), *EXISTING_SECRET_ARGS, *extra_args],
        check=False,
        capture_output=True,
        text=True,
    )


def rendered_documents(*extra_args: str) -> list[dict]:
    result = helm_template(*extra_args)
    assert result.returncode == 0, result.stderr
    return [document for document in yaml.safe_load_all(result.stdout) if document]


def deployments(documents: list[dict]) -> dict[str, dict]:
    return {
        item["metadata"]["labels"]["app.kubernetes.io/component"]: item
        for item in documents
        if item["kind"] == "Deployment"
    }


def test_values_define_three_business_processes():
    values = load_yaml("values.yaml")

    assert set(values["apps"]) == {"app", "agent", "runner"}
    assert values["apps"]["app"]["port"] == 8080
    assert values["apps"]["app"]["internalMtlsPort"] == 8443
    assert values["apps"]["agent"]["port"] == 9008
    assert values["apps"]["runner"]["port"] == 8092
    assert values["apps"]["app"]["enabled"] is True
    assert values["apps"]["agent"]["enabled"] is True
    assert values["apps"]["runner"]["enabled"] is False


def test_chart_contains_no_internal_oauth_or_keycloak_configuration():
    chart_text = "\n".join(
        path.read_text(encoding="utf-8")
        for path in ROOT.rglob("*")
        if path.is_file() and "tests" not in path.parts
    ).lower()

    assert "oauth" not in chart_text
    assert "jwks" not in chart_text
    assert "keycloak" not in chart_text


def test_secret_defaults_are_empty_and_existing_secret_names_are_supported():
    values = load_yaml("values.yaml")
    security = values["security"]

    assert security["jwtSecret"] == ""
    assert security["taskGrant"]["privateKey"] == ""
    assert security["taskGrant"]["publicKey"] == ""
    assert security["mtls"]["appCertificate"] == ""
    assert security["mtls"]["appPrivateKey"] == ""
    assert security["mtls"]["agentCertificate"] == ""
    assert security["mtls"]["agentPrivateKey"] == ""
    assert security["existingSecrets"] == {"app": "", "agent": "", "runner": ""}
    assert values["external"]["postgres"]["appPassword"] == ""
    assert values["external"]["postgres"]["runnerPassword"] == ""


def test_default_chart_renders_diagnostic_topology_without_runner():
    rendered = deployments(rendered_documents())

    assert set(rendered) == {"app", "agent"}


def test_automation_chart_adds_runner():
    rendered = deployments(rendered_documents("--set", "apps.runner.enabled=true"))

    assert set(rendered) == {"app", "agent", "runner"}


def test_app_agent_mtls_routes_and_secret_mounts_are_wired():
    rendered = deployments(rendered_documents())
    app = rendered["app"]["spec"]["template"]["spec"]["containers"][0]
    agent = rendered["agent"]["spec"]["template"]["spec"]["containers"][0]
    app_env = {item["name"]: item.get("value") for item in app["env"]}
    agent_env = {item["name"]: item.get("value") for item in agent["env"]}

    assert app_env["AIOPS_AGENT_BASE_URL"] == "https://test-aegisops-agent:9008"
    assert app_env["AIOPS_INTERNAL_MTLS_PORT"] == "8443"
    assert app_env["AIOPS_TLS_CLIENT_CA_FILE"] == "/run/secrets/agent-ca.crt"
    assert agent_env["AIOPS_AGENT_WORKFLOW_API_BASE_URL"] == (
        "https://test-aegisops-app:8443"
    )
    assert agent_env["AIOPS_AGENT_TLS_CLIENT_CA_FILE"] == (
        "/run/secrets/control-plane-ca.crt"
    )
    assert _secret_volume_name(rendered["app"]) == "test-app-security"
    assert _secret_volume_name(rendered["agent"]) == "test-agent-security"
    assert _secret_mount_is_read_only(app)
    assert _secret_mount_is_read_only(agent)

    services = {
        item["metadata"]["labels"]["app.kubernetes.io/component"]: item
        for item in rendered_documents()
        if item["kind"] == "Service"
    }
    app_ports = {item["name"]: item["port"] for item in services["app"]["spec"]["ports"]}
    assert app_ports == {"http": 8080, "internal-mtls": 8443}


def test_runner_uses_separate_database_identity_and_public_key_only():
    rendered = deployments(rendered_documents("--set", "apps.runner.enabled=true"))
    runner = rendered["runner"]["spec"]["template"]["spec"]["containers"][0]
    env = {item["name"]: item for item in runner["env"]}

    assert env["AIOPS_DB_USERNAME"]["value"] == "aegisops_runner"
    assert env["SPRING_FLYWAY_ENABLED"]["value"] == "false"
    assert env["AIOPS_RUNNER_EXECUTION_GRANT_CURRENT_PUBLIC_KEY_FILE"]["value"] == (
        "/run/secrets/grant-public.pem"
    )
    assert "AIOPS_RUNNER_EXECUTION_GRANT_PRIVATE_KEY_FILE" not in env
    assert "AIOPS_EXECUTION_GRANT_PRIVATE_KEY_FILE" not in env
    assert _secret_volume_name(rendered["runner"]) == "test-runner-security"
    assert _secret_mount_is_read_only(runner)


def test_inline_component_secrets_keep_private_key_in_app_only():
    args = [
        "--set-string",
        "security.existingSecrets.app=",
        "--set-string",
        "security.existingSecrets.agent=",
        "--set-string",
        "security.existingSecrets.runner=",
        "--set-string",
        "security.jwtSecret=test-jwt-secret",
        "--set-string",
        "security.zabbixWebhookSigningSecret=test-webhook-secret",
        "--set-string",
        "security.taskGrant.privateKey=test-private-key",
        "--set-string",
        "security.taskGrant.publicKey=test-public-key",
        "--set-string",
        "security.mtls.appCertificate=test-app-certificate",
        "--set-string",
        "security.mtls.appPrivateKey=test-app-key",
        "--set-string",
        "security.mtls.agentCertificate=test-agent-certificate",
        "--set-string",
        "security.mtls.agentPrivateKey=test-agent-key",
        "--set-string",
        "security.mtls.controlPlaneCaCertificate=test-control-plane-ca",
        "--set-string",
        "security.mtls.agentCaCertificate=test-agent-ca",
        "--set-string",
        "external.postgres.appPassword=test-app-db-password",
        "--set-string",
        "external.postgres.runnerPassword=test-runner-db-password",
        "--set",
        "apps.runner.enabled=true",
    ]
    documents = rendered_documents(*args)
    secrets = {
        item["metadata"]["labels"]["app.kubernetes.io/component"]: item["stringData"]
        for item in documents
        if item["kind"] == "Secret"
        and "app.kubernetes.io/component" in item["metadata"]["labels"]
    }

    assert "grant-private.pem" in secrets["app"]
    assert "grant-private.pem" not in secrets["agent"]
    assert "grant-private.pem" not in secrets["runner"]
    assert secrets["agent"]["grant-public.pem"] == "test-public-key"
    assert secrets["runner"]["grant-public.pem"] == "test-public-key"
    assert secrets["app"]["AIOPS_DB_PASSWORD"] == "test-app-db-password"
    assert secrets["runner"]["AIOPS_DB_PASSWORD"] == "test-runner-db-password"


def test_only_app_service_is_a_valid_ingress_target():
    documents = rendered_documents("--set", "ingress.enabled=true")
    ingress = next(item for item in documents if item["kind"] == "Ingress")
    backend = ingress["spec"]["rules"][0]["http"]["paths"][0]["backend"]["service"]

    assert backend["name"] == "test-aegisops-app"
    assert backend["port"]["number"] == 8080


def test_agent_uses_tcp_probes_because_https_requires_client_certificate():
    rendered = deployments(rendered_documents())
    agent = rendered["agent"]["spec"]["template"]["spec"]["containers"][0]

    assert agent["readinessProbe"]["tcpSocket"]["port"] == "https"
    assert agent["livenessProbe"]["tcpSocket"]["port"] == "https"
    assert "httpGet" not in agent["readinessProbe"]


def test_network_policy_allows_only_app_agent_mtls_directions():
    documents = rendered_documents(
        "--set",
        "networkPolicy.enabled=true",
        "--set-string",
        "networkPolicy.egress.allowedCidrs[0]=10.0.0.0/8",
        "--set",
        "apps.runner.enabled=true",
    )
    policies = {
        item["metadata"]["name"]: item
        for item in documents
        if item["kind"] == "NetworkPolicy"
    }

    assert _has_component_egress(policies["test-aegisops-app-egress"], "agent", 9008)
    assert _has_component_egress(policies["test-aegisops-agent-egress"], "app", 8443)
    agent_ports = {
        port["port"]
        for rule in policies["test-aegisops-agent-egress"]["spec"]["egress"]
        for port in rule["ports"]
    }
    assert 5432 not in agent_ports
    assert not _has_component_egress(
        policies["test-aegisops-agent-egress"], "runner", 8092
    )
    assert not _has_component_egress(
        policies["test-aegisops-runner-egress"], "agent", 9008
    )


def test_disabled_runner_does_not_require_external_service_account_name():
    result = helm_template(
        "--set",
        "serviceAccount.create=false",
        "--set-string",
        "serviceAccount.names.app=app",
        "--set-string",
        "serviceAccount.names.agent=agent",
    )

    assert result.returncode == 0, result.stderr


def test_external_service_accounts_must_be_distinct():
    result = helm_template(
        "--set",
        "serviceAccount.create=false",
        "--set-string",
        "serviceAccount.names.app=shared",
        "--set-string",
        "serviceAccount.names.agent=shared",
        "--set-string",
        "serviceAccount.names.runner=runner",
    )

    assert result.returncode != 0
    assert "distinct" in result.stderr


def test_helm_lint_passes_with_external_secrets():
    if shutil.which("helm") is None:
        pytest.skip("helm is required")
    result = subprocess.run(
        ["helm", "lint", str(ROOT), *EXISTING_SECRET_ARGS],
        check=False,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr


def _secret_volume_name(deployment: dict) -> str:
    volumes = deployment["spec"]["template"]["spec"]["volumes"]
    security = next(item for item in volumes if item["name"] == "security-material")
    return security["secret"]["secretName"]


def _secret_mount_is_read_only(container: dict) -> bool:
    mount = next(
        item for item in container["volumeMounts"] if item["name"] == "security-material"
    )
    return mount["readOnly"] is True and mount["mountPath"] == "/run/secrets"


def _has_component_egress(policy: dict, component: str, port: int) -> bool:
    for rule in policy["spec"]["egress"]:
        if port not in {item["port"] for item in rule.get("ports", [])}:
            continue
        for destination in rule.get("to", []):
            labels = destination.get("podSelector", {}).get("matchLabels", {})
            if labels.get("app.kubernetes.io/component") == component:
                return True
    return False
