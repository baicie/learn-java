from __future__ import annotations

from aiops_agent.workflow.contracts import AgentMemory
from aiops_agent.workflow.graph.rca_graph import analyze_rca_node, infer_confidence


def test_single_agent_rca_uses_root_cause_memory_when_no_case_or_evidence():
    state = {
        "title": "Order service degraded",
        "evidence": [],
        "similar_cases": [],
        "memories": [
            AgentMemory(
                memory_id="agm_1",
                title="Redis timeout pattern",
                content="Order service had redis timeout before.",
                memory_type="root_cause_pattern",
                score=0.9,
                confidence=0.8,
                tags=["redis", "timeout"],
            )
        ],
    }

    result = analyze_rca_node(state)

    assert "recurring pattern" in result["root_cause"].lower()
    assert "Redis timeout pattern" in result["root_cause"]
    assert result["confidence"] > 0.35


def test_single_agent_rca_ignores_low_score_memory():
    state = {
        "title": "Order service degraded",
        "evidence": [],
        "similar_cases": [],
        "memories": [
            AgentMemory(
                memory_id="agm_1",
                title="Low confidence old memory",
                content="Old unrelated issue.",
                memory_type="root_cause_pattern",
                score=0.2,
                confidence=0.8,
                tags=[],
            )
        ],
    }

    result = analyze_rca_node(state)

    assert result["root_cause"] == "Root cause is not confirmed"


def test_infer_confidence_counts_memory_signal():
    without_memory = infer_confidence(evidence_count=0, case_count=0, memory_count=0)
    with_memory = infer_confidence(evidence_count=0, case_count=0, memory_count=2)

    assert with_memory > without_memory
