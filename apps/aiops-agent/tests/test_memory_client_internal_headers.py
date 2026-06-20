from __future__ import annotations

import pytest
import respx
from httpx import Response

from aiops_agent.settings import settings
from aiops_agent.workflow.tools.internal_auth import HEADER_INTERNAL_AGENT_TOKEN, HEADER_TENANT_ID
from aiops_agent.workflow.tools.memory_client import MemoryClient


@pytest.mark.asyncio
@respx.mock
async def test_memory_client_sends_internal_auth_headers(monkeypatch):
    monkeypatch.setattr(settings, "internal_agent_token", "secret-token")

    route = respx.post("http://java/internal/agent/memories/search").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "query": "redis timeout",
                    "topK": 5,
                    "results": [],
                }
            },
        )
    )

    client = MemoryClient(base_url="http://java")

    await client.search_memories(
        tenant_id="tenant_1",
        query="redis timeout",
        tags=["redis"],
    )

    assert route.called
    assert route.calls[0].request.headers[HEADER_TENANT_ID] == "tenant_1"
    assert route.calls[0].request.headers[HEADER_INTERNAL_AGENT_TOKEN] == "secret-token"
