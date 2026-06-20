"""Tests for the orchestrator: end-to-end graph execution."""

from __future__ import annotations

import pytest

from app.agent.contracts import DiagnosisRequest, EvidenceItem, SimilarCase
from app.agent.graph.context import GraphContext
from app.agent.graph.orchestrator import run_diagnosis_graph
from tests.fakes import FakeEvidenceClient, FakeKnowledgeClient


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
    assert response.metadata["graph_version"] == "phase7.0-modular-graph"


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
