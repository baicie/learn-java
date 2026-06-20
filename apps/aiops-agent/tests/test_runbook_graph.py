"""Tests for runbook_graph."""

from __future__ import annotations

from app.agent.graph.runbook_graph import recommend_runbook_node


def test_recommend_runbook_for_timeout_root_cause():
    state = {
        "root_cause": "Possible timeout or dependency latency issue",
        "enable_runbook_recommendation": True,
    }

    result = recommend_runbook_node(state)

    assert len(result["runbook_candidates"]) >= 1
    assert "latency" in result["runbook_candidates"][0].reason.lower()


def test_runbook_recommendation_can_be_disabled():
    state = {
        "root_cause": "timeout",
        "enable_runbook_recommendation": False,
    }

    result = recommend_runbook_node(state)

    assert result["runbook_candidates"] == []
