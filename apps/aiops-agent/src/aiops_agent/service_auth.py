"""Inbound service authentication for Java-to-Agent calls."""

from __future__ import annotations

import asyncio
import secrets
from dataclasses import dataclass
from typing import Protocol

import jwt
from fastapi import HTTPException, status

from aiops_agent.settings import Settings


@dataclass(frozen=True)
class ServicePrincipal:
    service_id: str
    scopes: frozenset[str]


class ServiceJwtDecoder(Protocol):
    async def decode(self, token: str) -> dict[str, object]: ...


class PyJwkServiceJwtDecoder:
    def __init__(self, current_settings: Settings):
        self.settings = current_settings
        self.jwk_client = jwt.PyJWKClient(current_settings.inbound_oauth2_jwks_url)

    async def decode(self, token: str) -> dict[str, object]:
        signing_key = await asyncio.to_thread(self.jwk_client.get_signing_key_from_jwt, token)
        return jwt.decode(
            token,
            signing_key.key,
            algorithms=["RS256"],
            audience=self.settings.inbound_oauth2_audience,
            issuer=self.settings.inbound_oauth2_issuer,
            options={"require": ["exp", "sub"]},
        )


class ServiceAuthenticator:
    def __init__(
        self,
        current_settings: Settings,
        decoder: ServiceJwtDecoder | None = None,
    ):
        self.settings = current_settings
        mode = (current_settings.inbound_auth_mode or "static").strip().lower()
        self.decoder = (
            decoder
            if decoder is not None
            else PyJwkServiceJwtDecoder(current_settings)
            if mode == "oauth2" and current_settings.inbound_oauth2_jwks_url
            else None
        )

    async def authenticate(
        self,
        authorization: str | None,
        static_token: str | None,
        required_scope: str,
    ) -> ServicePrincipal:
        mode = (self.settings.inbound_auth_mode or "static").strip().lower()
        if mode == "static":
            expected = self.settings.internal_agent_token
            if not static_token or not secrets.compare_digest(static_token, expected):
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="invalid internal service credentials",
                )
            return ServicePrincipal("static:java", frozenset({"agent:*"}))

        if mode != "oauth2":
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="unsupported service authentication mode",
            )
        if not authorization or not authorization.startswith("Bearer "):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="bearer service token is required",
            )

        try:
            decoder = self.decoder or PyJwkServiceJwtDecoder(self.settings)
            claims = await decoder.decode(authorization.removeprefix("Bearer ").strip())
        except HTTPException:
            raise
        except Exception as exc:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="invalid service token",
            ) from exc

        audiences = _claim_values(claims.get("aud"))
        if self.settings.inbound_oauth2_audience not in audiences:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="invalid service token audience",
            )

        scopes = _scopes(claims)
        if required_scope not in scopes:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"missing required service scope: {required_scope}",
            )

        subject = str(claims.get("sub") or "").strip()
        if not subject:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="service token subject is required",
            )
        return ServicePrincipal(subject, frozenset(scopes))


def _claim_values(value: object) -> set[str]:
    if isinstance(value, str):
        return {value}
    if isinstance(value, list):
        return {str(item) for item in value}
    return set()


def _scopes(claims: dict[str, object]) -> set[str]:
    result: set[str] = set()
    scope = claims.get("scope")
    if isinstance(scope, str):
        result.update(scope.split())
    result.update(_claim_values(claims.get("scp")))
    return result
