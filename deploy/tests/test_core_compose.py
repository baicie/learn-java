from __future__ import annotations

import os
import shutil
import subprocess
from pathlib import Path

import pytest
import yaml


ROOT = Path(__file__).resolve().parents[2]
COMPOSE_FILE = ROOT / "deploy/docker-compose.core.yml"


def compose() -> dict[str, object]:
    return yaml.safe_load(COMPOSE_FILE.read_text(encoding="utf-8"))


def test_default_core_is_postgres_and_single_modular_app():
    services = compose()["services"]
    unprofiled = {name for name, service in services.items() if "profiles" not in service}

    assert unprofiled == {"postgres", "aegisops-app"}
    assert set(services["aegisops-app"]["depends_on"]) == {"postgres"}
    assert "aiops-server" not in services
    assert "aiops-worker" not in services


def test_profiles_resolve_core_diagnostic_and_automation_topologies():
    if shutil.which("docker") is None:
        pytest.skip("docker compose is required")

    assert _resolved_services() == {"postgres", "aegisops-app"}
    assert _resolved_services("ai") == {
        "postgres",
        "aegisops-app",
        "aiops-agent",
    }
    assert _resolved_services("ai", "automation") == {
        "postgres",
        "aegisops-app",
        "aiops-agent",
        "runner-db-permissions",
        "aiops-runner",
    }


def test_ai_and_automation_are_explicit_profiles_without_keycloak():
    services = compose()["services"]

    assert set(services["aiops-agent"]["profiles"]) == {"ai"}
    assert set(services["aiops-runner"]["profiles"]) == {"automation"}
    assert "keycloak" not in services

    serialized = COMPOSE_FILE.read_text(encoding="utf-8").lower()
    assert "oauth" not in serialized
    assert "jwks" not in serialized
    assert "keycloak" not in serialized


def test_only_app_public_http_port_is_published():
    services = compose()["services"]

    assert services["aegisops-app"]["ports"] == ["${AIOPS_APP_PORT:-8080}:8080"]
    assert "ports" not in services["postgres"]
    assert "ports" not in services["aiops-agent"]
    assert "ports" not in services["aiops-runner"]
    assert services["aegisops-app"]["expose"] == [8443]
    assert services["aiops-agent"]["expose"] == [9008]


def test_app_and_agent_use_role_separated_mtls_and_task_grant_secrets():
    model = compose()
    services = model["services"]

    app_secrets = _secret_targets(services["aegisops-app"])
    agent_secrets = _secret_targets(services["aiops-agent"])
    runner_secrets = _secret_targets(services["aiops-runner"])

    assert {
        "app_tls_cert",
        "app_tls_key",
        "agent_ca_cert",
        "task_grant_private_key",
        "task_grant_public_key",
        "app_db_password",
    }.issubset(app_secrets)
    assert {
        "agent_tls_cert",
        "agent_tls_key",
        "control_plane_ca_cert",
        "task_grant_public_key",
    }.issubset(agent_secrets)
    assert {"runner_db_password", "task_grant_public_key"}.issubset(runner_secrets)
    assert "task_grant_private_key" not in agent_secrets
    assert "task_grant_private_key" not in runner_secrets

    app_env = services["aegisops-app"]["environment"]
    agent_env = services["aiops-agent"]["environment"]
    assert app_env["AIOPS_AGENT_BASE_URL"] == "https://aiops-agent:9008"
    assert app_env["AIOPS_INTERNAL_MTLS_PORT"] == 8443
    assert agent_env["AIOPS_AGENT_WORKFLOW_API_BASE_URL"] == "https://aegisops-app:8443"
    assert agent_env["AIOPS_AGENT_TLS_CLIENT_CA_FILE"] == (
        "/run/secrets/control_plane_ca_cert"
    )
    assert app_env["AIOPS_DIAGNOSIS_GRANT_PRIVATE_KEY_FILE"] == (
        "/run/secrets/task_grant_private_key"
    )
    assert app_env["AIOPS_DIAGNOSIS_GRANT_PUBLIC_KEY_FILE"] == (
        "/run/secrets/task_grant_public_key"
    )
    assert "AIOPS_SECURITY_DIAGNOSIS_GRANT_PUBLIC_KEY_FILE" not in app_env


def test_agent_and_runner_have_separate_egress_and_no_shared_network():
    model = compose()
    services = model["services"]
    networks = model["networks"]

    app_networks = set(services["aegisops-app"]["networks"])
    agent_networks = set(services["aiops-agent"]["networks"])
    runner_networks = set(services["aiops-runner"]["networks"])
    postgres_networks = set(services["postgres"]["networks"])

    assert app_networks == {"frontend", "app-db", "app-agent"}
    assert agent_networks == {"app-agent", "agent-egress"}
    assert runner_networks == {"runner-db", "runner-egress"}
    assert postgres_networks == {"app-db", "runner-db"}
    assert agent_networks.isdisjoint(runner_networks)
    assert app_networks.isdisjoint(runner_networks)
    assert networks["app-agent"]["internal"] is True
    assert networks["app-db"]["internal"] is True
    assert networks["runner-db"]["internal"] is True
    assert networks["agent-egress"].get("internal", False) is False
    assert networks["runner-egress"].get("internal", False) is False


def test_runner_uses_restricted_database_identity_and_no_http_dependency():
    services = compose()["services"]
    runner = services["aiops-runner"]

    assert runner["environment"]["AIOPS_DB_USERNAME"] == "aegisops_runner"
    assert runner["environment"]["SPRING_FLYWAY_ENABLED"] == "false"
    assert runner["read_only"] is True
    assert set(runner["depends_on"]) == {"postgres", "runner-db-permissions"}
    assert "aegisops-app" not in runner["depends_on"]
    assert "aiops-agent" not in runner["depends_on"]


def test_runner_permissions_match_execution_code_access_surface():
    sql = (ROOT / "deploy/init/002-grant-runner.sql").read_text(encoding="utf-8").lower()
    role_sql = " ".join(
        (ROOT / "deploy/init/001-create-roles.sql")
        .read_text(encoding="utf-8")
        .lower()
        .split()
    )
    normalized = " ".join(sql.split())

    assert "revoke all on all tables in schema public from aegisops_runner" in sql
    assert "grant select on table execution_run, execution_step" in sql
    assert "grant update (status, runner_id, started_at" in normalized
    assert "on table execution_run to aegisops_runner" in normalized
    assert "grant update (status, started_at, finished_at" in normalized
    assert "on table execution_step to aegisops_runner" in normalized
    assert "grant insert on table execution_artifact, execution_audit_event" in sql
    assert "grant select (tenant_id, id) on table automation_plan" in normalized
    assert "grant update (status, updated_at) on table automation_plan" in normalized
    assert "grant select (tenant_id, id, status) on table rollback_plan" in normalized
    assert "grant update (status, updated_at) on table rollback_plan" in normalized
    assert (
        "grant select on table ansible_inventory, ansible_playbook, "
        "ansible_execution_policy, ansible_credential_ref"
    ) in " ".join(sql.split())
    assert "grant select on table webhook_connector, webhook_execution_policy" in " ".join(
        sql.split()
    )
    assert "grant all" not in sql
    assert "alter role aegisops_runner with login nosuperuser nocreatedb nocreaterole noinherit" in role_sql
    assert "revoke temporary on database" in role_sql
    assert "from public" in role_sql


def test_docker_compose_resolves_only_default_core_without_profiles():
    if shutil.which("docker") is None:
        pytest.skip("docker compose is required")

    result = subprocess.run(
        ["docker", "compose", "-f", str(COMPOSE_FILE), "config", "--services"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
        env={
            **os.environ,
            "AIOPS_JWT_SECRET": "compose-contract-test-jwt-secret",
            "AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN": "compose-contract-test-webhook-secret",
        },
    )

    assert set(result.stdout.splitlines()) == {"postgres", "aegisops-app"}


def _resolved_services(*profiles: str) -> set[str]:
    command = ["docker", "compose"]
    for profile in profiles:
        command.extend(["--profile", profile])
    command.extend(["-f", str(COMPOSE_FILE), "config", "--services"])
    result = subprocess.run(
        command,
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
        env={
            **os.environ,
            "AIOPS_JWT_SECRET": "compose-contract-test-jwt-secret",
            "AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN": (
                "compose-contract-test-webhook-secret"
            ),
        },
    )
    return set(result.stdout.splitlines())


def _secret_targets(service: dict[str, object]) -> set[str]:
    targets: set[str] = set()
    for item in service.get("secrets", []):
        if isinstance(item, str):
            targets.add(item)
        else:
            targets.add(item.get("target", item["source"]))
    return targets
