from __future__ import annotations

import shutil
import subprocess
from pathlib import Path

import pytest
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
    "security.diagnosisGrantSecret=test-diagnosis-grant-secret-at-least-32-bytes",
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


@pytest.mark.parametrize("profile", ["values-private.yaml", "values-offline.yaml"])
def test_production_profiles_leave_service_auth_secrets_empty(profile):
    values = load_yaml(profile)
    security = values["security"]
    clients = security["serviceAuth"]["clients"]

    assert security["diagnosisGrantSecret"] == ""
    assert clients["server"]["clientSecret"] == ""
    assert clients["worker"]["clientSecret"] == ""
    assert clients["agent"]["clientSecret"] == ""


@pytest.mark.parametrize("profile", ["values-private.yaml", "values-offline.yaml"])
@pytest.mark.parametrize(
    ("missing_path", "expected_error"),
    [
        (
            "security.diagnosisGrantSecret",
            "security.diagnosisGrantSecret is required",
        ),
        (
            "security.serviceAuth.clients.server.clientSecret",
            "security.serviceAuth.clients.server.clientSecret is required",
        ),
        (
            "security.serviceAuth.clients.worker.clientSecret",
            "security.serviceAuth.clients.worker.clientSecret is required",
        ),
        (
            "security.serviceAuth.clients.agent.clientSecret",
            "security.serviceAuth.clients.agent.clientSecret is required",
        ),
    ],
)
def test_production_profiles_fail_closed_when_service_auth_secret_is_missing(
    profile, missing_path, expected_error
):
    required_values = {
        "security.jwtSecret": "test-jwt-secret",
        "security.zabbixWebhookSigningSecret": "test-webhook-secret",
        "security.diagnosisGrantSecret": "test-diagnosis-grant-secret-at-least-32-bytes",
        "security.serviceAuth.clients.server.clientSecret": "test-server-secret",
        "security.serviceAuth.clients.worker.clientSecret": "test-worker-secret",
        "security.serviceAuth.clients.agent.clientSecret": "test-agent-secret",
        "external.postgres.password": "test-postgres-secret",
        "networkPolicy.egress.allowedCidrs[0]": "10.0.0.0/8",
    }
    command = ["helm", "template", "test", str(ROOT), "-f", str(ROOT / profile)]
    for path, value in required_values.items():
        if path != missing_path:
            command.extend(["--set-string", f"{path}={value}"])

    result = subprocess.run(
        command,
        check=False,
        capture_output=True,
        text=True,
    )

    assert result.returncode != 0
    assert expected_error in result.stderr


def test_chart_exposes_only_oauth2_service_authentication():
    values = load_yaml("values.yaml")
    service_auth = values["security"]["serviceAuth"]
    templates = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (ROOT / "templates").glob("*.yaml")
    )

    assert "mode" not in service_auth
    assert "staticJavaToAgentToken" not in service_auth
    assert "staticAgentToJavaToken" not in service_auth
    assert "AIOPS_AGENT_INTERNAL_TOKEN" not in templates
    assert "AIOPS_INTERNAL_AGENT_TOKEN" not in templates
    assert "AIOPS_AGENT_OUTBOUND_STATIC_TOKEN" not in templates
    assert "AIOPS_DIAGNOSIS_GRANT_REQUIRED" not in templates
    assert "AIOPS_AGENT_DIAGNOSIS_GRANT_REQUIRED" not in templates
    assert set(service_auth["clients"]["worker"]["scope"].split()) == {
        "agent:diagnose",
        "agent:work-record",
    }


def test_chart_rejects_shared_oauth_client_secrets():
    shared_secret = "shared-oauth-client-secret"
    result = helm_template_with_required_secrets(
        "--set-string",
        f"security.serviceAuth.clients.server.clientSecret={shared_secret}",
        "--set-string",
        f"security.serviceAuth.clients.worker.clientSecret={shared_secret}",
        "--set-string",
        f"security.serviceAuth.clients.agent.clientSecret={shared_secret}",
    )

    assert result.returncode != 0
    assert "OAuth2 client secrets must be distinct" in result.stderr
    assert shared_secret not in result.stderr


def test_chart_rejects_weak_diagnosis_grant_secret():
    result = helm_template_with_required_secrets(
        "--set-string", f"security.diagnosisGrantSecret={'x' * 31}"
    )

    assert result.returncode != 0
    assert "diagnosisGrantSecret" in result.stderr
    assert "32" in result.stderr


@pytest.mark.parametrize("ttl_seconds", [0, 301])
def test_chart_rejects_diagnosis_grant_ttl_outside_five_minute_boundary(ttl_seconds):
    result = helm_template_with_required_secrets(
        "--set", f"security.diagnosisGrantTtlSeconds={ttl_seconds}"
    )

    assert result.returncode != 0
    assert "diagnosisGrantTtlSeconds" in result.stderr


def test_chart_injects_diagnosis_grant_ttl_only_into_java_signers():
    result = helm_template_with_required_secrets()
    assert result.returncode == 0, result.stderr
    deployments = {
        document["metadata"]["labels"]["app.kubernetes.io/component"]: document
        for document in yaml.safe_load_all(result.stdout)
        if document and document.get("kind") == "Deployment"
    }

    for component in ("server", "worker"):
        env = {
            entry["name"]: entry.get("value")
            for entry in deployments[component]["spec"]["template"]["spec"]["containers"][0][
                "env"
            ]
        }
        assert env["AIOPS_DIAGNOSIS_GRANT_TTL_SECONDS"] == "300"

    agent_env_names = {
        entry["name"]
        for entry in deployments["agent"]["spec"]["template"]["spec"]["containers"][0][
            "env"
        ]
    }
    assert "AIOPS_DIAGNOSIS_GRANT_TTL_SECONDS" not in agent_env_names


def test_production_profiles_enable_fail_closed_ingress_and_egress_network_policy():
    private = load_yaml("values-private.yaml")
    offline = load_yaml("values-offline.yaml")

    assert private["networkPolicy"]["enabled"] is True
    assert offline["networkPolicy"]["enabled"] is True

    result = helm_template_with_required_secrets(
        "--set",
        "networkPolicy.enabled=true",
        "--set-string",
        "networkPolicy.egress.allowedCidrs[0]=10.0.0.0/8",
    )
    assert result.returncode == 0, result.stderr
    policies = {
        document["metadata"]["name"]: document
        for document in yaml.safe_load_all(result.stdout)
        if document and document.get("kind") == "NetworkPolicy"
    }

    default_deny = policies["test-aegisops-default-deny"]
    assert set(default_deny["spec"]["policyTypes"]) == {"Ingress", "Egress"}

    for component in ("server", "worker", "runner", "agent"):
        policy = policies[f"test-aegisops-{component}-egress"]
        assert policy["spec"]["policyTypes"] == ["Egress"]
        for rule in policy["spec"]["egress"]:
            assert rule.get("to"), f"{component} has an unrestricted egress destination"
            assert rule.get("ports"), f"{component} has an unrestricted egress port"

    assert _has_component_egress(policies["test-aegisops-server-egress"], "agent", 9008)
    assert _has_component_egress(policies["test-aegisops-worker-egress"], "agent", 9008)
    assert _has_component_egress(policies["test-aegisops-agent-egress"], "server", 8080)

    agent_ports = {
        port["port"]
        for rule in policies["test-aegisops-agent-egress"]["spec"]["egress"]
        for port in rule["ports"]
    }
    assert {5432, 6379, 8123, 8428, 9000}.isdisjoint(agent_ports)


def _has_component_egress(policy: dict, component: str, port: int) -> bool:
    for rule in policy["spec"]["egress"]:
        ports = {entry["port"] for entry in rule.get("ports", [])}
        if port not in ports:
            continue
        for destination in rule.get("to", []):
            labels = destination.get("podSelector", {}).get("matchLabels", {})
            if labels.get("app.kubernetes.io/component") == component:
                return True
    return False


def helm_template_with_required_secrets(
    *extra_args: str, chart: Path = ROOT
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            "helm",
            "template",
            "test",
            str(chart),
            *HELM_SECURITY_ARGS,
            "--set-string",
            "security.jwtSecret=test-jwt-secret",
            "--set-string",
            "security.zabbixWebhookSigningSecret=test-webhook-secret",
            "--set-string",
            "external.postgres.password=test-db-password",
            *extra_args,
        ],
        check=False,
        capture_output=True,
        text=True,
    )


@pytest.mark.parametrize(
    "service_account_args",
    [
        [
            "--set",
            "serviceAccount.create=false",
            "--set-string",
            "serviceAccount.names.server=aegisops-server",
            "--set-string",
            "serviceAccount.names.worker=aegisops-worker",
            "--set-string",
            "serviceAccount.names.runner=aegisops-runner",
        ],
        [
            "--set",
            "serviceAccount.create=false",
            "--set-string",
            "serviceAccount.names.server=aegisops-server",
            "--set-string",
            "serviceAccount.names.worker=aegisops-worker",
            "--set-string",
            "serviceAccount.names.runner=default",
            "--set-string",
            "serviceAccount.names.agent=aegisops-agent",
        ],
        [
            "--set",
            "serviceAccount.create=false",
            "--set-string",
            "serviceAccount.names.server=shared-service-account",
            "--set-string",
            "serviceAccount.names.worker=shared-service-account",
            "--set-string",
            "serviceAccount.names.runner=aegisops-runner",
            "--set-string",
            "serviceAccount.names.agent=aegisops-agent",
        ],
    ],
)
def test_external_service_accounts_fail_closed_when_missing_default_or_shared(
    service_account_args,
):
    result = helm_template_with_required_secrets(*service_account_args)

    assert result.returncode != 0
    assert "serviceaccount" in result.stderr.lower()


def test_external_service_accounts_render_only_with_four_distinct_names():
    expected = {
        "server": "external-aegisops-server",
        "worker": "external-aegisops-worker",
        "runner": "external-aegisops-runner",
        "agent": "external-aegisops-agent",
    }
    arguments = ["--set", "serviceAccount.create=false"]
    for component, name in expected.items():
        arguments.extend(
            ["--set-string", f"serviceAccount.names.{component}={name}"]
        )

    result = helm_template_with_required_secrets(*arguments)

    assert result.returncode == 0, result.stderr
    documents = [document for document in yaml.safe_load_all(result.stdout) if document]
    deployments = {
        document["metadata"]["labels"]["app.kubernetes.io/component"]: document
        for document in documents
        if document["kind"] == "Deployment"
    }
    assert {
        component: deployment["spec"]["template"]["spec"]["serviceAccountName"]
        for component, deployment in deployments.items()
    } == expected


def test_network_policy_defaults_to_a_specific_ingress_namespace():
    values = load_yaml("values.yaml")

    assert values["networkPolicy"]["ingressNamespaceSelector"] == {
        "matchLabels": {"kubernetes.io/metadata.name": "ingress-nginx"}
    }


def test_network_policy_rejects_an_empty_ingress_namespace_selector(tmp_path):
    chart = tmp_path / "aegisops"
    shutil.copytree(ROOT, chart)
    values_path = chart / "values.yaml"
    values = yaml.safe_load(values_path.read_text(encoding="utf-8"))
    values["networkPolicy"]["ingressNamespaceSelector"] = {}
    values_path.write_text(yaml.safe_dump(values, sort_keys=False), encoding="utf-8")

    result = helm_template_with_required_secrets(
        "--set",
        "networkPolicy.enabled=true",
        "--skip-schema-validation",
        chart=chart,
    )

    assert result.returncode != 0
    assert "ingressNamespaceSelector" in result.stderr


@pytest.mark.parametrize(
    "cidr",
    [
        "0.0.0.0/0",
        "1.2.3.4/0",
        "1.2.3.4/00",
        "::/0",
        "2001:db8::/0",
        "2001:db8::/000",
    ],
)
def test_enabled_network_policy_rejects_world_egress_cidrs(cidr):
    result = helm_template_with_required_secrets(
        "--set",
        "networkPolicy.enabled=true",
        "--set-string",
        f"networkPolicy.egress.allowedCidrs[0]={cidr}",
    )

    assert result.returncode != 0
    assert "networkPolicy.egress.allowedCidrs" in result.stderr
    assert "world-open CIDR" in result.stderr


@pytest.mark.parametrize(
    "cidr",
    [
        "not-a-cidr",
        "10.0.0/8",
        "999.2.3.4/24",
        "10.0.0.0/33",
        "2001:db8::/129",
    ],
)
def test_enabled_network_policy_rejects_malformed_egress_cidrs(cidr):
    result = helm_template_with_required_secrets(
        "--set",
        "networkPolicy.enabled=true",
        "--set-string",
        f"networkPolicy.egress.allowedCidrs[0]={cidr}",
    )

    assert result.returncode != 0
    assert "allowedCidrs" in result.stderr


@pytest.mark.parametrize("profile", ["values-private.yaml", "values-offline.yaml"])
def test_production_profiles_require_explicit_restricted_egress_cidrs(profile):
    result = subprocess.run(
        [
            "helm",
            "template",
            "test",
            str(ROOT),
            "-f",
            str(ROOT / profile),
            *HELM_SECURITY_ARGS,
            "--set-string",
            "security.jwtSecret=test-jwt-secret",
            "--set-string",
            "security.zabbixWebhookSigningSecret=test-webhook-secret",
            "--set-string",
            "external.postgres.password=test-db-password",
        ],
        check=False,
        capture_output=True,
        text=True,
    )

    assert result.returncode != 0
    assert "networkPolicy.egress.allowedCidrs" in result.stderr


def test_istio_agent_policy_allows_only_java_callers_to_probe_auth():
    result = helm_template_with_required_secrets(
        "--set",
        "serviceMesh.istio.enabled=true",
    )
    assert result.returncode == 0, result.stderr
    policies = [
        document
        for document in yaml.safe_load_all(result.stdout)
        if document and document.get("kind") == "AuthorizationPolicy"
    ]
    agent_policy = next(
        policy
        for policy in policies
        if policy["metadata"]["labels"].get("app.kubernetes.io/component") == "agent"
    )
    post_rule = next(
        rule
        for rule in agent_policy["spec"]["rules"]
        if rule.get("to", [{}])[0].get("operation", {}).get("methods") == ["POST"]
    )

    assert "/v1/auth/probe" in post_rule["to"][0]["operation"]["paths"]
    assert set(post_rule["from"][0]["source"]["principals"]) == {
        "cluster.local/ns/default/sa/test-aegisops-server",
        "cluster.local/ns/default/sa/test-aegisops-worker",
    }


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
            "security.diagnosisGrantSecret=test-diagnosis-grant-secret-at-least-32-bytes",
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
    assert "security.serviceAuth.issuerUri is required" in result.stderr


def test_worker_receives_inbound_oauth2_verifier_configuration():
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
            "security.zabbixWebhookSigningSecret=test-webhook-secret",
            "--set-string",
            "external.postgres.password=test-db-password",
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    documents = [document for document in yaml.safe_load_all(result.stdout) if document]
    worker = next(
        document
        for document in documents
        if document["kind"] == "Deployment"
        and document["metadata"]["labels"]["app.kubernetes.io/component"] == "worker"
    )
    environment = {
        entry["name"]: entry.get("value")
        for entry in worker["spec"]["template"]["spec"]["containers"][0]["env"]
    }

    assert environment["AIOPS_INTERNAL_AGENT_JWT_ISSUER_URI"] == (
        "https://idp.example.com/realms/aegisops"
    )
    assert environment["AIOPS_INTERNAL_AGENT_JWT_JWK_SET_URI"] == (
        "https://idp.example.com/realms/aegisops/certs"
    )


def render_component_auth_checksums(**overrides):
    command = [
        "helm",
        "template",
        "test",
        str(ROOT),
        *HELM_SECURITY_ARGS,
        "--set-string",
        "security.jwtSecret=test-jwt-secret",
        "--set-string",
        "security.zabbixWebhookSigningSecret=test-webhook-secret",
        "--set-string",
        "external.postgres.password=test-db-password",
    ]
    for path, value in overrides.items():
        command.extend(["--set-string", f"{path}={value}"])

    result = subprocess.run(
        command,
        check=True,
        capture_output=True,
        text=True,
    )
    documents = [document for document in yaml.safe_load_all(result.stdout) if document]
    return {
        document["metadata"]["labels"]["app.kubernetes.io/component"]: document[
            "spec"
        ]["template"]["metadata"]["annotations"].get("checksum/component-auth-secret")
        for document in documents
        if document["kind"] == "Deployment"
    }


def test_component_auth_secret_rotation_changes_only_affected_pod_checksums():
    baseline = render_component_auth_checksums()
    rotations = {
        "security.serviceAuth.clients.server.clientSecret": {"server"},
        "security.serviceAuth.clients.worker.clientSecret": {"worker"},
        "security.serviceAuth.clients.agent.clientSecret": {"agent"},
        "security.diagnosisGrantSecret": {"server", "worker"},
    }

    assert baseline["runner"] is None
    assert all(baseline[component] for component in ("server", "worker", "agent"))

    for path, affected_components in rotations.items():
        rotated = render_component_auth_checksums(
            **{path: "rotated-secret-with-at-least-32-bytes"}
        )
        for component in ("server", "worker", "agent"):
            if component in affected_components:
                assert rotated[component] != baseline[component]
            else:
                assert rotated[component] == baseline[component]
        assert rotated["runner"] is None


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
            "--set-string",
            "networkPolicy.egress.allowedCidrs[0]=10.0.0.0/8",
            "--set",
            "apps.server.port=18080",
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
    assert configmap["data"]["AIOPS_AGENT_WORKFLOW_API_BASE_URL"].endswith(
        "-server:18080"
    )
    assert configmap["data"]["AIOPS_AGENT_EVIDENCE_BASE_URL"].endswith(
        "-server:18080/internal/agent/evidence"
    )
    config_map_refs = {
        entry["configMapRef"]["name"]
        for entry in container.get("envFrom", [])
        if "configMapRef" in entry
    }
    assert configmap["metadata"]["name"] in config_map_refs
    agent_ingress_ports = agent_network_policy["spec"]["ingress"][0]["ports"]
    agent_ingress_peers = agent_network_policy["spec"]["ingress"][0]["from"]
    server_ingress_ports = server_network_policy["spec"]["ingress"][0]["ports"]
    server_ingress_peers = server_network_policy["spec"]["ingress"][0]["from"]
    assert {entry["port"] for entry in agent_ingress_ports} == {9008}
    assert {entry["port"] for entry in server_ingress_ports} == {18080}
    assert {
        "namespaceSelector": {
            "matchLabels": {"kubernetes.io/metadata.name": "ingress-nginx"}
        }
    } in server_ingress_peers
    expected_release_labels = {
        "app.kubernetes.io/name": "aegisops",
        "app.kubernetes.io/instance": "test",
    }
    assert agent_ingress_peers[0]["podSelector"]["matchLabels"] == (
        expected_release_labels
    )
    agent_to_server_peer = next(
        peer for peer in server_ingress_peers if "podSelector" in peer
    )
    agent_to_server_labels = agent_to_server_peer["podSelector"]["matchLabels"]
    assert expected_release_labels.items() <= agent_to_server_labels.items()
    assert agent_to_server_labels["app.kubernetes.io/component"] == "agent"


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
    assert "AIOPS_AGENT_WORKFLOW_API_BASE_URL" in configmap
    assert "AIOPS_AGENT_EVIDENCE_BASE_URL" in configmap
    assert "AIOPS_AGENT_EVIDENCE_API_BASE_URL" not in configmap
    assert "AIOPS_AGENT_KNOWLEDGE_API_BASE_URL" not in configmap
    assert "AIOPS_AGENT_CHECKPOINT_API_BASE_URL" not in configmap
    assert "AIOPS_AGENT_MEMORY_API_BASE_URL" not in configmap

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
