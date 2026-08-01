"""Inbound service authentication for Java-to-Agent calls."""

from __future__ import annotations

import asyncio
import logging
import time
from dataclasses import dataclass
from typing import Protocol

import jwt
from fastapi import HTTPException, status

from aiops_agent.settings import Settings

MAX_SERVICE_TOKEN_LIFETIME_SECONDS = 300
MAX_SERVICE_TOKEN_CLOCK_SKEW_SECONDS = 30
logger = logging.getLogger(__name__)


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
            options={"require": ["exp", "sub"], "verify_iat": False},
        )


class ServiceAuthenticator:
    def __init__(
        self,
        current_settings: Settings,
        decoder: ServiceJwtDecoder | None = None,
    ):
        self.settings = current_settings
        self.decoder = (
            decoder
            if decoder is not None
            else PyJwkServiceJwtDecoder(current_settings)
        )

    async def authenticate(
        self,
        authorization: str | None,
        required_scope: str,
    ) -> ServicePrincipal:
        verified_service_id: str | None = None
        try:
            scheme, separator, token = (authorization or "").partition(" ")
            if scheme.lower() != "bearer" or not separator or not token.strip():
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="bearer service token is required",
                )

            try:
                claims = await self.decoder.decode(token.strip())
            except HTTPException:
                raise
            except (
                jwt.PyJWKClientConnectionError,
                jwt.PyJWKError,
                jwt.PyJWKSetError,
            ) as exc:
                raise HTTPException(
                    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                    detail="service authentication is temporarily unavailable",
                ) from exc
            except jwt.PyJWTError as exc:
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="invalid service token",
                ) from exc
            except Exception as exc:
                raise HTTPException(
                    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                    detail="service authentication is temporarily unavailable",
                ) from exc

            _validate_token_lifetime(claims)
            audiences = _claim_values(claims.get("aud"))
            if self.settings.inbound_oauth2_audience not in audiences:
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="invalid service token audience",
                )

            subject_claim = claims.get("sub")
            subject = subject_claim.strip() if isinstance(subject_claim, str) else ""
            if not subject:
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="service token subject is required",
                )
            verified_service_id = subject

            scopes = _scopes(claims)
            if required_scope not in scopes:
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail="service is not authorized for this endpoint",
                )

            return ServicePrincipal(subject, frozenset(scopes))
        except HTTPException as exc:
            _log_auth_rejection(exc.status_code, required_scope, verified_service_id)
            raise


def _log_auth_rejection(
    http_status: int,
    required_scope: str,
    service_id: str | None,
) -> None:
    events = {
        status.HTTP_401_UNAUTHORIZED: ("internal_auth_failed", "critical", logging.WARNING),
        status.HTTP_403_FORBIDDEN: ("internal_auth_forbidden", "critical", logging.WARNING),
        status.HTTP_503_SERVICE_UNAVAILABLE: ("internal_auth_unavailable", "high", logging.ERROR),
    }
    event = events.get(http_status)
    if event is None:
        return

    event_type, severity, level = event
    fields: dict[str, object] = {
        "eventType": event_type,
        "severity": severity,
        "httpStatus": http_status,
        "requiredScope": required_scope,
    }
    if service_id:
        fields["serviceId"] = service_id
    logger.log(level, "Internal service authentication rejected", extra=fields)


def _validate_token_lifetime(claims: dict[str, object]) -> None:
    issued_at = claims.get("iat")
    expires_at = claims.get("exp")
    if not _numeric_date(issued_at) or not _numeric_date(expires_at):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="service token must contain numeric iat and exp claims",
        )
    lifetime = float(expires_at) - float(issued_at)
    if lifetime <= 0 or lifetime > MAX_SERVICE_TOKEN_LIFETIME_SECONDS:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="invalid service token lifetime",
        )
    if float(issued_at) > time.time() + MAX_SERVICE_TOKEN_CLOCK_SKEW_SECONDS:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="service token issued too far in the future",
        )


def _numeric_date(value: object) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool)


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
