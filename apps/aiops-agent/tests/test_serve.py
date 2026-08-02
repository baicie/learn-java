from __future__ import annotations

import logging
from typing import Any

import aiops_agent.serve as serve
from aiops_agent.settings import settings


class FakeTlsObject:
    def __init__(self, peer_certificate: dict[str, Any]) -> None:
        self.peer_certificate = peer_certificate

    def getpeercert(self) -> dict[str, Any]:
        return self.peer_certificate


class FakeTransport:
    def __init__(self, peer_certificate: dict[str, Any]) -> None:
        self.tls_object = FakeTlsObject(peer_certificate)
        self.closed = False

    def get_extra_info(self, name: str) -> object | None:
        return self.tls_object if name == "ssl_object" else None

    def close(self) -> None:
        self.closed = True


def test_peer_identity_requires_exact_app_spiffe_uri() -> None:
    expected = "spiffe://aegisops.local/service/aegisops-app"
    wrong_peer = FakeTransport(
        {"subjectAltName": (("URI", "spiffe://aegisops.local/service/other"),)}
    )
    app_peer = FakeTransport(
        {
            "subjectAltName": (
                ("DNS", "aegisops-app"),
                ("URI", expected),
            )
        }
    )

    assert not serve.peer_has_spiffe_identity(wrong_peer, expected)
    assert serve.peer_has_spiffe_identity(app_peer, expected)


def test_protocol_drops_data_from_wrong_spiffe_identity(monkeypatch) -> None:
    expected = "spiffe://aegisops.local/service/aegisops-app"
    delivered: list[bytes] = []
    monkeypatch.setattr(settings, "tls_expected_client_spiffe_uri", expected)
    monkeypatch.setattr(
        serve.H11Protocol,
        "connection_made",
        lambda _self, _transport: None,
    )
    monkeypatch.setattr(
        serve.H11Protocol,
        "data_received",
        lambda _self, data: delivered.append(data),
    )
    protocol = object.__new__(serve.SpiffeIdentityH11Protocol)
    protocol.logger = logging.getLogger("test.agent.mtls")
    transport = FakeTransport(
        {"subjectAltName": (("URI", "spiffe://aegisops.local/service/other"),)}
    )

    protocol.connection_made(transport)
    protocol.data_received(b"GET /health HTTP/1.1\r\n\r\n")

    assert transport.closed
    assert delivered == []


def test_server_entrypoint_installs_spiffe_identity_protocol(monkeypatch) -> None:
    captured: dict[str, object] = {}
    monkeypatch.setattr(settings, "tls_certificate_file", "/certs/agent.crt")
    monkeypatch.setattr(settings, "tls_private_key_file", "/certs/agent.key")
    monkeypatch.setattr(settings, "tls_client_ca_file", "/certs/control-plane-ca.crt")
    monkeypatch.setattr(
        settings,
        "tls_expected_client_spiffe_uri",
        "spiffe://aegisops.local/service/aegisops-app",
        raising=False,
    )
    monkeypatch.setattr(
        serve.uvicorn,
        "run",
        lambda *args, **kwargs: captured.update(kwargs),
    )

    serve.main()

    assert captured["http"] is serve.SpiffeIdentityH11Protocol
