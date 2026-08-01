"""Tests for evidence_graph."""

from __future__ import annotations

from datetime import datetime, timezone

import pytest

from aiops_agent.workflow.contracts import EvidenceItem
from aiops_agent.workflow.graph.context import GraphContext
from aiops_agent.workflow.graph.evidence_graph import fetch_evidence_node
from tests.fakes import (
    FailingEvidenceClient,
    FakeCheckpointClient,
    FakeEvidenceClient,
    FakeKnowledgeClient,
)


@pytest.mark.asyncio
async def test_fetch_evidence_node_sets_evidence():
    evidence = [
        EvidenceItem(
            evidence_id="ev_1",
            evidence_type="metric",
            title="Error rate",
            summary="5xx error rate increased",
            source="test",
        )
    ]
    evidence_client = FakeEvidenceClient(evidence)
    context = GraphContext(
        evidence_client=evidence_client,
        knowledge_client=FakeKnowledgeClient(),
        checkpoint_client=FakeCheckpointClient(),
    )

    state = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "trace_id": "trace_1",
        "title": "Order service error",
        "primary_asset_id": "asset_order",
        "started_at": datetime(2026, 7, 31, 4, 0, tzinfo=timezone.utc),
        "last_seen_at": datetime(2026, 7, 31, 4, 5, tzinfo=timezone.utc),
    }

    result = await fetch_evidence_node(state, context)

    assert len(result["evidence"]) == 1
    assert result["evidence"][0].evidence_id == "ev_1"
    assert evidence_client.query_context == {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "trace_id": "trace_1",
        "primary_asset_id": "asset_order",
        "started_at": datetime(2026, 7, 31, 4, 0, tzinfo=timezone.utc),
        "last_seen_at": datetime(2026, 7, 31, 4, 5, tzinfo=timezone.utc),
    }


@pytest.mark.asyncio
async def test_fetch_evidence_node_converts_unexpected_error_to_tool_error_evidence():
    context = GraphContext(
        evidence_client=FailingEvidenceClient(),
        knowledge_client=FakeKnowledgeClient(),
        checkpoint_client=FakeCheckpointClient(),
    )

    state = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "trace_id": "trace_1",
        "title": "Order service error",
    }

    result = await fetch_evidence_node(state, context)

    assert len(result["evidence"]) == 1
    assert result["evidence"][0].evidence_type == "tool_error"
    assert result["evidence"][0].evidence_id == "evidence_tool_unexpected_error"
