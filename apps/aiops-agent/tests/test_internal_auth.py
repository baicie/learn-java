from __future__ import annotations

import pytest

from aiops_agent.observability.context import diagnosis_grant_var
from aiops_agent.workflow.tools.internal_auth import (
    HEADER_DIAGNOSIS_GRANT,
    HEADER_TENANT_ID,
    internal_tool_headers,
)


@pytest.mark.asyncio
async def test_internal_tool_headers_contains_tenant_and_diagnosis_grant_without_bearer():
    headers = await internal_tool_headers(
        "tenant_1",
        diagnosis_grant="diagnosis-grant",
    )

    assert headers[HEADER_TENANT_ID] == "tenant_1"
    assert "Authorization" not in headers
    assert headers[HEADER_DIAGNOSIS_GRANT] == "diagnosis-grant"


@pytest.mark.asyncio
async def test_internal_tool_headers_requires_keyword_for_diagnosis_grant():
    with pytest.raises(TypeError):
        await internal_tool_headers("tenant_1", "diagnosis-grant")


@pytest.mark.asyncio
async def test_internal_tool_headers_strips_tenant():
    headers = await internal_tool_headers(
        " tenant_1 ",
        diagnosis_grant="diagnosis-grant",
    )

    assert headers[HEADER_TENANT_ID] == "tenant_1"


@pytest.mark.asyncio
async def test_internal_tool_headers_rejects_blank_tenant():
    with pytest.raises(ValueError):
        await internal_tool_headers(" ", diagnosis_grant="diagnosis-grant")


@pytest.mark.asyncio
async def test_internal_tool_headers_rejects_missing_diagnosis_grant():
    token = diagnosis_grant_var.set(None)

    try:
        with pytest.raises(ValueError):
            await internal_tool_headers("tenant_1", diagnosis_grant="")
    finally:
        diagnosis_grant_var.reset(token)


@pytest.mark.asyncio
async def test_internal_tool_headers_rejects_blank_context_diagnosis_grant():
    token = diagnosis_grant_var.set(" \t ")

    try:
        with pytest.raises(ValueError, match="diagnosis grant is required"):
            await internal_tool_headers("tenant_1")
    finally:
        diagnosis_grant_var.reset(token)


@pytest.mark.asyncio
async def test_internal_tool_headers_cannot_disable_diagnosis_grant(monkeypatch):
    monkeypatch.setenv("AIOPS_AGENT_DIAGNOSIS_GRANT_REQUIRED", "false")
    token = diagnosis_grant_var.set(None)

    try:
        with pytest.raises(ValueError, match="diagnosis grant is required"):
            await internal_tool_headers("tenant_1")
    finally:
        diagnosis_grant_var.reset(token)
