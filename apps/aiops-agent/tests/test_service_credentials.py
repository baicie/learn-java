from __future__ import annotations

import httpx
import pytest
import respx

from aiops_agent.service_credentials import (
    OAuth2ServiceCredentialProvider,
    SynchronousOAuth2ServiceCredentialProvider,
)
from aiops_agent.settings import Settings

TOKEN_URL = "https://idp.example.com/realms/aegisops/token"


def oauth_settings() -> Settings:
    return Settings(
        outbound_oauth2_token_url=TOKEN_URL,
        outbound_oauth2_client_id="aiops-agent",
        outbound_oauth2_client_secret="client-secret",
        outbound_oauth2_scope="evidence:read",
    )


def token_response(access_token: str, expires_in: int) -> httpx.Response:
    return httpx.Response(
        200,
        json={
            "access_token": access_token,
            "token_type": "Bearer",
            "expires_in": expires_in,
        },
    )


@pytest.mark.asyncio
@respx.mock
async def test_client_credentials_provider_fetches_and_caches_access_token():
    route = respx.post(TOKEN_URL).mock(
        return_value=token_response("agent-service-token", 300)
    )
    test_settings = Settings(
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
    route = respx.post(TOKEN_URL).mock(
        return_value=token_response("legacy-evidence-token", 300)
    )
    test_settings = Settings(
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


@pytest.mark.asyncio
@respx.mock
async def test_async_provider_reuses_unexpired_token_and_suppresses_failed_refresh(
    monkeypatch,
):
    now = [1_000.0]
    monkeypatch.setattr(
        "aiops_agent.service_credentials.time.time", lambda: now[0]
    )
    route = respx.post(TOKEN_URL).mock(
        side_effect=[
            token_response("still-valid-token", 60),
            httpx.Response(503),
        ]
    )
    provider = OAuth2ServiceCredentialProvider(oauth_settings())

    initial = await provider.headers()
    now[0] += 31
    fallback = await provider.headers()
    suppressed_retry = await provider.headers()

    assert initial["Authorization"] == "Bearer still-valid-token"
    assert fallback["Authorization"] == "Bearer still-valid-token"
    assert suppressed_retry["Authorization"] == "Bearer still-valid-token"
    assert route.call_count == 2


@pytest.mark.asyncio
@respx.mock
async def test_async_provider_never_reuses_hard_expired_token(monkeypatch):
    now = [1_000.0]
    monkeypatch.setattr(
        "aiops_agent.service_credentials.time.time", lambda: now[0]
    )
    route = respx.post(TOKEN_URL).mock(
        side_effect=[
            token_response("short-lived-token", 40),
            httpx.Response(503),
        ]
    )
    provider = OAuth2ServiceCredentialProvider(oauth_settings())

    await provider.headers()
    now[0] += 41

    with pytest.raises(httpx.HTTPStatusError):
        await provider.headers()
    assert route.call_count == 2


@pytest.mark.asyncio
@respx.mock
async def test_async_provider_rechecks_expiry_after_slow_refresh_failure(monkeypatch):
    now = [1_000.0]
    calls = [0]
    monkeypatch.setattr(
        "aiops_agent.service_credentials.time.time", lambda: now[0]
    )

    def respond(_request):
        calls[0] += 1
        if calls[0] == 1:
            return token_response("two-second-token", 2)
        now[0] = 1_002.1
        return httpx.Response(503)

    route = respx.post(TOKEN_URL).mock(side_effect=respond)
    provider = OAuth2ServiceCredentialProvider(oauth_settings())

    await provider.headers()
    now[0] = 1_001.1

    with pytest.raises(httpx.HTTPStatusError):
        await provider.headers()
    now[0] = 1_002.2
    with pytest.raises(RuntimeError, match="temporarily unavailable"):
        await provider.headers()
    assert route.call_count == 2


@pytest.mark.asyncio
@respx.mock
async def test_async_provider_rejects_token_lifetime_over_five_minutes():
    respx.post(TOKEN_URL).mock(return_value=token_response("long-lived-token", 301))
    provider = OAuth2ServiceCredentialProvider(oauth_settings())

    with pytest.raises(RuntimeError, match="short lived"):
        await provider.headers()


@respx.mock
def test_sync_provider_reuses_unexpired_token_and_suppresses_failed_refresh(
    monkeypatch,
):
    now = [1_000.0]
    monkeypatch.setattr(
        "aiops_agent.service_credentials.time.time", lambda: now[0]
    )
    route = respx.post(TOKEN_URL).mock(
        side_effect=[
            token_response("still-valid-token", 60),
            httpx.Response(503),
        ]
    )
    provider = SynchronousOAuth2ServiceCredentialProvider(oauth_settings())

    initial = provider.headers()
    now[0] += 31
    fallback = provider.headers()
    suppressed_retry = provider.headers()

    assert initial["Authorization"] == "Bearer still-valid-token"
    assert fallback["Authorization"] == "Bearer still-valid-token"
    assert suppressed_retry["Authorization"] == "Bearer still-valid-token"
    assert route.call_count == 2


@respx.mock
def test_sync_provider_never_reuses_hard_expired_token(monkeypatch):
    now = [1_000.0]
    monkeypatch.setattr(
        "aiops_agent.service_credentials.time.time", lambda: now[0]
    )
    route = respx.post(TOKEN_URL).mock(
        side_effect=[
            token_response("short-lived-token", 40),
            httpx.Response(503),
        ]
    )
    provider = SynchronousOAuth2ServiceCredentialProvider(oauth_settings())

    provider.headers()
    now[0] += 41

    with pytest.raises(httpx.HTTPStatusError):
        provider.headers()
    assert route.call_count == 2


@respx.mock
def test_sync_provider_rechecks_expiry_after_slow_refresh_failure(monkeypatch):
    now = [1_000.0]
    calls = [0]
    monkeypatch.setattr(
        "aiops_agent.service_credentials.time.time", lambda: now[0]
    )

    def respond(_request):
        calls[0] += 1
        if calls[0] == 1:
            return token_response("two-second-token", 2)
        now[0] = 1_002.1
        return httpx.Response(503)

    route = respx.post(TOKEN_URL).mock(side_effect=respond)
    provider = SynchronousOAuth2ServiceCredentialProvider(oauth_settings())

    provider.headers()
    now[0] = 1_001.1

    with pytest.raises(httpx.HTTPStatusError):
        provider.headers()
    now[0] = 1_002.2
    with pytest.raises(RuntimeError, match="temporarily unavailable"):
        provider.headers()
    assert route.call_count == 2


@respx.mock
def test_sync_provider_rejects_token_lifetime_over_five_minutes():
    respx.post(TOKEN_URL).mock(return_value=token_response("long-lived-token", 301))
    provider = SynchronousOAuth2ServiceCredentialProvider(oauth_settings())

    with pytest.raises(RuntimeError, match="short lived"):
        provider.headers()
