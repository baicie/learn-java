"""Ed25519 Diagnosis Grant verification and request-context authorization."""

from __future__ import annotations

from pathlib import Path
from typing import Any

import jwt
from cryptography.hazmat.primitives import serialization

from aiops_agent.settings import Settings

MAX_GRANT_TTL_SECONDS = 300


class DiagnosisGrantError(ValueError):
    pass


class DiagnosisGrantVerifier:
    def __init__(
        self,
        current_settings: Settings,
        *,
        verification_keys: dict[str, object] | None = None,
    ) -> None:
        self.settings = current_settings
        self._verification_keys = verification_keys

    def verify(
        self,
        token: str,
        *,
        required_scope: str,
        tenant_id: str | None = None,
        incident_id: str | None = None,
        diagnosis_id: str | None = None,
        trace_id: str | None = None,
    ) -> dict[str, Any]:
        if not token or not token.strip():
            raise DiagnosisGrantError("diagnosis grant is required")
        try:
            header = jwt.get_unverified_header(token)
        except jwt.PyJWTError as exc:
            raise DiagnosisGrantError("invalid diagnosis grant header") from exc
        if header.get("alg") != "EdDSA":
            raise DiagnosisGrantError("invalid diagnosis grant algorithm")
        key_id = str(header.get("kid") or "").strip()
        key = self.verification_keys().get(key_id)
        if key is None:
            raise DiagnosisGrantError("unknown diagnosis grant key id")

        try:
            claims = jwt.decode(
                token,
                key,
                algorithms=["EdDSA"],
                audience=self.settings.diagnosis_grant_audience,
                issuer=self.settings.diagnosis_grant_issuer,
                options={
                    "require": [
                        "iss",
                        "aud",
                        "sub",
                        "scope",
                        "tenantId",
                        "incidentId",
                        "diagnosisId",
                        "traceId",
                        "jti",
                        "iat",
                        "exp",
                    ]
                },
            )
        except jwt.ExpiredSignatureError as exc:
            raise DiagnosisGrantError("diagnosis grant expired") from exc
        except jwt.InvalidAudienceError as exc:
            raise DiagnosisGrantError("invalid diagnosis grant audience") from exc
        except jwt.InvalidSignatureError as exc:
            raise DiagnosisGrantError("invalid diagnosis grant signature") from exc
        except jwt.PyJWTError as exc:
            raise DiagnosisGrantError("invalid diagnosis grant") from exc

        self._validate_lifetime(claims)
        scopes = self._claim_values(claims.get("scope"))
        if required_scope not in scopes:
            raise DiagnosisGrantError("diagnosis grant scope is not allowed")
        if claims.get("sub") != f"diagnosis:{claims.get('diagnosisId')}":
            raise DiagnosisGrantError("invalid diagnosis grant subject")

        expected = {
            "tenantId": tenant_id,
            "incidentId": incident_id,
            "diagnosisId": diagnosis_id,
            "traceId": trace_id,
        }
        if any(value is not None and claims.get(name) != value for name, value in expected.items()):
            raise DiagnosisGrantError("diagnosis grant does not authorize request context")
        return claims

    def verification_keys(self) -> dict[str, object]:
        if self._verification_keys is None:
            self._verification_keys = self._load_verification_keys()
        return self._verification_keys

    def _load_verification_keys(self) -> dict[str, object]:
        current_id = self.settings.diagnosis_grant_key_id.strip()
        current_file = self.settings.diagnosis_grant_public_key_file.strip()
        if not current_id or not current_file:
            raise DiagnosisGrantError("diagnosis grant public key configuration is required")
        keys = {current_id: self._load_public_key(current_file)}

        previous_id = self.settings.diagnosis_grant_previous_key_id.strip()
        previous_file = self.settings.diagnosis_grant_previous_public_key_file.strip()
        if bool(previous_id) != bool(previous_file):
            raise DiagnosisGrantError(
                "previous diagnosis grant key id and public key file must be configured together"
            )
        if previous_id:
            if previous_id in keys:
                raise DiagnosisGrantError("diagnosis grant key ids must differ")
            keys[previous_id] = self._load_public_key(previous_file)
        return keys

    @staticmethod
    def _load_public_key(file: str) -> object:
        try:
            return serialization.load_pem_public_key(Path(file).read_bytes())
        except Exception as exc:
            raise DiagnosisGrantError("invalid diagnosis grant public key") from exc

    @staticmethod
    def _validate_lifetime(claims: dict[str, Any]) -> None:
        issued_at = claims.get("iat")
        expires_at = claims.get("exp")
        if (
            not isinstance(issued_at, (int, float))
            or isinstance(issued_at, bool)
            or not isinstance(expires_at, (int, float))
            or isinstance(expires_at, bool)
            or expires_at <= issued_at
            or expires_at - issued_at > MAX_GRANT_TTL_SECONDS
        ):
            raise DiagnosisGrantError("invalid diagnosis grant lifetime")

    @staticmethod
    def _claim_values(value: object) -> set[str]:
        if isinstance(value, str):
            return set(value.split())
        if isinstance(value, list):
            return {str(item) for item in value}
        raise DiagnosisGrantError("invalid diagnosis grant scope")
