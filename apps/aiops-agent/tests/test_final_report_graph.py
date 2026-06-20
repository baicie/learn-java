"""Tests for final_report_graph."""

from __future__ import annotations

from app.agent.graph.final_report_graph import final_report_node


def test_final_report_node_sets_summary_next_steps_and_metadata():
    state = {
        "title": "Order service timeout",
        "root_cause": "redis timeout",
        "confidence": 0.7,
        "risk_level": "high",
        "enable_multi_agent_collaboration": False,
        "metadata": {},
    }

    result = final_report_node(state)

    assert "redis timeout" in result["final_summary"]
    assert "Require human approval" in " ".join(result["next_steps"])
    assert result["metadata"]["graph_version"] == "phase7.2-multi-agent-collaboration"
    assert "evidence_graph" in result["metadata"]["graph_modules"]
    assert "multi_agent_graph" in result["metadata"]["graph_modules"]
    assert result["metadata"]["collaboration_mode"] == "single_agent"


def test_final_report_node_reports_multi_agent_collaboration():
    state = {
        "title": "Order service timeout",
        "root_cause": "redis timeout",
        "confidence": 0.7,
        "risk_level": "medium",
        "enable_multi_agent_collaboration": True,
        "metadata": {},
    }

    result = final_report_node(state)

    assert "multi-agent" in result["final_summary"]
    assert result["metadata"]["collaboration_mode"] == "multi_agent"
