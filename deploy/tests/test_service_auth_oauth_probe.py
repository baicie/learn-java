from __future__ import annotations

import base64
import importlib.util
import json
from pathlib import Path
from typing import Any

import pytest


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "deploy/scripts/verify-service-auth-oauth.py"


class JsonResponse:
    def __init__(self, value: dict[str, object]):
        self.payload = json.dumps(value).encode("utf-8")

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def read(self) -> bytes:
        return self.payload


class SequenceOpener:
    def __init__(self, outcomes):
        self.outcomes = list(outcomes)
        self.attempts = 0

    def open(self, _request, *, timeout):
        assert timeout == 10.0
        self.attempts += 1
        outcome = self.outcomes.pop(0)
        if isinstance(outcome, BaseException):
            raise outcome
        return outcome


def load_probe_module():
    assert SCRIPT.exists(), "production OAuth probe script is missing"
    spec = importlib.util.spec_from_file_location("service_auth_oauth_probe", SCRIPT)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def jwt(claims: dict[str, object]) -> str:
    def segment(value: dict[str, object]) -> str:
        encoded = json.dumps(value, separators=(",", ":")).encode("utf-8")
        return base64.urlsafe_b64encode(encoded).rstrip(b"=").decode("ascii")

    return f"{segment({'alg': 'RS256'})}.{segment(claims)}.signature"


def configure_probe_main(
    monkeypatch,
    module,
    *,
    subjects: dict[str, str] | None = None,
    client_claims: dict[str, dict[str, str]] | None = None,
    agent_service_ids: dict[str, str] | None = None,
    java_service_id: str | None = None,
    expected_status_calls: list[dict[str, Any]] | None = None,
) -> None:
    issuer = "https://idp.example.com/realms/aegisops"
    client_ids = {
        "server": "aiops-server",
        "worker": "aiops-worker",
        "agent": "aiops-agent",
    }
    expected_subjects = subjects or {
        "server": "svc:aiops-server",
        "worker": "svc:aiops-worker",
        "agent": "svc:aiops-agent",
    }
    scopes = {
        "server": {"agent:diagnose", "agent:resume", "agent:work-record"},
        "worker": {"agent:diagnose", "agent:work-record"},
        "agent": {
            "evidence:read",
            "cases:read",
            "plugin:authorize",
            "memory:read",
            "memory:write",
            "checkpoint:read",
            "checkpoint:write",
        },
    }
    audiences = {
        "server": "aiops-agent-api",
        "worker": "aiops-agent-api",
        "agent": "aegisops-internal-api",
    }
    expected_client_claims = client_claims or {
        caller: {
            "azp": client_ids[caller],
            "client_id": client_ids[caller],
        }
        for caller in client_ids
    }
    tokens = {
        client_ids[caller]: jwt(
            {
                "iss": issuer,
                "sub": expected_subjects[caller],
                "aud": [audiences[caller]],
                "scope": " ".join(sorted(scopes[caller])),
                "iat": 1_000,
                "exp": 1_300,
                **expected_client_claims[caller],
            }
        )
        for caller in client_ids
    }

    for name, value in {
        "AIOPS_SERVICE_AUTH_ISSUER_URI": issuer,
        "AIOPS_SERVICE_AUTH_JWK_SET_URI": f"{issuer}/certs",
        "AIOPS_SERVICE_AUTH_TOKEN_URI": f"{issuer}/token",
        "AIOPS_SERVER_OAUTH2_CLIENT_SECRET": "server-secret",
        "AIOPS_WORKER_OAUTH2_CLIENT_SECRET": "worker-secret",
        "AIOPS_AGENT_OAUTH2_CLIENT_SECRET": "agent-secret",
        "AIOPS_DIAGNOSIS_GRANT_SECRET": "x" * 32,
    }.items():
        monkeypatch.setenv(name, value)
    monkeypatch.setattr(module.time, "time", lambda: 1_001)
    monkeypatch.setattr(
        module,
        "exchange_token",
        lambda _url, client_id, _secret, _scopes: tokens[client_id],
    )

    configured_agent_ids = agent_service_ids or {
        "server": expected_subjects["server"],
        "worker": expected_subjects["worker"],
    }

    def fake_request_json(url: str, **kwargs: Any) -> dict[str, Any]:
        if url.endswith("/certs"):
            return {"keys": [{"kid": "test-key"}]}
        if url.endswith("/v1/auth/probe"):
            token = kwargs["headers"]["Authorization"].removeprefix("Bearer ")
            subject = module.decode_segment(token.split(".")[1])["sub"]
            caller = "server" if subject == expected_subjects["server"] else "worker"
            return {"ok": True, "serviceId": configured_agent_ids[caller]}
        if url.endswith("/internal/agent/auth/probe"):
            grant = kwargs["headers"]["X-AegisOps-Diagnosis-Grant"]
            claims = module.decode_segment(grant.split(".")[1])
            return {
                "ok": True,
                "serviceId": java_service_id or expected_subjects["agent"],
                "tenantId": claims["tenantId"],
                "incidentId": claims["incidentId"],
                "traceId": claims["traceId"],
            }
        raise AssertionError(f"unexpected URL: {url}")

    monkeypatch.setattr(module, "request_json", fake_request_json)

    def fake_expect_http_status(
        url: str, *, expected_status: int, **kwargs: Any
    ) -> None:
        if expected_status_calls is not None:
            expected_status_calls.append(
                {
                    "url": url,
                    "expected_status": expected_status,
                    **kwargs,
                }
            )

    monkeypatch.setattr(module, "expect_http_status", fake_expect_http_status)


def test_probe_validates_short_lived_audience_and_scopes():
    module = load_probe_module()
    token = jwt(
        {
            "iss": "https://idp.example.com/realms/aegisops",
            "sub": "svc:aiops-server",
            "aud": ["aiops-agent-api"],
            "scope": "agent:diagnose openid profile",
            "iat": 1_000,
            "exp": 1_300,
        }
    )

    claims = module.validate_access_token(
        token,
        expected_issuer="https://idp.example.com/realms/aegisops",
        expected_audience="aiops-agent-api",
        expected_scopes={"agent:diagnose"},
        now=1_001,
    )

    assert claims["sub"] == "svc:aiops-server"


def test_probe_rejects_known_aegisops_scope_escalation():
    module = load_probe_module()
    token = jwt(
        {
            "iss": "https://idp.example.com/realms/aegisops",
            "sub": "svc:aiops-worker",
            "aud": ["aiops-agent-api"],
            "scope": "agent:diagnose agent:work-record agent:resume openid",
            "iat": 1_000,
            "exp": 1_300,
        }
    )

    with pytest.raises(RuntimeError, match="unexpected AegisOps scopes"):
        module.validate_access_token(
            token,
            expected_issuer="https://idp.example.com/realms/aegisops",
            expected_audience="aiops-agent-api",
            expected_scopes={"agent:diagnose", "agent:work-record"},
            now=1_001,
        )


@pytest.mark.parametrize(
    "claims",
    [
        {
            "iss": "https://idp.example.com/realms/aegisops",
            "sub": "svc:aiops-server",
            "aud": ["wrong-api"],
            "scope": "agent:diagnose",
            "iat": 1_000,
            "exp": 1_300,
        },
        {
            "iss": "https://idp.example.com/realms/aegisops",
            "sub": "svc:aiops-server",
            "aud": ["aiops-agent-api"],
            "scope": "agent:diagnose",
            "iat": 1_000,
            "exp": 1_301,
        },
        {
            "iss": "https://idp.example.com/realms/aegisops",
            "sub": "svc:aiops-server",
            "aud": ["aiops-agent-api"],
            "scope": "agent:diagnose",
            "iat": 1_032,
            "exp": 1_300,
        },
    ],
)
def test_probe_rejects_invalid_service_token_contract(claims):
    module = load_probe_module()

    with pytest.raises(RuntimeError):
        module.validate_access_token(
            jwt(claims),
            expected_issuer="https://idp.example.com/realms/aegisops",
            expected_audience="aiops-agent-api",
            expected_scopes={"agent:diagnose"},
            now=1_001,
        )


@pytest.mark.parametrize("identity_claim", ["azp", "client_id"])
def test_main_accepts_keycloak_client_identity_claims(monkeypatch, identity_claim):
    module = load_probe_module()
    configure_probe_main(
        monkeypatch,
        module,
        client_claims={
            caller: {identity_claim: f"aiops-{caller}"}
            for caller in ("server", "worker", "agent")
        },
    )

    module.main()


@pytest.mark.parametrize(
    "claims",
    [
        {"sub": "svc:aiops-server"},
        {"sub": "svc:aiops-server", "azp": "aiops-worker"},
        {
            "sub": "svc:aiops-server",
            "azp": "aiops-server",
            "client_id": "aiops-worker",
        },
    ],
)
def test_main_rejects_missing_or_conflicting_client_identity(monkeypatch, claims):
    module = load_probe_module()
    configure_probe_main(
        monkeypatch,
        module,
        client_claims={
            "server": claims,
            "worker": {"azp": "aiops-worker"},
            "agent": {"azp": "aiops-agent"},
        },
    )

    with pytest.raises(RuntimeError, match="client identity"):
        module.main()


def test_main_rejects_shared_service_subject(monkeypatch):
    module = load_probe_module()
    configure_probe_main(
        monkeypatch,
        module,
        subjects={
            "server": "svc:shared",
            "worker": "svc:shared",
            "agent": "svc:shared",
        },
    )

    with pytest.raises(RuntimeError, match="distinct"):
        module.main()


def test_main_rejects_shared_oauth_client_secrets(monkeypatch):
    module = load_probe_module()
    configure_probe_main(monkeypatch, module)
    shared_secret = "shared-client-secret"
    monkeypatch.setenv("AIOPS_SERVER_OAUTH2_CLIENT_SECRET", shared_secret)
    monkeypatch.setenv("AIOPS_WORKER_OAUTH2_CLIENT_SECRET", shared_secret)
    monkeypatch.setenv("AIOPS_AGENT_OAUTH2_CLIENT_SECRET", shared_secret)

    with pytest.raises(RuntimeError, match="client secrets must be distinct") as error:
        module.main()

    assert shared_secret not in str(error.value)


def test_main_rejects_agent_probe_service_id_mismatch(monkeypatch):
    module = load_probe_module()
    configure_probe_main(
        monkeypatch,
        module,
        agent_service_ids={
            "server": "svc:wrong-server",
            "worker": "svc:aiops-worker",
        },
    )

    with pytest.raises(RuntimeError, match="server service identity"):
        module.main()


def test_main_rejects_java_probe_service_id_mismatch(monkeypatch):
    module = load_probe_module()
    configure_probe_main(
        monkeypatch,
        module,
        java_service_id="svc:wrong-agent",
    )

    with pytest.raises(RuntimeError, match="serviceId"):
        module.main()


def test_main_runs_non_destructive_diagnosis_grant_negative_probes(monkeypatch):
    module = load_probe_module()
    calls: list[dict[str, Any]] = []
    configure_probe_main(monkeypatch, module, expected_status_calls=calls)

    module.main()

    assert [(call["url"], call["expected_status"]) for call in calls] == [
        ("http://localhost:9008/v1/diagnose", 401),
        ("http://localhost:8080/internal/agent/auth/probe", 401),
        ("http://localhost:8080/internal/agent/auth/probe", 401),
        ("http://localhost:8080/internal/agent/auth/probe", 401),
        ("http://localhost:8080/internal/agent/auth/probe", 401),
        ("http://localhost:8080/internal/agent/evidence/query", 403),
    ]
    assert "X-AegisOps-Diagnosis-Grant" not in calls[0]["headers"]
    assert "X-AegisOps-Diagnosis-Grant" not in calls[1]["headers"]
    assert calls[-1]["body"]["incidentId"].endswith("-mismatch")


def test_expect_http_status_accepts_expected_auth_rejection_without_retry(monkeypatch):
    module = load_probe_module()
    opener = SequenceOpener(
        [
            module.urllib.error.HTTPError(
                "https://server.example.com/internal/agent/auth/probe",
                401,
                "unauthorized",
                {},
                None,
            )
        ]
    )
    sleeps: list[float] = []
    monkeypatch.setattr(module, "DIRECT_HTTP_OPENER", opener)
    monkeypatch.setattr(module.time, "sleep", sleeps.append)

    module.expect_http_status(
        "https://server.example.com/internal/agent/auth/probe",
        expected_status=401,
        method="POST",
        headers={"Authorization": "Bearer test-token"},
        body={},
    )

    assert opener.attempts == 1
    assert sleeps == []


def test_request_json_retries_url_error_then_succeeds(monkeypatch):
    module = load_probe_module()
    opener = SequenceOpener(
        [
            module.urllib.error.URLError("temporary failure"),
            JsonResponse({"ok": True}),
        ]
    )
    sleeps: list[float] = []
    monkeypatch.setattr(module, "DIRECT_HTTP_OPENER", opener)
    monkeypatch.setattr(module.time, "sleep", sleeps.append)

    assert module.request_json("https://idp.example.com/certs") == {"ok": True}
    assert opener.attempts == 2
    assert sleeps == [1.0]


@pytest.mark.parametrize("status_code", [408, 429, 500, 503])
def test_request_json_retries_transient_http_status_then_succeeds(
    monkeypatch, status_code
):
    module = load_probe_module()
    opener = SequenceOpener(
        [
            module.urllib.error.HTTPError(
                "https://idp.example.com/token",
                status_code,
                "transient failure",
                {},
                None,
            ),
            JsonResponse({"access_token": "token"}),
        ]
    )
    sleeps: list[float] = []
    monkeypatch.setattr(module, "DIRECT_HTTP_OPENER", opener)
    monkeypatch.setattr(module.time, "sleep", sleeps.append)

    assert module.request_json("https://idp.example.com/token") == {
        "access_token": "token"
    }
    assert opener.attempts == 2
    assert sleeps == [1.0]


def test_request_json_stops_after_three_transient_failures(monkeypatch):
    module = load_probe_module()
    opener = SequenceOpener(
        [
            module.urllib.error.URLError("temporary failure"),
            module.urllib.error.URLError("temporary failure"),
            module.urllib.error.URLError("temporary failure"),
        ]
    )
    sleeps: list[float] = []
    monkeypatch.setattr(module, "DIRECT_HTTP_OPENER", opener)
    monkeypatch.setattr(module.time, "sleep", sleeps.append)

    with pytest.raises(RuntimeError, match="GET https://idp.example.com/certs failed"):
        module.request_json("https://idp.example.com/certs")

    assert opener.attempts == 3
    assert sleeps == [1.0, 2.0]


@pytest.mark.parametrize("status_code", [400, 401, 403])
def test_request_json_does_not_retry_non_transient_http_status(
    monkeypatch, status_code
):
    module = load_probe_module()
    opener = SequenceOpener(
        [
            module.urllib.error.HTTPError(
                "https://idp.example.com/token",
                status_code,
                "permanent failure",
                {},
                None,
            ),
            JsonResponse({"unexpected": "retry"}),
        ]
    )
    sleeps: list[float] = []
    monkeypatch.setattr(module, "DIRECT_HTTP_OPENER", opener)
    monkeypatch.setattr(module.time, "sleep", sleeps.append)

    with pytest.raises(RuntimeError, match=f"HTTP {status_code}"):
        module.request_json("https://idp.example.com/token")

    assert opener.attempts == 1
    assert sleeps == []
