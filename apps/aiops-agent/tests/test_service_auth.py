from __future__ import annotations

import logging
import time
from types import SimpleNamespace

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa
from fastapi import HTTPException

from aiops_agent.service_auth import PyJwkServiceJwtDecoder, ServiceAuthenticator
from aiops_agent.settings import Settings


class FakeDecoder:
    def __init__(self, claims: dict[str, object]):
        self.claims = claims

    async def decode(self, token: str) -> dict[str, object]:
        assert token == "service-token"
        return self.claims


class FailingDecoder:
    async def decode(self, token: str) -> dict[str, object]:
        raise jwt.PyJWKClientConnectionError("identity provider unavailable")


def signed_service_token(
    settings: Settings,
    *,
    issued_at: int,
    expires_at: int,
) -> tuple[str, object]:
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    token = jwt.encode(
        {
            "iss": settings.inbound_oauth2_issuer,
            "aud": settings.inbound_oauth2_audience,
            "sub": "svc:aiops-server",
            "scope": "agent:diagnose",
            "iat": issued_at,
            "exp": expires_at,
        },
        private_key,
        algorithm="RS256",
    )
    return token, private_key.public_key()


def authenticator_with_signing_key(
    monkeypatch: pytest.MonkeyPatch,
    settings: Settings,
    public_key: object,
) -> ServiceAuthenticator:
    authenticator = ServiceAuthenticator(settings)
    assert isinstance(authenticator.decoder, PyJwkServiceJwtDecoder)
    monkeypatch.setattr(
        authenticator.decoder.jwk_client,
        "get_signing_key_from_jwt",
        lambda token: SimpleNamespace(key=public_key),
    )
    return authenticator


@pytest.mark.asyncio
async def test_oauth2_accepts_issued_at_within_thirty_second_clock_skew(monkeypatch):
    test_settings = Settings()
    now = int(time.time())
    token, public_key = signed_service_token(
        test_settings,
        issued_at=now + 10,
        expires_at=now + 299,
    )
    authenticator = authenticator_with_signing_key(
        monkeypatch,
        test_settings,
        public_key,
    )

    principal = await authenticator.authenticate(
        authorization=f"Bearer {token}",
        required_scope="agent:diagnose",
    )

    assert principal.service_id == "svc:aiops-server"


@pytest.mark.asyncio
async def test_oauth2_rejects_issued_at_beyond_thirty_second_clock_skew(monkeypatch):
    test_settings = Settings()
    now = int(time.time())
    token, public_key = signed_service_token(
        test_settings,
        issued_at=now + 60,
        expires_at=now + 299,
    )
    authenticator = authenticator_with_signing_key(
        monkeypatch,
        test_settings,
        public_key,
    )

    with pytest.raises(HTTPException) as error:
        await authenticator.authenticate(
            authorization=f"Bearer {token}",
            required_scope="agent:diagnose",
        )

    assert error.value.status_code == 401
    assert error.value.detail == "service token issued too far in the future"


@pytest.mark.asyncio
async def test_oauth2_clock_skew_does_not_extend_expiration(monkeypatch):
    test_settings = Settings()
    now = int(time.time())
    token, public_key = signed_service_token(
        test_settings,
        issued_at=now - 60,
        expires_at=now - 1,
    )
    authenticator = authenticator_with_signing_key(
        monkeypatch,
        test_settings,
        public_key,
    )

    with pytest.raises(HTTPException) as error:
        await authenticator.authenticate(
            authorization=f"Bearer {token}",
            required_scope="agent:diagnose",
        )

    assert error.value.status_code == 401
    assert error.value.detail == "invalid service token"


@pytest.mark.asyncio
async def test_oauth2_authenticates_expected_scope_and_audience():
    test_settings = Settings(
        inbound_oauth2_issuer="https://idp.example.com/realms/aegisops",
        inbound_oauth2_jwks_url="https://idp.example.com/realms/aegisops/certs",
        inbound_oauth2_audience="aiops-agent-api",
    )
    authenticator = ServiceAuthenticator(
        test_settings,
        FakeDecoder(
            {
                "sub": "svc:aiops-server",
                "aud": ["aiops-agent-api"],
                "scope": "agent:diagnose agent:resume",
                "iat": 1_000,
                "exp": 1_300,
            }
        ),
    )

    principal = await authenticator.authenticate(
        authorization="Bearer service-token",
        required_scope="agent:diagnose",
    )

    assert principal.service_id == "svc:aiops-server"
    assert "agent:diagnose" in principal.scopes


@pytest.mark.asyncio
@pytest.mark.parametrize("scheme", ["bearer", "BEARER"])
async def test_oauth2_accepts_case_insensitive_bearer_scheme(scheme: str):
    authenticator = ServiceAuthenticator(
        Settings(),
        FakeDecoder(
            {
                "sub": "svc:aiops-server",
                "aud": ["aiops-agent-api"],
                "scope": "agent:diagnose",
                "iat": 1_000,
                "exp": 1_300,
            }
        ),
    )

    principal = await authenticator.authenticate(
        authorization=f"{scheme} service-token",
        required_scope="agent:diagnose",
    )

    assert principal.service_id == "svc:aiops-server"


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "authorization",
    ["Basic service-token", "Bearer", "Bearer ", "Bearer\tservice-token"],
)
async def test_oauth2_rejects_malformed_authorization_format(authorization: str):
    authenticator = ServiceAuthenticator(Settings(), FakeDecoder({}))

    with pytest.raises(HTTPException) as error:
        await authenticator.authenticate(
            authorization=authorization,
            required_scope="agent:diagnose",
        )

    assert error.value.status_code == 401


@pytest.mark.asyncio
async def test_oauth2_rejects_missing_endpoint_scope():
    test_settings = Settings()
    authenticator = ServiceAuthenticator(
        test_settings,
        FakeDecoder(
            {
                "sub": "svc:aiops-worker",
                "aud": ["aiops-agent-api"],
                "scope": "agent:diagnose",
                "iat": 1_000,
                "exp": 1_300,
            }
        ),
    )

    with pytest.raises(HTTPException) as error:
        await authenticator.authenticate(
            authorization="Bearer service-token",
            required_scope="agent:work-record",
        )

    assert error.value.status_code == 403
    assert "agent:work-record" not in str(error.value.detail)


@pytest.mark.asyncio
async def test_oauth2_rejects_token_without_issued_at():
    authenticator = ServiceAuthenticator(
        Settings(),
        FakeDecoder(
            {
                "sub": "svc:aiops-server",
                "aud": ["aiops-agent-api"],
                "scope": "agent:diagnose",
                "exp": 1_300,
            }
        ),
    )

    with pytest.raises(HTTPException) as error:
        await authenticator.authenticate(
            authorization="Bearer service-token",
            required_scope="agent:diagnose",
        )

    assert error.value.status_code == 401


@pytest.mark.asyncio
async def test_oauth2_rejects_token_lifetime_over_five_minutes():
    authenticator = ServiceAuthenticator(
        Settings(),
        FakeDecoder(
            {
                "sub": "svc:aiops-server",
                "aud": ["aiops-agent-api"],
                "scope": "agent:diagnose",
                "iat": 1_000,
                "exp": 1_301,
            }
        ),
    )

    with pytest.raises(HTTPException) as error:
        await authenticator.authenticate(
            authorization="Bearer service-token",
            required_scope="agent:diagnose",
        )

    assert error.value.status_code == 401


@pytest.mark.asyncio
async def test_oauth2_reports_jwks_dependency_failure_as_unavailable():
    authenticator = ServiceAuthenticator(Settings(), FailingDecoder())

    with pytest.raises(HTTPException) as error:
        await authenticator.authenticate(
            authorization="Bearer service-token",
            required_scope="agent:diagnose",
        )

    assert error.value.status_code == 503
    assert error.value.detail == "service authentication is temporarily unavailable"


@pytest.mark.asyncio
async def test_static_header_cannot_authenticate_internal_service_call():
    authenticator = ServiceAuthenticator(Settings())

    with pytest.raises(HTTPException) as error:
        await authenticator.authenticate(
            authorization=None,
            required_scope="agent:diagnose",
        )

    assert error.value.status_code == 401


@pytest.mark.asyncio
@pytest.mark.parametrize(
    (
        "authorization",
        "decoder",
        "required_scope",
        "expected_status",
        "expected_event",
        "expected_severity",
        "expected_level",
        "expected_service_id",
    ),
    [
        (
            "Basic service-token",
            FakeDecoder({}),
            "agent:diagnose",
            401,
            "internal_auth_failed",
            "critical",
            logging.WARNING,
            None,
        ),
        (
            "Bearer service-token",
            FakeDecoder(
                {
                    "sub": "svc:aiops-worker",
                    "aud": ["aiops-agent-api"],
                    "scope": "agent:diagnose",
                    "iat": 1_000,
                    "exp": 1_300,
                }
            ),
            "agent:work-record",
            403,
            "internal_auth_forbidden",
            "critical",
            logging.WARNING,
            "svc:aiops-worker",
        ),
        (
            "Bearer service-token",
            FailingDecoder(),
            "agent:diagnose",
            503,
            "internal_auth_unavailable",
            "high",
            logging.ERROR,
            None,
        ),
    ],
)
async def test_oauth2_rejections_emit_sanitized_security_events(
    caplog,
    authorization,
    decoder,
    required_scope,
    expected_status,
    expected_event,
    expected_severity,
    expected_level,
    expected_service_id,
):
    authenticator = ServiceAuthenticator(Settings(), decoder)

    with caplog.at_level(logging.WARNING, logger="aiops_agent.service_auth"):
        with pytest.raises(HTTPException) as error:
            await authenticator.authenticate(
                authorization=authorization,
                required_scope=required_scope,
            )

    assert error.value.status_code == expected_status
    records = [
        record
        for record in caplog.records
        if getattr(record, "eventType", None) == expected_event
    ]
    assert len(records) == 1
    record = records[0]
    assert record.levelno == expected_level
    assert record.httpStatus == expected_status
    assert record.requiredScope == required_scope
    assert record.severity == expected_severity
    assert getattr(record, "serviceId", None) == expected_service_id
    for forbidden_field in ("token", "authorization", "claims", "diagnosisGrant"):
        assert not hasattr(record, forbidden_field)
    assert "service-token" not in caplog.text
