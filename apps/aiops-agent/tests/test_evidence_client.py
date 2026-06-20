"""Tests for evidence_client: structure validation and error handling."""

from __future__ import annotations

import pytest
import respx
from httpx import Response

from app.agent.errors import ToolError
from app.agent.tools.evidence_client import EvidenceClient


@pytest.mark.asyncio
@respx.mock
async def test_evidence_client_parses_data_items():
    respx.get("http://java/internal/agent/incidents/inc_1/evidence").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "items": [
                        {
                            "evidence_id": "ev_1",
                            "evidence_type": "metric",
                            "title": "Error rate",
                            "summary": "5xx increased",
                            "source": "test",
                        }
                    ]
                }
            },
        )
    )

    client = EvidenceClient(base_url="http://java")

    evidence = await client.fetch_evidence("tenant_1", "inc_1")

    assert len(evidence) == 1
    assert evidence[0].evidence_id == "ev_1"


@pytest.mark.asyncio
@respx.mock
async def test_evidence_client_parses_top_level_items():
    respx.get("http://java/internal/agent/incidents/inc_1/evidence").mock(
        return_value=Response(
            200,
            json=[
                {
                    "evidence_id": "ev_2",
                    "evidence_type": "log",
                    "title": "Timeout log",
                    "summary": "connection timed out",
                    "source": "test",
                }
            ],
        )
    )

    client = EvidenceClient(base_url="http://java")

    evidence = await client.fetch_evidence("tenant_1", "inc_1")

    assert len(evidence) == 1
    assert evidence[0].evidence_id == "ev_2"


@pytest.mark.asyncio
@respx.mock
async def test_evidence_client_rejects_invalid_response_shape():
    bad_payload = {"data": {"items": {"bad": "shape"}}}
    respx.get("http://java/internal/agent/incidents/inc_1/evidence").mock(
        return_value=Response(200, json=bad_payload)
    )

    client = EvidenceClient(base_url="http://java")

    with pytest.raises(ToolError) as exc_info:
        await client.fetch_evidence("tenant_1", "inc_1")

    assert exc_info.value.code == "EVIDENCE_TOOL_RESPONSE_INVALID"


@pytest.mark.asyncio
@respx.mock
async def test_evidence_client_wraps_invalid_json_response():
    respx.get("http://java/internal/agent/incidents/inc_1/evidence").mock(
        return_value=Response(200, text="not-json")
    )

    client = EvidenceClient(base_url="http://java")

    with pytest.raises(ToolError):
        await client.fetch_evidence("tenant_1", "inc_1")


@pytest.mark.asyncio
@respx.mock
async def test_evidence_client_rejects_non_object_items():
    bad_payload = {"data": {"items": ["not an object"]}}
    respx.get("http://java/internal/agent/incidents/inc_1/evidence").mock(
        return_value=Response(200, json=bad_payload)
    )

    client = EvidenceClient(base_url="http://java")

    with pytest.raises(ToolError) as exc_info:
        await client.fetch_evidence("tenant_1", "inc_1")

    assert exc_info.value.code == "EVIDENCE_TOOL_RESPONSE_INVALID"
