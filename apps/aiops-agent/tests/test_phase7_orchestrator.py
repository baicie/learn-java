"""Tests for the orchestrator: end-to-end graph execution."""

from __future__ import annotations

import pytest

from app.agent.contracts import DiagnosisRequest, DiagnosisResumeRequest, EvidenceItem, SimilarCase
from app.agent.graph.context import GraphContext
from app.agent.graph.orchestrator import resume_diagnosis_graph, run_diagnosis_graph
from tests.fakes import (
    ApprovedCheckpointClient,
    FakeCheckpointClient,
    FakeEvidenceClient,
    FakeKnowledgeClient,
)


@pytest.mark.asyncio
async def test_orchestrator_runs_all_graph_modules():
    context = GraphContext(
        evidence_client=FakeEvidenceClient(
            [
                EvidenceItem(
                    evidence_id="ev_1",
                    evidence_type="log",
                    title="Redis timeout",
                    summary="redis dependency timeout happened",
                    source="test",
                )
            ]
        ),
        knowledge_client=FakeKnowledgeClient(
            [
                SimilarCase(
                    case_id="case_1",
                    title="Redis timeout case",
                    summary="Redis timeout caused service degradation",
                    root_cause="redis timeout",
                    resolution="increase timeout and check pool",
                    score=0.9,
                )
            ]
        ),
        checkpoint_client=FakeCheckpointClient(),
    )

    response = await run_diagnosis_graph(
        DiagnosisRequest(
            tenant_id="tenant_1",
            incident_id="inc_1",
            title="Order service timeout",
            severity="high",
            description="Order service returned 5xx",
            alert_summary="Redis timeout",
            tags=["order-service", "redis"],
        ),
        context,
    )

    assert response.incident_id == "inc_1"
    assert response.root_cause == "redis timeout"
    assert response.confidence > 0.4
    assert response.risk_level == "high"
    assert len(response.evidence) == 1
    assert len(response.similar_cases) == 1
    assert response.metadata["graph_version"] == "phase7.2-multi-agent-collaboration"


@pytest.mark.asyncio
async def test_orchestrator_without_case_retrieval():
    context = GraphContext(
        evidence_client=FakeEvidenceClient(
            [
                EvidenceItem(
                    evidence_id="ev_1",
                    evidence_type="log",
                    title="Timeout",
                    summary="dependency timeout happened",
                    source="test",
                )
            ]
        ),
        knowledge_client=FakeKnowledgeClient(),
        checkpoint_client=FakeCheckpointClient(),
    )

    response = await run_diagnosis_graph(
        DiagnosisRequest(
            tenant_id="tenant_1",
            incident_id="inc_1",
            title="Order service timeout",
            severity="medium",
            enable_case_retrieval=False,
        ),
        context,
    )

    assert response.similar_cases == []
    assert "timeout" in response.root_cause.lower()


@pytest.mark.asyncio
async def test_orchestrator_runs_multi_agent_collaboration():
    context = GraphContext(
        evidence_client=FakeEvidenceClient(
            [
                EvidenceItem(
                    evidence_id="ev_1",
                    evidence_type="log",
                    title="Redis timeout",
                    summary="redis dependency timeout happened",
                    source="test",
                )
            ]
        ),
        knowledge_client=FakeKnowledgeClient(
            [
                SimilarCase(
                    case_id="case_1",
                    title="Redis timeout case",
                    summary="Redis timeout caused service degradation",
                    root_cause="redis timeout",
                    resolution="increase timeout and check pool",
                    score=0.9,
                )
            ]
        ),
        checkpoint_client=FakeCheckpointClient(),
    )

    response = await run_diagnosis_graph(
        DiagnosisRequest(
            tenant_id="tenant_1",
            incident_id="inc_1",
            title="Order service timeout",
            severity="high",
            tags=["redis"],
            enable_human_checkpoint=False,
            enable_multi_agent_collaboration=True,
        ),
        context,
    )

    assert response.root_cause == "redis timeout"
    assert len(response.agent_messages) == 5
    assert [message.role for message in response.agent_messages] == [
        "evidence_agent",
        "rca_agent",
        "runbook_agent",
        "safety_agent",
        "reviewer_agent",
    ]
    assert response.metadata["collaboration_mode"] == "multi_agent"
    assert len(response.runbook_candidates) >= 1


@pytest.mark.asyncio
async def test_resume_after_checkpoint_approved_uses_multi_agent_recommendation():
    checkpoint_client = ApprovedCheckpointClient()
    checkpoint_client.checkpoint.state_snapshot = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "title": "Order service timeout",
        "severity": "high",
        "description": None,
        "alert_summary": None,
        "tags": ["redis"],
        "enable_case_retrieval": True,
        "enable_runbook_recommendation": True,
        "enable_human_checkpoint": True,
        "enable_multi_agent_collaboration": True,
        "evidence": [],
        "similar_cases": [],
        "root_cause": "redis timeout",
        "confidence": 0.8,
        "risk_level": "high",
        "agent_messages": [],
        "metadata": {},
    }

    context = GraphContext(
        evidence_client=FakeEvidenceClient(),
        knowledge_client=FakeKnowledgeClient(),
        checkpoint_client=checkpoint_client,
    )

    response = await resume_diagnosis_graph(
        DiagnosisResumeRequest(
            tenant_id="tenant_1",
            checkpoint_id="agcp_1",
        ),
        context,
    )

    assert response.checkpoint_status == "approved"
    assert len(response.runbook_candidates) >= 1
    assert len(response.agent_messages) == 3
    assert response.agent_messages[-1].role == "reviewer_agent"
    assert response.metadata["collaboration_mode"] == "multi_agent"
