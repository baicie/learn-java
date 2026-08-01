#!/usr/bin/env python3
"""Verify the complete OAuth2 trust path before release images are published."""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from typing import Any


MAX_TOKEN_TTL_SECONDS = 300
GRANT_TTL_SECONDS = 300
RUNTIME_CHANGE_MARKER = "OAuth callback runtime marker"
DIRECT_HTTP_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))
AEGISOPS_SERVICE_SCOPES = frozenset(
    {
        "agent:diagnose",
        "agent:resume",
        "agent:work-record",
        "evidence:read",
        "cases:read",
        "plugin:authorize",
        "memory:read",
        "memory:write",
        "checkpoint:read",
        "checkpoint:write",
    }
)


def decode_segment(segment: str) -> dict[str, Any]:
    padding = "=" * (-len(segment) % 4)
    try:
        decoded = base64.urlsafe_b64decode(segment + padding)
        value = json.loads(decoded)
    except (ValueError, json.JSONDecodeError) as exc:
        raise RuntimeError("JWT contains an invalid JSON segment") from exc
    if not isinstance(value, dict):
        raise RuntimeError("JWT segment must contain a JSON object")
    return value


def encode_segment(value: dict[str, Any]) -> str:
    raw = json.dumps(value, separators=(",", ":")).encode("utf-8")
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def validate_access_token(
    token: str,
    *,
    expected_issuer: str,
    expected_audience: str,
    expected_scopes: set[str],
    now: int | None = None,
) -> dict[str, Any]:
    parts = token.split(".")
    if len(parts) != 3:
        raise RuntimeError("Access token must contain three JWT segments")
    claims = decode_segment(parts[1])

    if claims.get("iss") != expected_issuer:
        raise RuntimeError("Access token has an unexpected iss claim")
    audiences = claims.get("aud")
    if isinstance(audiences, str):
        audiences = [audiences]
    if not isinstance(audiences, list) or expected_audience not in audiences:
        raise RuntimeError("Access token has an unexpected aud claim")
    if not str(claims.get("sub") or "").strip():
        raise RuntimeError("Access token is missing sub")

    issued_at = claims.get("iat")
    expires_at = claims.get("exp")
    if not isinstance(issued_at, int):
        raise RuntimeError("Access token is missing iat")
    if not isinstance(expires_at, int):
        raise RuntimeError("Access token is missing exp")
    current_time = int(time.time()) if now is None else now
    if expires_at <= current_time:
        raise RuntimeError("Access token is expired")
    if expires_at <= issued_at or expires_at - issued_at > MAX_TOKEN_TTL_SECONDS:
        raise RuntimeError("Access token is not short lived")

    scopes = set(str(claims.get("scope") or "").split())
    missing_scopes = expected_scopes - scopes
    if missing_scopes:
        raise RuntimeError("Access token is missing required scopes")
    if (scopes & AEGISOPS_SERVICE_SCOPES) - expected_scopes:
        raise RuntimeError("Access token contains unexpected AegisOps scopes")
    return claims


def issue_diagnosis_grant(
    *,
    secret: str,
    issuer: str,
    audience: str,
    tenant_id: str,
    incident_id: str,
    trace_id: str,
    now: int | None = None,
    jti: str | None = None,
) -> str:
    if len(secret.encode("utf-8")) < 32:
        raise RuntimeError("Diagnosis Grant secret must contain at least 32 bytes")
    issued_at = int(time.time()) if now is None else now
    header = encode_segment(
        {"alg": "HS256", "typ": "JWT", "kid": "diagnosis-grant-v1"}
    )
    payload = encode_segment(
        {
            "iss": issuer,
            "aud": audience,
            "tenantId": tenant_id,
            "incidentId": incident_id,
            "traceId": trace_id,
            "iat": issued_at,
            "exp": issued_at + GRANT_TTL_SECONDS,
            "jti": jti or str(uuid.uuid4()),
        }
    )
    signing_input = f"{header}.{payload}"
    signature = hmac.new(
        secret.encode("utf-8"), signing_input.encode("ascii"), hashlib.sha256
    ).digest()
    encoded_signature = base64.urlsafe_b64encode(signature).rstrip(b"=").decode("ascii")
    return f"{signing_input}.{encoded_signature}"


def request_json(
    url: str,
    *,
    method: str = "GET",
    headers: dict[str, str] | None = None,
    body: dict[str, Any] | None = None,
    form: dict[str, str] | None = None,
    timeout: float = 10.0,
) -> dict[str, Any]:
    request_headers = dict(headers or {})
    data = None
    if body is not None:
        request_headers["Content-Type"] = "application/json"
        data = json.dumps(body, separators=(",", ":")).encode("utf-8")
    elif form is not None:
        request_headers["Content-Type"] = "application/x-www-form-urlencoded"
        data = urllib.parse.urlencode(form).encode("ascii")

    request = urllib.request.Request(
        url, data=data, headers=request_headers, method=method
    )
    try:
        with DIRECT_HTTP_OPENER.open(request, timeout=timeout) as response:
            payload = response.read()
    except urllib.error.HTTPError as exc:
        raise RuntimeError(f"{method} {url} returned HTTP {exc.code}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"{method} {url} failed: {exc.reason}") from exc

    try:
        value = json.loads(payload)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"{method} {url} returned invalid JSON") from exc
    if not isinstance(value, dict):
        raise RuntimeError(f"{method} {url} returned a non-object JSON response")
    return value


def wait_for_json(url: str, timeout_seconds: int = 180) -> dict[str, Any]:
    deadline = time.monotonic() + timeout_seconds
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        try:
            return request_json(url, timeout=3.0)
        except RuntimeError as exc:
            last_error = exc
            time.sleep(2)
    raise RuntimeError(f"Identity provider did not become ready: {last_error}")


def required_environment(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"{name} is required")
    return value


def exchange_token(
    token_url: str, client_id: str, client_secret: str, scopes: set[str]
) -> str:
    response = request_json(
        token_url,
        method="POST",
        form={
            "grant_type": "client_credentials",
            "client_id": client_id,
            "client_secret": client_secret,
            "scope": " ".join(sorted(scopes)),
        },
    )
    token = str(response.get("access_token") or "")
    if not token:
        raise RuntimeError(f"Token endpoint returned no access_token for {client_id}")
    return token


def build_work_record_smoke_request(*, tenant_id: str, trace_id: str) -> dict[str, Any]:
    return {
        "contractVersion": "work-record-generation.v1",
        "generationType": "record_summary",
        "tenantId": tenant_id,
        "resourceId": "record-runtime-smoke",
        "records": [
            {
                "id": "record-runtime-smoke",
                "title": "Runtime authentication smoke",
                "status": "completed",
                "recordTime": "2026-07-31T00:00:00Z",
                "ownerName": "runtime-smoke",
                "fields": {"summary": "OAuth2 scope verification"},
                "relations": [],
            }
        ],
        "statistics": {},
        "traceId": trace_id,
    }


def _postgres_command(tenant_id: str, incident_id: str) -> list[str]:
    return [
        "docker",
        "exec",
        "-i",
        "aegisops-postgres",
        "psql",
        "-v",
        "ON_ERROR_STOP=1",
        "-v",
        f"tenant_id={tenant_id}",
        "-v",
        f"incident_id={incident_id}",
        "-U",
        "aegisops",
        "-d",
        "aegisops",
    ]


def upsert_runtime_incident(tenant_id: str, incident_id: str) -> None:
    sql = f"""
INSERT INTO incident (
  id, tenant_id, title, severity, status, source, primary_asset_id,
  started_at, detected_at, last_seen_at
)
VALUES (:'incident_id', :'tenant_id', 'OAuth service callback runtime smoke',
        'high', 'open', 'runtime-smoke', 'asset-oauth-runtime-smoke',
        now() - interval '5 minutes', now() - interval '5 minutes', now())
ON CONFLICT (id) DO UPDATE SET
  tenant_id = EXCLUDED.tenant_id,
  title = EXCLUDED.title,
  severity = EXCLUDED.severity,
  status = EXCLUDED.status,
  source = EXCLUDED.source,
  primary_asset_id = EXCLUDED.primary_asset_id,
  started_at = EXCLUDED.started_at,
  detected_at = EXCLUDED.detected_at,
  last_seen_at = EXCLUDED.last_seen_at,
  updated_at = now();

INSERT INTO change_event (
  id, tenant_id, asset_id, change_type, title, description, source,
  operator, risk_level, occurred_at
)
VALUES ('change-oauth-runtime-smoke', :'tenant_id', 'asset-oauth-runtime-smoke',
        'deploy', '{RUNTIME_CHANGE_MARKER}', 'OAuth2 Agent callback proof',
        'runtime-smoke', 'release-verify', 'low', now() - interval '1 minute')
ON CONFLICT (id) DO UPDATE SET
  tenant_id = EXCLUDED.tenant_id,
  asset_id = EXCLUDED.asset_id,
  title = EXCLUDED.title,
  occurred_at = EXCLUDED.occurred_at;
"""
    subprocess.run(
        _postgres_command(tenant_id, incident_id),
        input=sql,
        text=True,
        check=True,
        timeout=30,
    )


def cleanup_runtime_incident(tenant_id: str, incident_id: str) -> None:
    sql = """
DELETE FROM change_event
WHERE id = 'change-oauth-runtime-smoke' AND tenant_id = :'tenant_id';
DELETE FROM incident
WHERE id = :'incident_id' AND tenant_id = :'tenant_id';
"""
    subprocess.run(
        _postgres_command(tenant_id, incident_id),
        input=sql,
        text=True,
        check=True,
        timeout=30,
    )


def _response_data(response: dict[str, Any], operation: str) -> dict[str, Any]:
    if response.get("success") is not True:
        raise RuntimeError(f"{operation} returned an unsuccessful API response")
    data = response.get("data")
    if not isinstance(data, dict):
        raise RuntimeError(f"{operation} response is missing data")
    return data


def _assert_workflow_callbacks_succeeded(diagnosis: dict[str, Any]) -> None:
    raw = diagnosis.get("raw")
    if not isinstance(raw, dict) or raw.get("generationMode") != "workflow":
        raise RuntimeError("Java diagnosis did not execute the Agent workflow")
    if raw.get("fallbackReason") not in (None, ""):
        raise RuntimeError("Java diagnosis unexpectedly used an Agent fallback")

    workflow = raw.get("workflow")
    if not isinstance(workflow, dict):
        raise RuntimeError("Java diagnosis response is missing workflow details")
    evidence = workflow.get("evidence")
    if not isinstance(evidence, list):
        raise RuntimeError("Java diagnosis response is missing workflow evidence")

    callback_marker_found = False
    for item in evidence:
        if not isinstance(item, dict):
            continue
        if item.get("evidence_type") == "tool_error":
            raise RuntimeError("Agent callback returned tool_error evidence")
        if (
            item.get("evidence_type") == "change"
            and item.get("title") == RUNTIME_CHANGE_MARKER
        ):
            callback_marker_found = True

    serialized = json.dumps(workflow, ensure_ascii=False).lower()
    for marker in (
        "tool_error",
        "retrieval failed",
        "authentication failed",
        "temporarily unavailable",
    ):
        if marker in serialized:
            raise RuntimeError(f"Agent callback failure marker found: {marker}")
    if not callback_marker_found:
        raise RuntimeError("Agent callback did not return the runtime change marker")


def verify_java_agent_business_path(
    *,
    server_url: str,
    username: str,
    password: str,
    incident_id: str,
) -> None:
    login = _response_data(
        request_json(
            f"{server_url}/api/auth/login",
            method="POST",
            body={"username": username, "password": password},
        ),
        "Java login",
    )
    token = str(login.get("token") or "").strip()
    user = login.get("user")
    tenant_id = str(user.get("tenantId") if isinstance(user, dict) else "").strip()
    if not token or not tenant_id:
        raise RuntimeError("Java login response is missing token or tenant identity")

    upsert_runtime_incident(tenant_id, incident_id)
    try:
        diagnosis = _response_data(
            request_json(
                f"{server_url}/api/incidents/{incident_id}/ai/diagnose",
                method="POST",
                headers={"Authorization": f"Bearer {token}"},
                body={"force": True, "locale": "zh-CN"},
            ),
            "Java AI diagnosis",
        )
        _assert_workflow_callbacks_succeeded(diagnosis)
    finally:
        cleanup_runtime_incident(tenant_id, incident_id)


def main() -> None:
    issuer = required_environment("AIOPS_SERVICE_AUTH_ISSUER_URI")
    token_url = os.environ.get(
        "AIOPS_SERVICE_AUTH_SMOKE_TOKEN_URI",
        "http://localhost:8089/realms/aegisops/protocol/openid-connect/token",
    )
    discovery_url = os.environ.get(
        "AIOPS_SERVICE_AUTH_SMOKE_DISCOVERY_URI",
        "http://localhost:8089/realms/aegisops/.well-known/openid-configuration",
    )
    jwks_url = os.environ.get(
        "AIOPS_SERVICE_AUTH_SMOKE_JWKS_URI",
        "http://localhost:8089/realms/aegisops/protocol/openid-connect/certs",
    )
    agent_url = os.environ.get("AIOPS_AGENT_SMOKE_URL", "http://localhost:9008")
    server_url = os.environ.get("AIOPS_SERVER_SMOKE_URL", "http://localhost:8080")

    wait_for_json(discovery_url)
    wait_for_json(jwks_url)

    server_scopes = {"agent:diagnose", "agent:resume", "agent:work-record"}
    worker_scopes = {"agent:diagnose", "agent:work-record"}
    agent_scopes = {
        "evidence:read",
        "cases:read",
        "plugin:authorize",
        "memory:read",
        "memory:write",
        "checkpoint:read",
        "checkpoint:write",
    }
    client_secrets = {
        "server": required_environment("AIOPS_SERVER_OAUTH2_CLIENT_SECRET"),
        "worker": required_environment("AIOPS_WORKER_OAUTH2_CLIENT_SECRET"),
        "agent": required_environment("AIOPS_AGENT_OAUTH2_CLIENT_SECRET"),
    }
    if len(set(client_secrets.values())) != len(client_secrets):
        raise RuntimeError("OAuth2 client secrets must be distinct")
    server_token = exchange_token(
        token_url,
        "aiops-server",
        client_secrets["server"],
        server_scopes,
    )
    worker_token = exchange_token(
        token_url,
        "aiops-worker",
        client_secrets["worker"],
        worker_scopes,
    )
    agent_token = exchange_token(
        token_url,
        "aiops-agent",
        client_secrets["agent"],
        agent_scopes,
    )
    validate_access_token(
        server_token,
        expected_issuer=issuer,
        expected_audience="aiops-agent-api",
        expected_scopes=server_scopes,
    )
    validate_access_token(
        worker_token,
        expected_issuer=issuer,
        expected_audience="aiops-agent-api",
        expected_scopes=worker_scopes,
    )
    validate_access_token(
        agent_token,
        expected_issuer=issuer,
        expected_audience="aegisops-internal-api",
        expected_scopes=agent_scopes,
    )

    tenant_id = "tenant-runtime-smoke"
    incident_id = "incident-runtime-smoke"
    trace_id = "trace-runtime-smoke"
    grant = issue_diagnosis_grant(
        secret=required_environment("AIOPS_DIAGNOSIS_GRANT_SECRET"),
        issuer="aiops-server",
        audience="aegisops-internal-api",
        tenant_id=tenant_id,
        incident_id=incident_id,
        trace_id=trace_id,
    )

    diagnose_response = request_json(
        f"{agent_url}/v1/diagnose",
        method="POST",
        headers={
            "Authorization": f"Bearer {server_token}",
            "X-AegisOps-Diagnosis-Grant": grant,
            "X-AegisOps-Contract-Version": "agent-diagnosis.v1",
        },
        body={
            "contractVersion": "agent-diagnosis.v1",
            "tenantId": tenant_id,
            "incidentId": incident_id,
            "incident": {"id": incident_id, "title": "Runtime authentication smoke"},
            "alerts": [],
            "locale": "zh-CN",
            "traceId": trace_id,
        },
    )
    if diagnose_response.get("contractVersion") != "agent-diagnosis.v1":
        raise RuntimeError("Agent diagnose response has an unexpected contract version")

    work_record_response = request_json(
        f"{agent_url}/v1/work-record/generate",
        method="POST",
        headers={"Authorization": f"Bearer {worker_token}"},
        body=build_work_record_smoke_request(
            tenant_id=tenant_id,
            trace_id=trace_id,
        ),
    )
    if not str(work_record_response.get("markdown") or "").strip():
        raise RuntimeError("Agent work-record response is missing markdown")

    evidence_response = request_json(
        f"{server_url}/internal/agent/evidence/query",
        method="POST",
        headers={
            "Authorization": f"Bearer {agent_token}",
            "X-AegisOps-Diagnosis-Grant": grant,
        },
        body={
            "contractVersion": "agent-diagnosis.v1",
            "tenantId": tenant_id,
            "incidentId": incident_id,
            "traceId": trace_id,
            "primaryAssetId": "asset-runtime-smoke",
            "alertFingerprints": [],
            "alertTitles": [],
            "serviceNames": [],
        },
    )
    for field, expected in (
        ("tenantId", tenant_id),
        ("incidentId", incident_id),
        ("traceId", trace_id),
    ):
        if evidence_response.get(field) != expected:
            raise RuntimeError(f"Java Evidence response has an unexpected {field}")

    verify_java_agent_business_path(
        server_url=server_url,
        username=os.environ.get("AIOPS_SERVICE_AUTH_SMOKE_USERNAME", "admin"),
        password=os.environ.get("AIOPS_SERVICE_AUTH_SMOKE_PASSWORD", "admin123"),
        incident_id="incident-oauth-runtime-smoke",
    )

    print("OAuth2 runtime verification passed for direct and Java/Agent business paths.")


if __name__ == "__main__":
    main()
