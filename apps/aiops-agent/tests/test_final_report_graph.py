"""Tests for final_report_graph."""

from __future__ import annotations

from app.agent.graph.final_report_graph import final_report_node


def test_final_report_node_sets_summary_next_steps_and_metadata():
    state = {
        "title": "Order service timeout",
        "root_cause": "redis timeout",
        "confidence": 0.7,
        "risk_level": "high",
        "metadata": {},
    }

    result = final_report_node(state)

    assert "redis timeout" in result["final_summary"]
    assert "Require human approval" in " ".join(result["next_steps"])
    assert result["metadata"]["graph_version"] == "phase7.0-modular-graph"
    assert "evidence_graph" in result["metadata"]["graph_modules"]
