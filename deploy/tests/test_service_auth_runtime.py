from __future__ import annotations

import base64
import hashlib
import hmac
import importlib.util
import io
import json
import subprocess
from pathlib import Path

import pytest
import yaml


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/ci/verify-service-auth-runtime.py"


def load_runtime_verifier():
    spec = importlib.util.spec_from_file_location("service_auth_runtime", SCRIPT)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def encode_segment(value: dict[str, object]) -> str:
    raw = json.dumps(value, separators=(",", ":")).encode("utf-8")
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def unsigned_token(claims: dict[str, object]) -> str:
    return f"{encode_segment({'alg': 'RS256'})}.{encode_segment(claims)}.signature"


def test_token_claim_validation_requires_short_lived_expected_identity():
    verifier = load_runtime_verifier()
    token = unsigned_token(
        {
            "iss": "http://keycloak:8080/realms/aegisops",
            "aud": ["aiops-agent-api"],
            "sub": "service-account-aiops-server",
            "scope": "agent:diagnose agent:resume agent:work-record",
            "iat": 1_000,
            "exp": 1_300,
        }
    )

    verifier.validate_access_token(
        token,
        expected_issuer="http://keycloak:8080/realms/aegisops",
        expected_audience="aiops-agent-api",
        expected_scopes={"agent:diagnose", "agent:resume", "agent:work-record"},
        now=1_100,
    )

    missing_exp = unsigned_token(
        {
            "iss": "http://keycloak:8080/realms/aegisops",
            "aud": "aiops-agent-api",
            "sub": "service-account-aiops-server",
            "scope": "agent:diagnose",
            "iat": 1_000,
        }
    )
    with pytest.raises(RuntimeError, match="exp"):
        verifier.validate_access_token(
            missing_exp,
            expected_issuer="http://keycloak:8080/realms/aegisops",
            expected_audience="aiops-agent-api",
            expected_scopes={"agent:diagnose"},
            now=1_100,
        )


def test_token_claim_validation_rejects_known_scope_escalation():
    verifier = load_runtime_verifier()
    token = unsigned_token(
        {
            "iss": "http://keycloak:8080/realms/aegisops",
            "aud": ["aiops-agent-api"],
            "sub": "service-account-aiops-worker",
            "scope": "agent:diagnose agent:work-record agent:resume openid",
            "iat": 1_000,
            "exp": 1_300,
        }
    )

    with pytest.raises(RuntimeError, match="unexpected AegisOps scopes"):
        verifier.validate_access_token(
            token,
            expected_issuer="http://keycloak:8080/realms/aegisops",
            expected_audience="aiops-agent-api",
            expected_scopes={"agent:diagnose", "agent:work-record"},
            now=1_100,
        )


def test_diagnosis_grant_binds_runtime_context_and_signature():
    verifier = load_runtime_verifier()
    secret = "runtime-smoke-diagnosis-grant-secret"

    grant = verifier.issue_diagnosis_grant(
        secret=secret,
        issuer="aiops-server",
        audience="aegisops-internal-api",
        tenant_id="tenant-smoke",
        incident_id="incident-smoke",
        trace_id="trace-smoke",
        now=1_000,
        jti="grant-smoke",
    )

    header_segment, payload_segment, signature_segment = grant.split(".")
    payload = verifier.decode_segment(payload_segment)
    signature = base64.urlsafe_b64decode(signature_segment + "==")
    expected_signature = hmac.new(
        secret.encode("utf-8"),
        f"{header_segment}.{payload_segment}".encode("ascii"),
        hashlib.sha256,
    ).digest()

    assert payload == {
        "iss": "aiops-server",
        "aud": "aegisops-internal-api",
        "tenantId": "tenant-smoke",
        "incidentId": "incident-smoke",
        "traceId": "trace-smoke",
        "iat": 1_000,
        "exp": 1_300,
        "jti": "grant-smoke",
    }
    assert hmac.compare_digest(signature, expected_signature)


def test_runtime_requests_use_an_opener_without_environment_proxies(monkeypatch):
    monkeypatch.setenv("HTTP_PROXY", "http://proxy.invalid:8080")
    monkeypatch.setenv("HTTPS_PROXY", "http://proxy.invalid:8443")
    verifier = load_runtime_verifier()

    opener = getattr(verifier, "DIRECT_HTTP_OPENER", None)
    assert opener is not None
    assert not any(
        isinstance(handler, verifier.urllib.request.ProxyHandler)
        for handler in opener.handlers
    )

    monkeypatch.setattr(
        verifier.urllib.request,
        "urlopen",
        lambda *args, **kwargs: pytest.fail("request_json used proxy-aware urlopen"),
    )
    assert verifier.request_json("data:application/json,%7B%22ok%22%3Atrue%7D") == {
        "ok": True
    }


def test_release_pipeline_executes_runtime_oauth_verification():
    workflow = (ROOT / ".github/workflows/release-verify.yml").read_text(encoding="utf-8")
    preflight = (ROOT / "scripts/ci/release-preflight.sh").read_text(encoding="utf-8")

    assert "infra/keycloak" in workflow
    assert "wait_health aegisops-keycloak" in workflow
    assert "python3 scripts/ci/verify-service-auth-runtime.py" in workflow
    assert "python3 scripts/ci/verify-service-auth-runtime.py 2>&1" in workflow
    assert "| python3 deploy/scripts/redact-runtime-output.py" in workflow
    assert "-f deploy/docker-compose.idp.yml" in preflight


def test_runtime_verifier_does_not_include_error_response_bodies(monkeypatch):
    verifier = load_runtime_verifier()
    reflected_secret = "reflected-authorization-secret"
    error = verifier.urllib.error.HTTPError(
        "http://localhost:9008/v1/auth/probe",
        401,
        "unauthorized",
        {},
        io.BytesIO(f'{{"authorization":"{reflected_secret}"}}'.encode()),
    )

    def fail_request(*_args, **_kwargs):
        raise error

    monkeypatch.setattr(verifier.DIRECT_HTTP_OPENER, "open", fail_request)

    with pytest.raises(RuntimeError, match="returned HTTP 401") as captured:
        verifier.request_json("http://localhost:9008/v1/auth/probe")

    assert reflected_secret not in str(captured.value)


def test_release_runtime_uses_workflow_mode_for_callback_smoke():
    workflow = yaml.safe_load(
        (ROOT / ".github/workflows/release-verify.yml").read_text(encoding="utf-8")
    )
    runtime_env = workflow["jobs"]["runtime-smoke"]["env"]

    assert runtime_env["AIOPS_AGENT_GENERATION_MODE"] == "workflow"
    assert runtime_env["AIOPS_AGENT_WORKFLOW_EVIDENCE_ENABLED"] == "true"
    assert runtime_env["AIOPS_AGENT_WORKFLOW_CASE_RETRIEVAL_ENABLED"] == "false"
    assert runtime_env["AIOPS_AGENT_WORKFLOW_MEMORY_ENABLED"] == "false"

    compose = yaml.safe_load(
        (ROOT / "deploy/docker-compose.app.yml").read_text(encoding="utf-8")
    )
    agent_env = compose["services"]["aiops-agent"]["environment"]
    assert agent_env["AIOPS_AGENT_WORKFLOW_EVIDENCE_ENABLED"] == (
        "${AIOPS_AGENT_WORKFLOW_EVIDENCE_ENABLED:-true}"
    )
    assert agent_env["AIOPS_AGENT_WORKFLOW_CASE_RETRIEVAL_ENABLED"] == (
        "${AIOPS_AGENT_WORKFLOW_CASE_RETRIEVAL_ENABLED:-true}"
    )
    assert agent_env["AIOPS_AGENT_WORKFLOW_MEMORY_ENABLED"] == (
        "${AIOPS_AGENT_WORKFLOW_MEMORY_ENABLED:-true}"
    )


def test_business_diagnosis_uses_java_controller_and_verified_tenant(monkeypatch):
    verifier = load_runtime_verifier()
    requests: list[tuple[str, dict[str, object]]] = []
    seeded: list[tuple[str, str]] = []
    cleaned: list[tuple[str, str]] = []

    def fake_request_json(url: str, **kwargs):
        requests.append((url, kwargs))
        if url.endswith("/api/auth/login"):
            return {
                "success": True,
                "data": {
                    "token": "user-access-token",
                    "user": {"tenantId": "tenant-runtime-smoke"},
                },
            }
        return {
            "success": True,
            "data": {
                "status": "completed",
                "raw": {
                    "generationMode": "workflow",
                        "workflow": {
                            "evidence": [
                                {
                                    "evidence_type": "change",
                                    "title": "OAuth callback runtime marker",
                                }
                            ],
                            "safety_notes": [],
                        },
                },
            },
        }

    monkeypatch.setattr(verifier, "request_json", fake_request_json)
    monkeypatch.setattr(
        verifier,
        "upsert_runtime_incident",
        lambda tenant_id, incident_id: seeded.append((tenant_id, incident_id)),
    )
    monkeypatch.setattr(
        verifier,
        "cleanup_runtime_incident",
        lambda tenant_id, incident_id: cleaned.append((tenant_id, incident_id)),
    )

    verifier.verify_java_agent_business_path(
        server_url="http://localhost:8080",
        username="admin",
        password="admin123",
        incident_id="incident-oauth-runtime-smoke",
    )

    assert seeded == [("tenant-runtime-smoke", "incident-oauth-runtime-smoke")]
    assert cleaned == [("tenant-runtime-smoke", "incident-oauth-runtime-smoke")]
    assert requests[0] == (
        "http://localhost:8080/api/auth/login",
        {
            "method": "POST",
            "body": {"username": "admin", "password": "admin123"},
        },
    )
    assert requests[1][0].endswith(
        "/api/incidents/incident-oauth-runtime-smoke/ai/diagnose"
    )
    assert requests[1][1]["headers"] == {
        "Authorization": "Bearer user-access-token"
    }
    assert requests[1][1]["body"] == {"force": True, "locale": "zh-CN"}


def test_business_diagnosis_rejects_agent_callback_failure(monkeypatch):
    verifier = load_runtime_verifier()
    cleaned: list[tuple[str, str]] = []

    responses = iter(
        [
            {
                "success": True,
                "data": {
                    "token": "user-access-token",
                    "user": {"tenantId": "tenant-runtime-smoke"},
                },
            },
            {
                "success": True,
                "data": {
                    "raw": {
                        "generationMode": "workflow",
                        "workflow": {
                            "evidence": [
                                {
                                    "evidence_type": "tool_error",
                                    "summary": "internal callback returned 401",
                                }
                            ]
                        },
                    }
                },
            },
        ]
    )
    monkeypatch.setattr(verifier, "request_json", lambda *args, **kwargs: next(responses))
    monkeypatch.setattr(verifier, "upsert_runtime_incident", lambda *args: None)
    monkeypatch.setattr(
        verifier,
        "cleanup_runtime_incident",
        lambda tenant_id, incident_id: cleaned.append((tenant_id, incident_id)),
    )

    with pytest.raises(RuntimeError, match="callback"):
        verifier.verify_java_agent_business_path(
            server_url="http://localhost:8080",
            username="admin",
            password="admin123",
            incident_id="incident-oauth-runtime-smoke",
        )

    assert cleaned == [("tenant-runtime-smoke", "incident-oauth-runtime-smoke")]


def test_runtime_incident_seed_uses_parameterized_docker_exec(monkeypatch):
    verifier = load_runtime_verifier()
    captured: dict[str, object] = {}

    def fake_run(command, **kwargs):
        captured["command"] = command
        captured.update(kwargs)
        return subprocess.CompletedProcess(command, 0)

    monkeypatch.setattr(verifier.subprocess, "run", fake_run)

    verifier.upsert_runtime_incident(
        "tenant-runtime-smoke",
        "incident-oauth-runtime-smoke",
    )

    assert captured["command"] == [
        "docker",
        "exec",
        "-i",
        "aegisops-postgres",
        "psql",
        "-v",
        "ON_ERROR_STOP=1",
        "-v",
        "tenant_id=tenant-runtime-smoke",
        "-v",
        "incident_id=incident-oauth-runtime-smoke",
        "-U",
        "aegisops",
        "-d",
        "aegisops",
    ]
    assert "VALUES (:'incident_id', :'tenant_id'" in str(captured["input"])
    assert "OAuth callback runtime marker" in str(captured["input"])
    assert captured["check"] is True


def test_runtime_incident_cleanup_uses_parameterized_docker_exec(monkeypatch):
    verifier = load_runtime_verifier()
    captured: dict[str, object] = {}

    def fake_run(command, **kwargs):
        captured["command"] = command
        captured.update(kwargs)
        return subprocess.CompletedProcess(command, 0)

    monkeypatch.setattr(verifier.subprocess, "run", fake_run)

    verifier.cleanup_runtime_incident(
        "tenant-runtime-smoke",
        "incident-oauth-runtime-smoke",
    )

    assert captured["command"][:4] == ["docker", "exec", "-i", "aegisops-postgres"]
    assert "DELETE FROM change_event" in str(captured["input"])
    assert "DELETE FROM incident" in str(captured["input"])
    assert captured["check"] is True


def test_pull_requests_run_oauth_smoke_without_publish_or_deploy():
    workflow = yaml.safe_load(
        (ROOT / ".github/workflows/release-verify.yml").read_text(encoding="utf-8")
    )
    runtime_smoke = workflow["jobs"]["runtime-smoke"]
    runtime_steps = {step["name"]: step for step in runtime_smoke["steps"]}
    deploy = workflow["jobs"]["deploy"]

    assert runtime_smoke["if"] == "needs.preflight.outputs.release_required == 'true'"
    assert "environment" not in runtime_smoke
    assert runtime_steps["Log in to Docker Hub"]["if"] == (
        "github.event_name != 'pull_request'"
    )
    assert runtime_steps["Push verified images"]["if"] == (
        "github.event_name != 'pull_request'"
    )
    assert deploy["if"] == "github.event_name != 'pull_request'"


def test_runtime_diagnostics_include_identity_provider_overlay_and_keycloak():
    workflow = (ROOT / ".github/workflows/release-verify.yml").read_text(encoding="utf-8")
    diagnostics = (ROOT / "scripts/ci/capture-runtime-diagnostics.sh").read_text(
        encoding="utf-8"
    )

    assert "COMPOSE_OVERLAY_FILE: deploy/docker-compose.idp.yml" in workflow
    assert "COMPOSE_OVERLAY_FILE" in diagnostics
    assert "aegisops-keycloak" in diagnostics


def test_release_runtime_failure_trap_redacts_logs_before_emitting_them():
    workflow = yaml.safe_load(
        (ROOT / ".github/workflows/release-verify.yml").read_text(encoding="utf-8")
    )
    runtime_steps = workflow["jobs"]["runtime-smoke"]["steps"]
    start_step = next(
        step
        for step in runtime_steps
        if step["name"] == "Start full runtime and verify container health"
    )
    run_script = start_step["run"]

    assert "compose logs --no-color --tail=160 2>&1" in run_script
    assert "| python3 deploy/scripts/redact-runtime-output.py || true" in run_script
    assert "compose logs --no-color --tail=160 || true" not in run_script


def test_deployment_gate_runs_python_contract_suites():
    deployment = (ROOT / "scripts/ci/deployment.sh").read_text(encoding="utf-8")
    workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")

    assert "python3 -m pytest deploy/helm/aegisops/tests deploy/tests" in deployment
    assert "actions/setup-python" in workflow
    assert "pytest" in workflow
    assert "PyYAML" in workflow


def test_offline_delivery_carries_secret_generator_and_documents_external_idp():
    package_script = (ROOT / "scripts/deploy/package-offline.sh").read_text(
        encoding="utf-8"
    )
    offline_readme = (ROOT / "deploy/offline/README.md").read_text(encoding="utf-8")

    assert (
        'cp scripts/deploy/generate-secrets.sh "$OUT_DIR/scripts/generate-secrets.sh"'
        in package_script
    )
    assert "OAuth2 Client Credentials" in offline_readme
    assert "aiops-server" in offline_readme
    assert "aiops-worker" in offline_readme
    assert "aiops-agent" in offline_readme
    assert "不包含 IdP" in offline_readme


def test_offline_readme_is_self_contained_for_oauth_client_contracts():
    offline_readme = (ROOT / "deploy/offline/README.md").read_text(encoding="utf-8")

    expected_clients = {
        "aiops-server": (
            "aiops-agent-api",
            "agent:diagnose agent:resume agent:work-record",
        ),
        "aiops-worker": (
            "aiops-agent-api",
            "agent:diagnose agent:work-record",
        ),
        "aiops-agent": (
            "aegisops-internal-api",
            (
                "evidence:read cases:read checkpoint:read checkpoint:write "
                "memory:read memory:write plugin:authorize"
            ),
        ),
    }

    for client_id, (audience, scopes) in expected_clients.items():
        assert any(
            f"`{client_id}`" in line
            and f"`{audience}`" in line
            and f"`{scopes}`" in line
            for line in offline_readme.splitlines()
        )

    assert "docs/api/internal-service-authentication.md" not in offline_readme


def test_phase8_design_contains_no_executable_static_token_configuration():
    phase8 = (ROOT / "docs/phase8.md").read_text(encoding="utf-8")

    assert "docs/adr/0010-service-authentication-oauth2-only.md" in phase8
    assert "AIOPS_AGENT_OUTBOUND_OAUTH2_CLIENT_ID=aiops-agent" in phase8
    assert "X-AegisOps-Diagnosis-Grant" in phase8
    assert "AIOPS_INTERNAL_AGENT_TOKEN=" not in phase8
    assert "internal-agent-token:" not in phase8
    assert "X-AIOPS-INTERNAL-TOKEN" not in phase8


def test_superseded_phase8_static_auth_designs_are_explicitly_deprecated():
    superseded = (
        "docs/mvp/design/phase4.md",
        "docs/mvp/design/phase4.1.md",
        "docs/mvp/design/phase4.2.md",
        "docs/mvp/design/phase4.3.md",
        "docs/mvp/design/phase4.4.md",
        "docs/mvp/design/phase8.0-saas-multi-tenant-hardening.md",
        "docs/mvp/phase8.1.md",
        "docs/mvp/phase8.2.md",
    )

    for relative_path in superseded:
        document = (ROOT / relative_path).read_text(encoding="utf-8")
        frontmatter = document.split("---", 2)[1]
        assert "status: deprecated" in frontmatter
        assert "docs/adr/0010-service-authentication-oauth2-only.md" in document

    phase18 = (ROOT / "docs/record/phase/18.md").read_text(encoding="utf-8")
    assert "internal-agent-token-required" not in phase18

    active_guidance = "\n".join(
        (ROOT / relative_path).read_text(encoding="utf-8")
        for relative_path in (
            "docs/designs/phase-20/2026-07-19-dify-workflow-integration.md",
            "docs/record/phase/20/05-phase-20-5-ai-summary-monthly-report.md",
        )
    )
    for removed_contract in (
        "internal token",
        "verify_internal_token",
        "INTERNAL_TOKEN_HEADER",
        "normalizedInternalToken",
    ):
        assert removed_contract not in active_guidance
