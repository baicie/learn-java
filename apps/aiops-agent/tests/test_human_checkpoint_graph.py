"""Tests for human checkpoint graph."""

from __future__ import annotations

import pytest

from app.agent.graph.context import GraphContext
from app.agent.graph.human_checkpoint_graph import human_checkpoint_node
from tests.fakes import (
    FakeCheckpointClient,
    FakeEvidenceClient,
    FakeKnowledgeClient,
    FailingCheckpointClient,
)


@pytest.mark.asyncio
async def test_human_checkpoint_skipped_when_disabled():
    context = GraphContext(
        evidence_client=FakeEvidenceClient(),
        knowledge_client=FakeKnowledgeClient(),
        checkpoint_client=FakeCheckpointClient(),
    )

    state = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "title": "Order timeout",
        "enable_human_checkpoint": False,
    }

    result = await human_checkpoint_node(state, context)

    assert result["checkpoint_required"] is False
    assert result["checkpoint_status"] == "skipped"


@pytest.mark.asyncio
async def test_human_checkpoint_handles_checkpoint_client_failure():
    context = GraphContext(
        evidence_client=FakeEvidenceClient(),
        knowledge_client=FakeKnowledgeClient(),
        checkpoint_client=FailingCheckpointClient(),
    )

    state = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "title": "Order timeout",
        "severity": "high",
        "enable_human_checkpoint": True,
        "root_cause": "redis timeout",
        "confidence": 0.8,
        "evidence": [],
        "similar_cases": [],
        "tags": ["redis"],
        "metadata": {},
        "safety_notes": [],
    }

    result = await human_checkpoint_node(state, context)

    assert result["checkpoint_required"] is True
    assert result["checkpoint_status"] == "failed"
    assert result["checkpoint"] is None
    assert any(
        "Checkpoint creation failed" in note
        for note in result["safety_notes"]
    )
