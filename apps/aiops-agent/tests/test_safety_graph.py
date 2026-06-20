"""Tests for safety_graph."""

from __future__ import annotations

from aiops_agent.workflow.contracts import RunbookCandidate
from aiops_agent.workflow.graph.safety_graph import safety_review_node


def test_safety_blocks_executable_action_candidates():
    state = {
        "severity": "high",
        "confidence": 0.8,
        "runbook_candidates": [
            RunbookCandidate(
                title="Restart service",
                action_type="ssh",
                target_type="service",
                risk_level="high",
                reason="unsafe direct action",
            ),
            RunbookCandidate(
                title="Collect logs",
                action_type="manual",
                target_type="service",
                risk_level="low",
                reason="safe manual action",
            ),
        ],
    }

    result = safety_review_node(state)

    assert len(result["runbook_candidates"]) == 1
    assert result["runbook_candidates"][0].action_type == "manual"
    assert result["risk_level"] == "high"
    assert any("Blocked executable action" in note for note in result["safety_notes"])
