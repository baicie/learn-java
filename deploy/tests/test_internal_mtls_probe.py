from __future__ import annotations

import importlib.util
import ssl
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "deploy/scripts/verify-internal-mtls.py"


def load_probe():
    spec = importlib.util.spec_from_file_location("internal_mtls_probe", SCRIPT)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_probe_builds_client_authenticated_tls_context(monkeypatch):
    probe = load_probe()
    calls: list[tuple[str, tuple[str, str] | str]] = []

    class FakeContext:
        minimum_version = None
        check_hostname = False
        verify_mode = ssl.CERT_NONE

        def load_cert_chain(self, certfile, keyfile):
            calls.append(("cert", (certfile, keyfile)))

        def load_verify_locations(self, cafile):
            calls.append(("ca", cafile))

    context = FakeContext()
    monkeypatch.setattr(ssl, "create_default_context", lambda: context)

    result = probe.build_ssl_context("app.crt", "app.key", "agent-ca.crt")

    assert result is context
    assert context.minimum_version == ssl.TLSVersion.TLSv1_2
    assert context.check_hostname is True
    assert context.verify_mode == ssl.CERT_REQUIRED
    assert calls == [("cert", ("app.crt", "app.key")), ("ca", "agent-ca.crt")]


def test_probe_rejects_peer_without_expected_spiffe_uri():
    probe = load_probe()

    with pytest.raises(ValueError, match="SPIFFE"):
        probe.require_spiffe_identity(
            {"subjectAltName": (("URI", "spiffe://aegisops.local/service/other"),)},
            "spiffe://aegisops.local/service/aiops-agent",
        )


def test_probe_script_contains_no_oauth_or_bearer_flow():
    text = SCRIPT.read_text(encoding="utf-8").lower()

    assert "oauth" not in text
    assert "bearer" not in text
    assert "client_credentials" not in text
