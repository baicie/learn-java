from __future__ import annotations

import pytest

from aiops_agent import main, mtls
from aiops_agent.observability.context import diagnosis_grant_var


@pytest.fixture(autouse=True)
def internal_security_context(monkeypatch):
    monkeypatch.setattr(mtls, "mtls_httpx_kwargs", lambda current_settings: {"trust_env": False})
    monkeypatch.setattr(
        main.diagnosis_grant_verifier,
        "verify",
        lambda token, **kwargs: {"sub": "diagnosis:test", "jti": "test"},
    )
    token = diagnosis_grant_var.set("test-diagnosis-grant")
    try:
        yield
    finally:
        diagnosis_grant_var.reset(token)
