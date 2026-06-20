"""Tests for evidence_graph."""

from __future__ import annotations

import pytest

from app.agent.contracts import EvidenceItem
from app.agent.graph.context import GraphContext
from app.agent.graph.evidence_graph import fetch_evidence_node
from tests.fakes import FakeEvidenceClient, FakeKnowledgeClient


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
    context = GraphContext(
        evidence_client=FakeEvidenceClient(evidence),
        knowledge_client=FakeKnowledgeClient(),
    )

    state = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "title": "Order service error",
    }

    result = await fetch_evidence_node(state, context)

    assert len(result["evidence"]) == 1
    assert result["evidence"][0].evidence_id == "ev_1"
