"""Tests for multi-agent graph nodes."""

from __future__ import annotations

from app.agent.contracts import EvidenceItem, SimilarCase
from app.agent.graph.multi_agent_graph import (
    multi_agent_rca_node,
    multi_agent_recommendation_node,
)


def test_multi_agent_rca_node_adds_messages_and_root_cause():
    state = {
        "title": "Order service redis timeout",
        "evidence": [
            EvidenceItem(
                evidence_id="ev_1",
                evidence_type="log",
                title="Redis timeout",
                summary="redis timeout happened",
            )
        ],
        "similar_cases": [
            SimilarCase(
                case_id="case_1",
                title="Redis timeout case",
                summary="summary",
                root_cause="redis timeout",
                score=0.9,
            )
        ],
        "agent_messages": [],
    }

    result = multi_agent_rca_node(state)

    assert result["root_cause"] == "redis timeout"
    assert result["confidence"] > 0.3
    assert len(result["agent_messages"]) == 2
    assert result["agent_messages"][0].role == "evidence_agent"
    assert result["agent_messages"][1].role == "rca_agent"


def test_multi_agent_recommendation_node_adds_runbook_safety_reviewer_messages():
    state = {
        "root_cause": "Possible timeout or dependency latency issue",
        "severity": "high",
        "confidence": 0.8,
        "enable_runbook_recommendation": True,
        "agent_messages": [],
    }

    result = multi_agent_recommendation_node(state)

    assert len(result["runbook_candidates"]) >= 1
    assert result["risk_level"] == "high"
    assert len(result["agent_messages"]) == 3
    assert result["agent_messages"][0].role == "runbook_agent"
    assert result["agent_messages"][1].role == "safety_agent"
    assert result["agent_messages"][2].role == "reviewer_agent"
