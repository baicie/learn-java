"""Tests for case_retrieval_graph."""

from __future__ import annotations

import pytest

from app.agent.contracts import SimilarCase
from app.agent.graph.case_retrieval_graph import retrieve_cases_node
from app.agent.graph.context import GraphContext
from tests.fakes import FakeCheckpointClient, FakeEvidenceClient, FakeKnowledgeClient, FailingKnowledgeClient


@pytest.mark.asyncio
async def test_retrieve_cases_node_calls_knowledge_client():
    knowledge = FakeKnowledgeClient(
        [
            SimilarCase(
                case_id="case_1",
                title="Redis timeout",
                summary="Redis timeout caused order service errors",
                root_cause="redis timeout",
                score=0.9,
            )
        ]
    )
    context = GraphContext(
        evidence_client=FakeEvidenceClient(),
        knowledge_client=knowledge,
        checkpoint_client=FakeCheckpointClient(),
    )

    state = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "title": "Order service redis timeout",
        "tags": ["order-service"],
        "enable_case_retrieval": True,
    }

    result = await retrieve_cases_node(state, context)

    assert knowledge.called is True
    assert len(result["similar_cases"]) == 1
    assert result["similar_cases"][0].case_id == "case_1"


@pytest.mark.asyncio
async def test_retrieve_cases_node_can_be_disabled():
    knowledge = FakeKnowledgeClient()
    context = GraphContext(
        evidence_client=FakeEvidenceClient(),
        knowledge_client=knowledge,
        checkpoint_client=FakeCheckpointClient(),
    )

    state = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "title": "Order service redis timeout",
        "tags": [],
        "enable_case_retrieval": False,
    }

    result = await retrieve_cases_node(state, context)

    assert knowledge.called is False
    assert result["similar_cases"] == []


@pytest.mark.asyncio
async def test_retrieve_cases_node_handles_unexpected_client_error():
    context = GraphContext(
        evidence_client=FakeEvidenceClient(),
        knowledge_client=FailingKnowledgeClient(),
        checkpoint_client=FakeCheckpointClient(),
    )

    state = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "title": "Order service redis timeout",
        "tags": [],
        "enable_case_retrieval": True,
    }

    result = await retrieve_cases_node(state, context)

    assert result["similar_cases"] == []
