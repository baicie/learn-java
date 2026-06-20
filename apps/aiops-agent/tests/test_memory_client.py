"""Tests for memory client."""

from __future__ import annotations

import pytest
import respx
from httpx import Response

from aiops_agent.workflow.tools.memory_client import MemoryClient


@pytest.mark.asyncio
@respx.mock
async def test_memory_client_search_parses_results():
    respx.post("http://java/internal/agent/memories/search").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "query": "redis timeout",
                    "topK": 5,
                    "results": [
                        {
                            "id": "agm_1",
                            "title": "Redis timeout pattern",
                            "content": "Redis timeout caused order service errors.",
                            "memoryType": "root_cause_pattern",
                            "scopeType": "tenant",
                            "score": 0.91,
                            "confidence": 0.8,
                            "tags": ["redis"],
                        }
                    ],
                }
            },
        )
    )

    client = MemoryClient(base_url="http://java")

    memories = await client.search_memories(
        tenant_id="tenant_1",
        query="redis timeout",
        tags=["redis"],
    )

    assert len(memories) == 1
    assert memories[0].memory_id == "agm_1"
    assert memories[0].memory_type == "root_cause_pattern"


@pytest.mark.asyncio
@respx.mock
async def test_memory_client_create_parses_response():
    respx.post("http://java/internal/agent/memories").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "id": "agm_1",
                    "title": "Redis timeout pattern",
                    "content": "Redis timeout caused order service errors.",
                    "memoryType": "root_cause_pattern",
                    "scopeType": "tenant",
                    "score": 0.0,
                    "confidence": 0.8,
                    "tags": ["redis"],
                }
            },
        )
    )

    client = MemoryClient(base_url="http://java")

    memory = await client.create_memory(
        tenant_id="tenant_1",
        scope_type="tenant",
        scope_id=None,
        memory_type="root_cause_pattern",
        source_type="diagnosis",
        source_id="inc_1",
        title="Redis timeout pattern",
        content="Redis timeout caused order service errors.",
        tags=["redis"],
        confidence=0.8,
    )

    assert memory.memory_id == "agm_1"
    assert memory.confidence == 0.8
