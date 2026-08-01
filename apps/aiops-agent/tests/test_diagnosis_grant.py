from __future__ import annotations

from datetime import datetime, timedelta, timezone

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from aiops_agent.diagnosis_grant import DiagnosisGrantError, DiagnosisGrantVerifier
from aiops_agent.settings import Settings


def test_verifies_signature_audience_scope_and_request_context() -> None:
    private_key = Ed25519PrivateKey.generate()
    verifier = DiagnosisGrantVerifier(
        Settings(), verification_keys={"task-grant-v1": private_key.public_key()}
    )
    token = grant(private_key)

    claims = verifier.verify(
        token,
        required_scope="diagnosis:execute",
        tenant_id="tenant_1",
        incident_id="inc_1",
        diagnosis_id="diag_1",
        trace_id="trace_1",
    )

    assert claims["sub"] == "diagnosis:diag_1"
    assert claims["jti"] == "grant_1"


def test_rejects_wrong_key_expired_audience_scope_and_resource_context() -> None:
    private_key = Ed25519PrivateKey.generate()
    other_key = Ed25519PrivateKey.generate()
    verifier = DiagnosisGrantVerifier(
        Settings(), verification_keys={"task-grant-v1": other_key.public_key()}
    )
    with pytest.raises(DiagnosisGrantError, match="signature"):
        verifier.verify(grant(private_key), required_scope="diagnosis:execute")

    verifier = DiagnosisGrantVerifier(
        Settings(), verification_keys={"task-grant-v1": private_key.public_key()}
    )
    with pytest.raises(DiagnosisGrantError, match="expired"):
        verifier.verify(grant(private_key, expired=True), required_scope="diagnosis:execute")
    with pytest.raises(DiagnosisGrantError, match="audience"):
        verifier.verify(
            grant(private_key, audiences=["other-api"]),
            required_scope="diagnosis:execute",
        )
    with pytest.raises(DiagnosisGrantError, match="scope"):
        verifier.verify(grant(private_key), required_scope="memory:write")
    with pytest.raises(DiagnosisGrantError, match="context"):
        verifier.verify(
            grant(private_key),
            required_scope="diagnosis:execute",
            tenant_id="tenant_2",
        )


def grant(
    private_key: Ed25519PrivateKey,
    *,
    expired: bool = False,
    audiences: list[str] | None = None,
) -> str:
    now = datetime.now(timezone.utc)
    issued_at = now - timedelta(minutes=10) if expired else now
    expires_at = issued_at + timedelta(minutes=5)
    return jwt.encode(
        {
            "iss": "aegisops-app",
            "aud": audiences or ["aiops-agent-api", "aegisops-internal-api"],
            "sub": "diagnosis:diag_1",
            "scope": ["diagnosis:execute", "evidence:read"],
            "tenantId": "tenant_1",
            "incidentId": "inc_1",
            "diagnosisId": "diag_1",
            "traceId": "trace_1",
            "jti": "grant_1",
            "iat": int(issued_at.timestamp()),
            "exp": int(expires_at.timestamp()),
        },
        private_key,
        algorithm="EdDSA",
        headers={"kid": "task-grant-v1", "typ": "JWT"},
    )
