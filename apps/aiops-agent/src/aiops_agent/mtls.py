"""mTLS client context for Agent callbacks to the AegisOps internal API."""

from __future__ import annotations

import ssl
from functools import lru_cache

from aiops_agent.settings import Settings


def mtls_httpx_kwargs(current_settings: Settings) -> dict[str, object]:
    certificate = current_settings.tls_certificate_file.strip()
    private_key = current_settings.tls_private_key_file.strip()
    trusted_ca = current_settings.tls_client_ca_file.strip()
    if not certificate or not private_key or not trusted_ca:
        raise RuntimeError("Agent mTLS certificate, private key, and trusted CA are required")
    return {
        "verify": _ssl_context(certificate, private_key, trusted_ca),
        "trust_env": False,
    }


@lru_cache(maxsize=4)
def _ssl_context(certificate: str, private_key: str, trusted_ca: str) -> ssl.SSLContext:
    context = ssl.create_default_context(ssl.Purpose.SERVER_AUTH, cafile=trusted_ca)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(certfile=certificate, keyfile=private_key)
    return context
