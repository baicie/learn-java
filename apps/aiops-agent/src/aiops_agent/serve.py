"""Fail-closed Uvicorn entrypoint for the internal mTLS Agent API."""

from __future__ import annotations

import ssl

import uvicorn

from aiops_agent.settings import settings


def main() -> None:
    required = {
        "tls_certificate_file": settings.tls_certificate_file,
        "tls_private_key_file": settings.tls_private_key_file,
        "tls_client_ca_file": settings.tls_client_ca_file,
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
        proxy_headers=False,
    )


if __name__ == "__main__":
    main()
