"""Tests for evidence_client: structure validation and error handling."""

from __future__ import annotations

import pytest
import respx
from httpx import Response

from aiops_agent.workflow.errors import ToolError
from aiops_agent.workflow.tools.evidence_client import EvidenceClient
from aiops_agent.workflow.tools.internal_auth import HEADER_INTERNAL_AGENT_TOKEN


@pytest.mark.asyncio
@respx.mock
async def test_evidence_client_queries_java_contract_with_internal_token():
    route = respx.post("http://java/internal/agent/evidence/query").mock(
        return_value=Response(
            200,
            json={
                "metrics": {
                    "available": True,
                    "reason": "",
                    "series": [{"name": "cpu", "latest": "0.95"}],
                },
                "logs": {"available": False, "reason": "disabled", "patterns": []},
                "changes": {"available": False, "reason": "disabled", "events": []},
            },
        )
    )

    client = EvidenceClient(base_url="http://java")

    evidence = await client.fetch_evidence("tenant_1", "inc_1")

    assert len(evidence) == 1
    assert evidence[0].evidence_type == "metric"
    request = route.calls[0].request
    assert request.headers[HEADER_INTERNAL_AGENT_TOKEN] == "dev-internal-agent-token"
    assert b'"tenantId":"tenant_1"' in request.content


@pytest.mark.asyncio
@respx.mock
async def test_evidence_client_maps_logs_and_changes():
    respx.post("http://java/internal/agent/evidence/query").mock(
        return_value=Response(
            200,
            json={
                "metrics": {"available": False, "reason": "disabled", "series": []},
                "logs": {
                    "available": True,
                    "reason": "",
                    "patterns": [{"severity": "error", "sample": "connection timed out"}],
                },
                "changes": {
                    "available": True,
                    "reason": "",
                    "events": [{"title": "deploy v2", "changeType": "deploy"}],
                },
            },
        )
    )

    client = EvidenceClient(base_url="http://java")

    evidence = await client.fetch_evidence("tenant_1", "inc_1")

    assert [item.evidence_type for item in evidence] == ["log", "change"]


@pytest.mark.asyncio
@respx.mock
async def test_evidence_client_rejects_invalid_response_shape():
    bad_payload = {"metrics": {"available": True, "series": {"bad": "shape"}}}
    respx.post("http://java/internal/agent/evidence/query").mock(
        return_value=Response(200, json=bad_payload)
    )

    client = EvidenceClient(base_url="http://java")

    with pytest.raises(ToolError) as exc_info:
        await client.fetch_evidence("tenant_1", "inc_1")

    assert exc_info.value.code == "EVIDENCE_TOOL_RESPONSE_INVALID"


@pytest.mark.asyncio
@respx.mock
async def test_evidence_client_wraps_invalid_json_response():
    respx.post("http://java/internal/agent/evidence/query").mock(
        return_value=Response(200, text="not-json")
    )

    client = EvidenceClient(base_url="http://java")

    with pytest.raises(ToolError):
        await client.fetch_evidence("tenant_1", "inc_1")


@pytest.mark.asyncio
@respx.mock
async def test_evidence_client_rejects_non_object_items():
    bad_payload = {
        "metrics": {"available": True, "series": ["not an object"]},
        "logs": {"available": False, "patterns": []},
        "changes": {"available": False, "events": []},
    }
    respx.post("http://java/internal/agent/evidence/query").mock(
        return_value=Response(200, json=bad_payload)
    )

    client = EvidenceClient(base_url="http://java")

    with pytest.raises(ToolError) as exc_info:
        await client.fetch_evidence("tenant_1", "inc_1")

    assert exc_info.value.code == "EVIDENCE_TOOL_RESPONSE_INVALID"
