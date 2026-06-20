"""Tests for rca_graph."""

from __future__ import annotations

from aiops_agent.workflow.contracts import EvidenceItem, SimilarCase
from aiops_agent.workflow.graph.rca_graph import analyze_rca_node


def test_rca_prefers_similar_case_root_cause():
    state = {
        "title": "Order service error",
        "evidence": [
            EvidenceItem(
                evidence_id="ev_1",
                evidence_type="metric",
                title="Error rate",
                summary="5xx increased",
            )
        ],
        "similar_cases": [
            SimilarCase(
                case_id="case_1",
                title="Redis timeout",
                summary="Redis timeout",
                root_cause="redis connection timeout",
                score=0.9,
            )
        ],
    }

    result = analyze_rca_node(state)

    assert result["root_cause"] == "redis connection timeout"
    assert result["confidence"] > 0.4


def test_rca_uses_evidence_when_no_case():
    state = {
        "title": "Order service error",
        "evidence": [
            EvidenceItem(
                evidence_id="ev_1",
                evidence_type="log",
                title="Timeout",
                summary="dependency timeout happened",
            )
        ],
        "similar_cases": [],
    }

    result = analyze_rca_node(state)

    assert "timeout" in result["root_cause"].lower()
