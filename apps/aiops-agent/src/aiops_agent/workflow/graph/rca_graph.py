"""rca_graph: root cause analysis based on evidence, similar cases and memories."""

from __future__ import annotations

from aiops_agent.workflow.contracts import AgentMemory
from aiops_agent.workflow.graph.state import DiagnosisGraphState


def analyze_rca_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
    evidence = state.get("evidence", [])
    cases = state.get("similar_cases", [])
    memories = state.get("memories", [])

    root_cause = infer_root_cause(state)
    confidence = infer_confidence(
        evidence_count=len(evidence),
        case_count=len(cases),
        memory_count=len(memories),
    )

    state["root_cause"] = root_cause
    state["confidence"] = confidence
    return state


def infer_root_cause(state: DiagnosisGraphState) -> str:
    evidence = state.get("evidence", [])
    cases = state.get("similar_cases", [])
    memories = state.get("memories", [])

    for case in cases:
        if case.root_cause:
            return case.root_cause

    memory_root_cause = _root_cause_from_memory(memories)
    if memory_root_cause:
        return memory_root_cause

    for item in evidence:
        summary = item.summary.lower()
        if "timeout" in summary:
            return "Possible timeout or dependency latency issue"
        if "error rate" in summary or "5xx" in summary:
            return "Possible service error rate increase"
        if "cpu" in summary or "memory" in summary:
            return "Possible resource saturation"

    title = state.get("title", "").lower()
    if "timeout" in title:
        return "Possible timeout or dependency latency issue"
    if "5xx" in title or "error rate" in title:
        return "Possible service error rate increase"
    if "cpu" in title or "memory" in title:
        return "Possible resource saturation"

    return "Root cause is not confirmed"


def infer_confidence(
    evidence_count: int, case_count: int, memory_count: int = 0
) -> float:
    score = 0.35
    score += min(evidence_count, 5) * 0.08
    score += min(case_count, 3) * 0.08
    score += min(memory_count, 2) * 0.04
    return round(min(score, 0.9), 2)


def _root_cause_from_memory(memories: list[AgentMemory]) -> str | None:
    for memory in memories:
        if memory.memory_type == "root_cause_pattern" and memory.score >= 0.5:
            return f"Possible recurring pattern: {memory.title}"

    for memory in memories:
        if memory.memory_type in {"incident_summary", "service_behavior"} and memory.score >= 0.7:
            return f"Possible related memory: {memory.title}"

    return None
