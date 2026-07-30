from __future__ import annotations

import pytest

from aiops_agent.settings import Settings
from aiops_agent.workflow.tools.internal_auth import (
    HEADER_DIAGNOSIS_GRANT,
    HEADER_INTERNAL_AGENT_TOKEN,
    HEADER_TENANT_ID,
    internal_tool_headers,
)


@pytest.mark.asyncio
async def test_internal_tool_headers_contains_tenant_token_and_diagnosis_grant():
    test_settings = Settings(internal_agent_token="secret-token")

    headers = await internal_tool_headers(
        "tenant_1",
        test_settings,
        diagnosis_grant="diagnosis-grant",
    )

    assert headers[HEADER_TENANT_ID] == "tenant_1"
    assert headers[HEADER_INTERNAL_AGENT_TOKEN] == "secret-token"
    assert headers[HEADER_DIAGNOSIS_GRANT] == "diagnosis-grant"


@pytest.mark.asyncio
async def test_internal_tool_headers_strips_tenant():
    test_settings = Settings(internal_agent_token="secret-token")

    headers = await internal_tool_headers(
        " tenant_1 ",
        test_settings,
        diagnosis_grant="diagnosis-grant",
    )

    assert headers[HEADER_TENANT_ID] == "tenant_1"


@pytest.mark.asyncio
async def test_internal_tool_headers_rejects_blank_tenant():
    test_settings = Settings(internal_agent_token="secret-token")

    with pytest.raises(ValueError):
        await internal_tool_headers(" ", test_settings, diagnosis_grant="diagnosis-grant")


@pytest.mark.asyncio
async def test_internal_tool_headers_rejects_missing_diagnosis_grant():
    test_settings = Settings(internal_agent_token="secret-token")

    with pytest.raises(ValueError):
        await internal_tool_headers("tenant_1", test_settings, diagnosis_grant="")
