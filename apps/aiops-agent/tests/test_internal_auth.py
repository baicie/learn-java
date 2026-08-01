from __future__ import annotations

import pytest

from aiops_agent.observability.context import diagnosis_grant_var
from aiops_agent.settings import Settings
from aiops_agent.workflow.tools.internal_auth import (
    HEADER_DIAGNOSIS_GRANT,
    HEADER_TENANT_ID,
    internal_tool_headers,
)


@pytest.fixture(autouse=True)
def oauth_service_headers(monkeypatch):
    async def headers(_settings):
        return {"Authorization": "Bearer oauth-service-token"}

    monkeypatch.setattr(
        "aiops_agent.workflow.tools.internal_auth.service_credential_headers",
        headers,
    )


@pytest.mark.asyncio
async def test_internal_tool_headers_contains_tenant_oauth_and_diagnosis_grant():
    test_settings = Settings()

    headers = await internal_tool_headers(
        "tenant_1",
        test_settings,
        diagnosis_grant="diagnosis-grant",
    )

    assert headers[HEADER_TENANT_ID] == "tenant_1"
    assert headers["Authorization"] == "Bearer oauth-service-token"
    assert headers[HEADER_DIAGNOSIS_GRANT] == "diagnosis-grant"


@pytest.mark.asyncio
async def test_internal_tool_headers_strips_tenant():
    test_settings = Settings()

    headers = await internal_tool_headers(
        " tenant_1 ",
        test_settings,
        diagnosis_grant="diagnosis-grant",
    )

    assert headers[HEADER_TENANT_ID] == "tenant_1"


@pytest.mark.asyncio
async def test_internal_tool_headers_rejects_blank_tenant():
    test_settings = Settings()

    with pytest.raises(ValueError):
        await internal_tool_headers(" ", test_settings, diagnosis_grant="diagnosis-grant")


@pytest.mark.asyncio
async def test_internal_tool_headers_rejects_missing_diagnosis_grant():
    test_settings = Settings()
    token = diagnosis_grant_var.set(None)

    try:
        with pytest.raises(ValueError):
            await internal_tool_headers("tenant_1", test_settings, diagnosis_grant="")
    finally:
        diagnosis_grant_var.reset(token)


@pytest.mark.asyncio
async def test_internal_tool_headers_rejects_blank_context_diagnosis_grant():
    test_settings = Settings()
    token = diagnosis_grant_var.set(" \t ")

    try:
        with pytest.raises(ValueError, match="diagnosis grant is required"):
            await internal_tool_headers("tenant_1", test_settings)
    finally:
        diagnosis_grant_var.reset(token)


@pytest.mark.asyncio
async def test_internal_tool_headers_cannot_disable_diagnosis_grant(monkeypatch):
    monkeypatch.setenv("AIOPS_AGENT_DIAGNOSIS_GRANT_REQUIRED", "false")
    test_settings = Settings()
    token = diagnosis_grant_var.set(None)

    try:
        with pytest.raises(ValueError, match="diagnosis grant is required"):
            await internal_tool_headers("tenant_1", test_settings)
    finally:
        diagnosis_grant_var.reset(token)
