from __future__ import annotations

import json

import pytest
import respx
from httpx import Response

from aiops_agent.workflow.tools.internal_auth import HEADER_TENANT_ID
from aiops_agent.workflow.tools.plugin_policy_client import (
    PluginPolicyClient,
    PluginToolDeniedError,
)


@respx.mock
async def test_authorize_tool_allowed():
    respx.post("http://localhost:8080/internal/agent/plugins/tools/authorize").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "allowed": True,
                    "toolKey": "memory.search",
                    "reason": "allowed by tenant plugin policy",
                }
            },
        ),
    )

    client = PluginPolicyClient(base_url="http://localhost:8080")

    allowed = await client.authorize_tool("tenant_1", "memory.search")

    assert allowed is True


@respx.mock
async def test_authorize_tool_denied_raises():
    respx.post("http://localhost:8080/internal/agent/plugins/tools/authorize").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "allowed": False,
                    "toolKey": "memory.create",
                    "reason": "not allowed by tenant plugin policy",
                }
            },
        ),
    )

    client = PluginPolicyClient(base_url="http://localhost:8080")

    with pytest.raises(PluginToolDeniedError) as exc_info:
        await client.authorize_tool("tenant_1", "memory.create")

    assert exc_info.value.tool_key == "memory.create"
    assert "not allowed" in exc_info.value.reason


@respx.mock
async def test_authorize_tool_sends_internal_headers():
    route = respx.post("http://localhost:8080/internal/agent/plugins/tools/authorize").mock(
        return_value=Response(
            200,
            json={"data": {"allowed": True, "toolKey": "evidence.fetch", "reason": "ok"}},
        ),
    )

    client = PluginPolicyClient(base_url="http://localhost:8080")

    await client.authorize_tool("tenant_abc", "evidence.fetch")

    assert route.calls[0].request.headers[HEADER_TENANT_ID] == "tenant_abc"


@respx.mock
async def test_authorize_tool_payload_structure():
    route = respx.post("http://localhost:8080/internal/agent/plugins/tools/authorize").mock(
        return_value=Response(
            200,
            json={"data": {"allowed": True, "toolKey": "knowledge.search_cases", "reason": "ok"}},
        ),
    )

    client = PluginPolicyClient(base_url="http://localhost:8080")

    await client.authorize_tool("tenant_1", "knowledge.search_cases")

    body = json.loads(route.calls[0].request.content)
    assert body["tenantId"] == "tenant_1"
    assert body["toolKey"] == "knowledge.search_cases"


@respx.mock
async def test_extract_data_from_wrapped_response():
    respx.post("http://localhost:8080/internal/agent/plugins/tools/authorize").mock(
        return_value=Response(
            200,
            json={
                "code": 0,
                "data": {"allowed": True, "toolKey": "checkpoint.get", "reason": "ok"},
                "message": "success",
            },
        ),
    )

    client = PluginPolicyClient(base_url="http://localhost:8080")

    allowed = await client.authorize_tool("tenant_1", "checkpoint.get")
    assert allowed is True


@respx.mock
async def test_denied_with_empty_reason():
    respx.post("http://localhost:8080/internal/agent/plugins/tools/authorize").mock(
        return_value=Response(
            200,
            json={"data": {"allowed": False, "toolKey": "memory.search"}},
        ),
    )

    client = PluginPolicyClient(base_url="http://localhost:8080")

    with pytest.raises(PluginToolDeniedError) as exc_info:
        await client.authorize_tool("tenant_1", "memory.search")

    assert exc_info.value.tool_key == "memory.search"
    assert "tool denied by plugin policy" in exc_info.value.reason


async def test_raises_when_response_not_object():
    respx.post("http://localhost:8080/internal/agent/plugins/tools/authorize").mock(
        return_value=Response(200, json=["not", "a", "dict"]),
    )

    client = PluginPolicyClient(base_url="http://localhost:8080")

    with pytest.raises(Exception):  # PluginToolPolicyError
        await client.authorize_tool("tenant_1", "memory.search")
