from __future__ import annotations

import httpx
import pytest
import respx

from aiops_agent.service_credentials import (
    OAuth2ServiceCredentialProvider,
    SynchronousOAuth2ServiceCredentialProvider,
)
from aiops_agent.settings import Settings


@pytest.mark.asyncio
@respx.mock
async def test_client_credentials_provider_fetches_and_caches_access_token():
    route = respx.post("https://idp.example.com/realms/aegisops/token").mock(
        return_value=httpx.Response(
            200,
            json={
                "access_token": "agent-service-token",
                "token_type": "Bearer",
                "expires_in": 300,
            },
        )
    )
    test_settings = Settings(
        outbound_auth_mode="oauth2",
        outbound_oauth2_token_url="https://idp.example.com/realms/aegisops/token",
        outbound_oauth2_client_id="aiops-agent",
        outbound_oauth2_client_secret="client-secret",
        outbound_oauth2_scope=(
            "evidence:read cases:read checkpoint:read checkpoint:write "
            "memory:read memory:write plugin:authorize"
        ),
    )
    provider = OAuth2ServiceCredentialProvider(test_settings)

    first = await provider.headers()
    second = await provider.headers()

    assert first["Authorization"] == "Bearer agent-service-token"
    assert second["Authorization"] == "Bearer agent-service-token"
    assert route.call_count == 1
    request = route.calls[0].request
    assert request.headers["Authorization"].startswith("Basic ")
    assert b"grant_type=client_credentials" in request.content


@respx.mock
def test_synchronous_client_credentials_provider_fetches_and_caches_access_token():
    route = respx.post("https://idp.example.com/realms/aegisops/token").mock(
        return_value=httpx.Response(
            200,
            json={
                "access_token": "legacy-evidence-token",
                "token_type": "Bearer",
                "expires_in": 300,
            },
        )
    )
    test_settings = Settings(
        outbound_auth_mode="oauth2",
        outbound_oauth2_token_url="https://idp.example.com/realms/aegisops/token",
        outbound_oauth2_client_id="aiops-agent",
        outbound_oauth2_client_secret="client-secret",
        outbound_oauth2_scope="evidence:read",
    )
    provider = SynchronousOAuth2ServiceCredentialProvider(test_settings)

    first = provider.headers()
    second = provider.headers()

    assert first["Authorization"] == "Bearer legacy-evidence-token"
    assert second["Authorization"] == "Bearer legacy-evidence-token"
    assert route.call_count == 1
