"""rca_graph: root cause analysis based on evidence and similar cases."""

from __future__ import annotations

from aiops_agent.workflow.graph.state import DiagnosisGraphState


def analyze_rca_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
    evidence = state.get("evidence", [])
    cases = state.get("similar_cases", [])

    root_cause = infer_root_cause(state)
    confidence = infer_confidence(evidence_count=len(evidence), case_count=len(cases))

    state["root_cause"] = root_cause
    state["confidence"] = confidence
    return state


def infer_root_cause(state: DiagnosisGraphState) -> str:
    evidence = state.get("evidence", [])
    cases = state.get("similar_cases", [])

    if cases and cases[0].root_cause:
        return cases[0].root_cause

    for item in evidence:
        summary = item.summary.lower()
        if "timeout" in summary:
            return "Possible timeout or dependency latency issue"
        if "error rate" in summary or "5xx" in summary:
            return "Possible service error rate increase"
        if "cpu" in summary:
            return "Possible resource saturation"

    title = state.get("title", "").lower()
    if "timeout" in title:
        return "Possible timeout or dependency latency issue"

    return "Root cause is not confirmed"


def infer_confidence(evidence_count: int, case_count: int) -> float:
    score = 0.35
    score += min(evidence_count, 5) * 0.08
    score += min(case_count, 3) * 0.08
    return round(min(score, 0.9), 2)
