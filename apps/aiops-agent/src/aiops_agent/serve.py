"""Fail-closed Uvicorn entrypoint for the internal mTLS Agent API."""

from __future__ import annotations

import asyncio
import ssl
from typing import Any

import uvicorn
from uvicorn.protocols.http.h11_impl import H11Protocol

from aiops_agent.settings import settings


def peer_has_spiffe_identity(
    transport: asyncio.Transport, expected_identity: str
) -> bool:
    try:
        tls_object = transport.get_extra_info("ssl_object")
        certificate: dict[str, Any] = tls_object.getpeercert()
    except (AttributeError, TypeError, ValueError, ssl.SSLError):
        return False

    subject_alt_names = certificate.get("subjectAltName", ())
    return any(
        isinstance(name, tuple)
        and len(name) >= 2
        and name[0] == "URI"
        and name[1] == expected_identity
        for name in subject_alt_names
    )


class SpiffeIdentityH11Protocol(H11Protocol):
    def connection_made(self, transport: asyncio.Transport) -> None:
        super().connection_made(transport)
        self._spiffe_identity_authorized = peer_has_spiffe_identity(
            transport,
            settings.tls_expected_client_spiffe_uri,
        )
        if not self._spiffe_identity_authorized:
            self.logger.warning("Rejected mTLS peer with an unauthorized workload identity")
            transport.close()

    def data_received(self, data: bytes) -> None:
        if getattr(self, "_spiffe_identity_authorized", False):
            super().data_received(data)


def main() -> None:
    required = {
        "tls_certificate_file": settings.tls_certificate_file,
        "tls_private_key_file": settings.tls_private_key_file,
        "tls_client_ca_file": settings.tls_client_ca_file,
        "tls_expected_client_spiffe_uri": settings.tls_expected_client_spiffe_uri,
    }
    missing = [name for name, value in required.items() if not value or not value.strip()]
    if missing:
        raise RuntimeError(f"Missing Agent mTLS setting: {missing[0]}")

    uvicorn.run(
        "aiops_agent.main:app",
        host="0.0.0.0",
        port=9008,
        ssl_certfile=settings.tls_certificate_file,
        ssl_keyfile=settings.tls_private_key_file,
        ssl_ca_certs=settings.tls_client_ca_file,
        ssl_cert_reqs=ssl.CERT_REQUIRED,
        http=SpiffeIdentityH11Protocol,
        proxy_headers=False,
    )


if __name__ == "__main__":
    main()
