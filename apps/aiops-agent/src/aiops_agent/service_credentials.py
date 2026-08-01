"""Outbound OAuth2 Client Credentials with short-lived token caching."""

from __future__ import annotations

import asyncio
import threading
import time

import httpx

from aiops_agent.settings import Settings, settings

_REFRESH_SKEW_SECONDS = 30
_REFRESH_FAILURE_RETRY_SECONDS = 5
_MAX_TOKEN_LIFETIME_SECONDS = 300


class OAuth2ServiceCredentialProvider:
    def __init__(self, current_settings: Settings):
        self.settings = current_settings
        self._access_token = ""
        self._refresh_after = 0.0
        self._expires_at = 0.0
        self._retry_after = 0.0
        self._lock = asyncio.Lock()

    async def headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {await self._token()}"}

    async def _token(self) -> str:
        now = time.time()
        if self._access_token and self._refresh_after > now:
            return self._access_token

        async with self._lock:
            now = time.time()
            if self._access_token and self._refresh_after > now:
                return self._access_token
            if self._retry_after > now:
                if self._access_token and self._expires_at > now:
                    return self._access_token
                raise RuntimeError("OAuth2 token refresh is temporarily unavailable")

            try:
                self._validate()
                async with httpx.AsyncClient(timeout=5.0, trust_env=False) as client:
                    response = await client.post(
                        self.settings.outbound_oauth2_token_url,
                        data={
                            "grant_type": "client_credentials",
                            "scope": self.settings.outbound_oauth2_scope,
                        },
                        auth=httpx.BasicAuth(
                            self.settings.outbound_oauth2_client_id,
                            self.settings.outbound_oauth2_client_secret,
                        ),
                    )
                    response.raise_for_status()
                    payload = response.json()
                access_token, expires_in = _parse_token_response(payload)
            except Exception:
                failure_now = time.time()
                self._retry_after = (
                    failure_now + _REFRESH_FAILURE_RETRY_SECONDS
                )
                if self._access_token and self._expires_at > failure_now:
                    return self._access_token
                raise

            self._access_token = access_token
            self._refresh_after = now + max(1, expires_in - _REFRESH_SKEW_SECONDS)
            self._expires_at = now + expires_in
            self._retry_after = 0.0
            return access_token

    def _validate(self) -> None:
        _validate_oauth2_settings(self.settings)


class SynchronousOAuth2ServiceCredentialProvider:
    """OAuth2 provider for the legacy synchronous evidence client."""

    def __init__(self, current_settings: Settings):
        self.settings = current_settings
        self._access_token = ""
        self._refresh_after = 0.0
        self._expires_at = 0.0
        self._retry_after = 0.0
        self._lock = threading.Lock()

    def headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {self._token()}"}

    def _token(self) -> str:
        now = time.time()
        if self._access_token and self._refresh_after > now:
            return self._access_token

        with self._lock:
            now = time.time()
            if self._access_token and self._refresh_after > now:
                return self._access_token
            if self._retry_after > now:
                if self._access_token and self._expires_at > now:
                    return self._access_token
                raise RuntimeError("OAuth2 token refresh is temporarily unavailable")

            try:
                _validate_oauth2_settings(self.settings)
                with httpx.Client(timeout=5.0, trust_env=False) as client:
                    response = client.post(
                        self.settings.outbound_oauth2_token_url,
                        data={
                            "grant_type": "client_credentials",
                            "scope": self.settings.outbound_oauth2_scope,
                        },
                        auth=httpx.BasicAuth(
                            self.settings.outbound_oauth2_client_id,
                            self.settings.outbound_oauth2_client_secret,
                        ),
                    )
                    response.raise_for_status()
                    payload = response.json()
                access_token, expires_in = _parse_token_response(payload)
            except Exception:
                failure_now = time.time()
                self._retry_after = (
                    failure_now + _REFRESH_FAILURE_RETRY_SECONDS
                )
                if self._access_token and self._expires_at > failure_now:
                    return self._access_token
                raise

            self._access_token = access_token
            self._refresh_after = now + max(1, expires_in - _REFRESH_SKEW_SECONDS)
            self._expires_at = now + expires_in
            self._retry_after = 0.0
            return access_token


def _parse_token_response(payload: object) -> tuple[str, int]:
    if not isinstance(payload, dict):
        raise RuntimeError("OAuth2 token endpoint returned an invalid response")
    access_token = str(payload.get("access_token") or "").strip()
    if not access_token:
        raise RuntimeError("OAuth2 token endpoint returned no access_token")
    try:
        expires_in = max(1, int(payload.get("expires_in") or 60))
    except (TypeError, ValueError) as exc:
        raise RuntimeError("OAuth2 token endpoint returned invalid expires_in") from exc
    if expires_in > _MAX_TOKEN_LIFETIME_SECONDS:
        raise RuntimeError("OAuth2 access token must be short lived (<= 300 seconds)")
    return access_token, expires_in


def _validate_oauth2_settings(current_settings: Settings) -> None:
    required = {
        "outbound_oauth2_token_url": current_settings.outbound_oauth2_token_url,
        "outbound_oauth2_client_id": current_settings.outbound_oauth2_client_id,
        "outbound_oauth2_client_secret": current_settings.outbound_oauth2_client_secret,
    }
    missing = [name for name, value in required.items() if not value or not value.strip()]
    if missing:
        raise RuntimeError(f"Missing OAuth2 service credential setting: {missing[0]}")


_oauth2_provider = OAuth2ServiceCredentialProvider(settings)
_synchronous_oauth2_provider = SynchronousOAuth2ServiceCredentialProvider(settings)


async def service_credential_headers(current_settings: Settings) -> dict[str, str]:
    provider = (
        _oauth2_provider
        if current_settings is settings
        else OAuth2ServiceCredentialProvider(current_settings)
    )
    return await provider.headers()


def synchronous_service_credential_headers(current_settings: Settings) -> dict[str, str]:
    provider = (
        _synchronous_oauth2_provider
        if current_settings is settings
        else SynchronousOAuth2ServiceCredentialProvider(current_settings)
    )
    return provider.headers()
