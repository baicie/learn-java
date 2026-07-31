#!/usr/bin/env python3
"""Run a non-destructive OAuth2 and Diagnosis Grant production probe."""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from typing import Any


MAX_TOKEN_TTL_SECONDS = 300
MAX_FUTURE_IAT_SECONDS = 30
MAX_REQUEST_ATTEMPTS = 3
RETRY_BACKOFF_SECONDS = 1.0
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
    if not isinstance(issued_at, int) or not isinstance(expires_at, int):
        raise RuntimeError("Access token must contain numeric iat and exp")
    current_time = int(time.time()) if now is None else now
    if expires_at <= current_time:
        raise RuntimeError("Access token is expired")
    if issued_at > current_time + MAX_FUTURE_IAT_SECONDS:
        raise RuntimeError("Access token iat is too far in the future")
    if expires_at <= issued_at or expires_at - issued_at > MAX_TOKEN_TTL_SECONDS:
        raise RuntimeError("Access token is not short lived")
    scopes = set(str(claims.get("scope") or "").split())
    if expected_scopes - scopes:
        raise RuntimeError("Access token is missing required scopes")
    if (scopes & AEGISOPS_SERVICE_SCOPES) - expected_scopes:
        raise RuntimeError("Access token contains unexpected AegisOps scopes")
    return claims


def validate_client_identity(
    claims: dict[str, Any], *, expected_client_id: str
) -> str:
    subject = str(claims.get("sub") or "").strip()
    client_identities = [
        value.strip()
        for claim_name in ("azp", "client_id")
        if isinstance((value := claims.get(claim_name)), str) and value.strip()
    ]
    if not client_identities or any(
        value != expected_client_id for value in client_identities
    ):
        raise RuntimeError("Access token has an unexpected client identity")
    return subject


def request_json(
    url: str,
    *,
    method: str = "GET",
    headers: dict[str, str] | None = None,
    body: dict[str, Any] | None = None,
    form: dict[str, str] | None = None,
    timeout: float = 10.0,
) -> dict[str, Any]:
    request = build_request(
        url,
        method=method,
        headers=headers,
        body=body,
        form=form,
    )
    payload = b""
    for attempt in range(1, MAX_REQUEST_ATTEMPTS + 1):
        try:
            with DIRECT_HTTP_OPENER.open(request, timeout=timeout) as response:
                payload = response.read()
            break
        except urllib.error.HTTPError as exc:
            if not is_retryable_status(exc.code) or attempt == MAX_REQUEST_ATTEMPTS:
                raise RuntimeError(
                    f"{method} {url} returned HTTP {exc.code}"
                ) from exc
        except urllib.error.URLError as exc:
            if attempt == MAX_REQUEST_ATTEMPTS:
                raise RuntimeError(f"{method} {url} failed") from exc
        time.sleep(RETRY_BACKOFF_SECONDS * attempt)
    try:
        value = json.loads(payload)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"{method} {url} returned invalid JSON") from exc
    if not isinstance(value, dict):
        raise RuntimeError(f"{method} {url} returned a non-object JSON response")
    return value


def build_request(
    url: str,
    *,
    method: str,
    headers: dict[str, str] | None,
    body: dict[str, Any] | None,
    form: dict[str, str] | None = None,
) -> urllib.request.Request:
    request_headers = dict(headers or {})
    data = None
    if body is not None:
        request_headers["Content-Type"] = "application/json"
        data = json.dumps(body, separators=(",", ":")).encode("utf-8")
    elif form is not None:
        request_headers["Content-Type"] = "application/x-www-form-urlencoded"
        data = urllib.parse.urlencode(form).encode("ascii")
    return urllib.request.Request(
        url, data=data, headers=request_headers, method=method
    )


def is_retryable_status(status_code: int) -> bool:
    return status_code in {408, 429} or 500 <= status_code < 600


def expect_http_status(
    url: str,
    *,
    expected_status: int,
    method: str = "GET",
    headers: dict[str, str] | None = None,
    body: dict[str, Any] | None = None,
    timeout: float = 10.0,
) -> None:
    request = build_request(
        url,
        method=method,
        headers=headers,
        body=body,
    )
    for attempt in range(1, MAX_REQUEST_ATTEMPTS + 1):
        try:
            with DIRECT_HTTP_OPENER.open(request, timeout=timeout):
                pass
        except urllib.error.HTTPError as exc:
            if exc.code == expected_status:
                return
            if not is_retryable_status(exc.code) or attempt == MAX_REQUEST_ATTEMPTS:
                raise RuntimeError(
                    f"{method} {url} returned HTTP {exc.code}; expected {expected_status}"
                ) from exc
        except urllib.error.URLError as exc:
            if attempt == MAX_REQUEST_ATTEMPTS:
                raise RuntimeError(f"{method} {url} failed") from exc
        else:
            raise RuntimeError(
                f"{method} {url} succeeded; expected HTTP {expected_status}"
            )
        time.sleep(RETRY_BACKOFF_SECONDS * attempt)


def required_environment(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"{name} is required")
    return value


def exchange_token(
    token_url: str,
    client_id: str,
    client_secret: str,
    scopes: set[str],
) -> str:
    basic = base64.b64encode(f"{client_id}:{client_secret}".encode("utf-8")).decode(
        "ascii"
    )
    response = request_json(
        token_url,
        method="POST",
        headers={"Authorization": f"Basic {basic}"},
        form={
            "grant_type": "client_credentials",
            "scope": " ".join(sorted(scopes)),
        },
    )
    token = str(response.get("access_token") or "").strip()
    if not token:
        raise RuntimeError(f"Token endpoint returned no access_token for {client_id}")
    return token


def issue_diagnosis_grant(
    *,
    secret: str,
    issuer: str,
    audience: str,
    tenant_id: str,
    incident_id: str,
    trace_id: str,
    now: int | None = None,
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
            "exp": issued_at + MAX_TOKEN_TTL_SECONDS,
            "jti": str(uuid.uuid4()),
        }
    )
    signing_input = f"{header}.{payload}"
    signature = hmac.new(
        secret.encode("utf-8"), signing_input.encode("ascii"), hashlib.sha256
    ).digest()
    encoded_signature = (
        base64.urlsafe_b64encode(signature).rstrip(b"=").decode("ascii")
    )
    return f"{signing_input}.{encoded_signature}"


def assert_probe_response(response: dict[str, Any], expected: dict[str, str]) -> None:
    if response.get("ok") is not True:
        raise RuntimeError("Service authentication probe returned ok=false")
    for field, value in expected.items():
        if response.get(field) != value:
            raise RuntimeError(f"Service authentication probe returned invalid {field}")


def main() -> None:
    issuer = required_environment("AIOPS_SERVICE_AUTH_ISSUER_URI")
    jwks_url = required_environment("AIOPS_SERVICE_AUTH_JWK_SET_URI")
    token_url = required_environment("AIOPS_SERVICE_AUTH_TOKEN_URI")
    agent_url = os.environ.get("AIOPS_AGENT_PROBE_URL", "http://localhost:9008").rstrip(
        "/"
    )
    server_url = os.environ.get(
        "AIOPS_SERVER_PROBE_URL", "http://localhost:8080"
    ).rstrip("/")
    jwks = request_json(jwks_url)
    if not isinstance(jwks.get("keys"), list) or not jwks["keys"]:
        raise RuntimeError("JWKS endpoint returned no signing keys")

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
    client_ids = {
        "server": os.environ.get("AIOPS_SERVER_OAUTH2_CLIENT_ID", "aiops-server"),
        "worker": os.environ.get("AIOPS_WORKER_OAUTH2_CLIENT_ID", "aiops-worker"),
        "agent": os.environ.get("AIOPS_AGENT_OAUTH2_CLIENT_ID", "aiops-agent"),
    }
    client_secrets = {
        "server": required_environment("AIOPS_SERVER_OAUTH2_CLIENT_SECRET"),
        "worker": required_environment("AIOPS_WORKER_OAUTH2_CLIENT_SECRET"),
        "agent": required_environment("AIOPS_AGENT_OAUTH2_CLIENT_SECRET"),
    }
    if len(set(client_secrets.values())) != len(client_secrets):
        raise RuntimeError("OAuth2 client secrets must be distinct")
    tokens = {
        "server": exchange_token(
            token_url,
            client_ids["server"],
            client_secrets["server"],
            server_scopes,
        ),
        "worker": exchange_token(
            token_url,
            client_ids["worker"],
            client_secrets["worker"],
            worker_scopes,
        ),
        "agent": exchange_token(
            token_url,
            client_ids["agent"],
            client_secrets["agent"],
            agent_scopes,
        ),
    }
    claims = {
        "server": validate_access_token(
            tokens["server"],
            expected_issuer=issuer,
            expected_audience="aiops-agent-api",
            expected_scopes=server_scopes,
        ),
        "worker": validate_access_token(
            tokens["worker"],
            expected_issuer=issuer,
            expected_audience="aiops-agent-api",
            expected_scopes=worker_scopes,
        ),
        "agent": validate_access_token(
            tokens["agent"],
            expected_issuer=issuer,
            expected_audience="aegisops-internal-api",
            expected_scopes=agent_scopes,
        ),
    }
    subjects = {
        caller: validate_client_identity(
            claims[caller], expected_client_id=client_ids[caller]
        )
        for caller in ("server", "worker", "agent")
    }
    if len(set(subjects.values())) != len(subjects):
        raise RuntimeError("OAuth2 service token subjects must be distinct")

    for caller in ("server", "worker"):
        response = request_json(
            f"{agent_url}/v1/auth/probe",
            method="POST",
            headers={"Authorization": f"Bearer {tokens[caller]}"},
            body={},
        )
        if (
            response.get("ok") is not True
            or response.get("serviceId") != subjects[caller]
        ):
            raise RuntimeError(f"Agent returned an invalid {caller} service identity")

    suffix = uuid.uuid4().hex
    tenant_id = f"tenant-auth-probe-{suffix}"
    incident_id = f"incident-auth-probe-{suffix}"
    trace_id = f"trace-auth-probe-{suffix}"
    grant_secret = required_environment("AIOPS_DIAGNOSIS_GRANT_SECRET")
    grant = issue_diagnosis_grant(
        secret=grant_secret,
        issuer="aiops-server",
        audience="aegisops-internal-api",
        tenant_id=tenant_id,
        incident_id=incident_id,
        trace_id=trace_id,
    )

    diagnose_body = {
        "contractVersion": "agent-diagnosis.v1",
        "tenantId": tenant_id,
        "incidentId": incident_id,
        "incident": {"id": incident_id},
        "alerts": [],
        "locale": "zh-CN",
        "traceId": trace_id,
    }
    expect_http_status(
        f"{agent_url}/v1/diagnose",
        expected_status=401,
        method="POST",
        headers={
            "Authorization": f"Bearer {tokens['server']}",
            "X-AegisOps-Contract-Version": "agent-diagnosis.v1",
        },
        body=diagnose_body,
    )

    java_auth_headers = {"Authorization": f"Bearer {tokens['agent']}"}
    expect_http_status(
        f"{server_url}/internal/agent/auth/probe",
        expected_status=401,
        method="POST",
        headers=java_auth_headers,
        body={},
    )
    grant_parts = grant.split(".")
    grant_parts[2] = ("A" if grant_parts[2][0] != "A" else "B") + grant_parts[2][1:]
    tampered_grant = ".".join(grant_parts)
    expired_grant = issue_diagnosis_grant(
        secret=grant_secret,
        issuer="aiops-server",
        audience="aegisops-internal-api",
        tenant_id=tenant_id,
        incident_id=incident_id,
        trace_id=trace_id,
        now=int(time.time()) - MAX_TOKEN_TTL_SECONDS - 1,
    )
    wrong_audience_grant = issue_diagnosis_grant(
        secret=grant_secret,
        issuer="aiops-server",
        audience="wrong-internal-api",
        tenant_id=tenant_id,
        incident_id=incident_id,
        trace_id=trace_id,
    )
    for invalid_grant in (tampered_grant, expired_grant, wrong_audience_grant):
        expect_http_status(
            f"{server_url}/internal/agent/auth/probe",
            expected_status=401,
            method="POST",
            headers={
                **java_auth_headers,
                "X-AegisOps-Diagnosis-Grant": invalid_grant,
            },
            body={},
        )

    java_response = request_json(
        f"{server_url}/internal/agent/auth/probe",
        method="POST",
        headers={
            "Authorization": f"Bearer {tokens['agent']}",
            "X-AegisOps-Diagnosis-Grant": grant,
        },
        body={},
    )
    assert_probe_response(
        java_response,
        {
            "serviceId": subjects["agent"],
            "tenantId": tenant_id,
            "incidentId": incident_id,
            "traceId": trace_id,
        },
    )
    expect_http_status(
        f"{server_url}/internal/agent/evidence/query",
        expected_status=403,
        method="POST",
        headers={
            **java_auth_headers,
            "X-AegisOps-Diagnosis-Grant": grant,
        },
        body={
            "contractVersion": "agent-diagnosis.v1",
            "tenantId": tenant_id,
            "incidentId": f"{incident_id}-mismatch",
            "traceId": trace_id,
            "alertFingerprints": [],
            "alertTitles": [],
            "serviceNames": [],
        },
    )
    print(
        "OAuth2 service authentication probe passed "
        "(clients=server,worker,agent; maxTtlSeconds=300)."
    )


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"Service authentication probe failed: {exc}", file=sys.stderr)
        raise SystemExit(1) from None
