"""Tests for knowledge_client: Phase6.2 schema compatibility and error handling."""

from __future__ import annotations

import pytest
import respx
from httpx import Response

from app.agent.errors import ToolError
from app.agent.tools.knowledge_client import KnowledgeClient


@pytest.mark.asyncio
@respx.mock
async def test_knowledge_client_extracts_root_cause_from_phase6_chunk_content():
    respx.post("http://java/internal/agent/tools/search-cases").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "results": [
                        {
                            "documentId": "kbd_1",
                            "chunkId": "kbc_1",
                            "sourceType": "incident_case",
                            "sourceId": "icase_1",
                            "title": "Order service redis timeout",
                            "content": (
                                "Title: Order service redis timeout\n"
                                "Root Cause: redis timeout\n"
                                "Resolution: restart service\n"
                            ),
                            "score": 0.91,
                            "vectorScore": 0.8,
                            "keywordScore": 0.5,
                            "metadataJson": '{"tags":["redis","timeout"]}',
                        }
                    ]
                }
            },
        )
    )

    client = KnowledgeClient(base_url="http://java")

    cases = await client.search_cases(
        tenant_id="tenant_1",
        query="redis timeout",
        tags=["redis"],
        top_k=5,
    )

    assert len(cases) == 1
    assert cases[0].case_id == "icase_1"
    assert cases[0].root_cause == "redis timeout"
    assert cases[0].resolution == "restart service"
    assert cases[0].tags == ["redis", "timeout"]


@pytest.mark.asyncio
@respx.mock
async def test_knowledge_client_extracts_root_cause_from_metadata():
    respx.post("http://java/internal/agent/tools/search-cases").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "results": [
                        {
                            "sourceId": "icase_2",
                            "title": "DB slow query",
                            "content": "No root cause here",
                            "score": 0.8,
                            "metadataJson": '{"rootCause":"slow query","tags":["db"]}',
                        }
                    ]
                }
            },
        )
    )

    client = KnowledgeClient(base_url="http://java")

    cases = await client.search_cases(
        tenant_id="tenant_1",
        query="slow query",
        tags=[],
        top_k=5,
    )

    assert len(cases) == 1
    assert cases[0].root_cause == "slow query"
    assert cases[0].tags == ["db"]


@pytest.mark.asyncio
@respx.mock
async def test_knowledge_client_rejects_invalid_results_shape():
    respx.post("http://java/internal/agent/tools/search-cases").mock(
        return_value=Response(
            200,
            json={"data": {"results": {"bad": "shape"}}},
        )
    )

    client = KnowledgeClient(base_url="http://java")

    with pytest.raises(ToolError) as exc_info:
        await client.search_cases(
            tenant_id="tenant_1",
            query="redis timeout",
            tags=[],
            top_k=5,
        )

    assert exc_info.value.code == "KNOWLEDGE_TOOL_RESPONSE_INVALID"


@pytest.mark.asyncio
@respx.mock
async def test_knowledge_client_wraps_invalid_json_response():
    respx.post("http://java/internal/agent/tools/search-cases").mock(
        return_value=Response(200, text="not-json")
    )

    client = KnowledgeClient(base_url="http://java")

    with pytest.raises(ToolError):
        await client.search_cases(
            tenant_id="tenant_1",
            query="redis timeout",
            tags=[],
            top_k=5,
        )
