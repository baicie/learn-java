from __future__ import annotations

import pytest
from fastapi import HTTPException

from aiops_agent.service_auth import ServiceAuthenticator
from aiops_agent.settings import Settings


class FakeDecoder:
    def __init__(self, claims: dict[str, object]):
        self.claims = claims

    async def decode(self, token: str) -> dict[str, object]:
        assert token == "service-token"
        return self.claims


@pytest.mark.asyncio
async def test_oauth2_authenticates_expected_scope_and_audience():
    test_settings = Settings(
        inbound_auth_mode="oauth2",
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
            }
        ),
    )

    principal = await authenticator.authenticate(
        authorization="Bearer service-token",
        static_token=None,
        required_scope="agent:diagnose",
    )

    assert principal.service_id == "svc:aiops-server"
    assert "agent:diagnose" in principal.scopes


@pytest.mark.asyncio
async def test_oauth2_rejects_missing_endpoint_scope():
    test_settings = Settings(inbound_auth_mode="oauth2")
    authenticator = ServiceAuthenticator(
        test_settings,
        FakeDecoder(
            {
                "sub": "svc:aiops-worker",
                "aud": ["aiops-agent-api"],
                "scope": "agent:diagnose",
            }
        ),
    )

    with pytest.raises(HTTPException) as error:
        await authenticator.authenticate(
            authorization="Bearer service-token",
            static_token=None,
            required_scope="agent:work-record",
        )

    assert error.value.status_code == 403


@pytest.mark.asyncio
async def test_static_mode_uses_constant_time_compatible_token_check():
    test_settings = Settings(
        inbound_auth_mode="static",
        internal_agent_token="static-secret",
    )
    authenticator = ServiceAuthenticator(test_settings)

    principal = await authenticator.authenticate(
        authorization=None,
        static_token="static-secret",
        required_scope="agent:diagnose",
    )

    assert principal.service_id == "static:java"
