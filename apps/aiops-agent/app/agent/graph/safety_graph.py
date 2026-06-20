"""safety_graph: risk assessment and forbidden-action filtering."""

from __future__ import annotations

from app.agent.contracts import RunbookCandidate
from app.agent.graph.state import DiagnosisGraphState


FORBIDDEN_ACTION_TYPES = {
    "shell",
    "ssh",
    "ansible",
    "webhook",
    "delete",
    "rollback",
    "restart",
}


def safety_review_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
    candidates = state.get("runbook_candidates", [])
    safe_candidates: list[RunbookCandidate] = []
    notes: list[str] = []

    for candidate in candidates:
        if candidate.action_type.lower() in FORBIDDEN_ACTION_TYPES:
            notes.append(
                f"Blocked executable action candidate: {candidate.title} ({candidate.action_type})"
            )
            continue
        safe_candidates.append(candidate)

    risk_level = infer_risk_level(state)

    notes.append(
        "Agent recommendation is advisory only. "
        "Execution must go through approval and runner."
    )

    state["runbook_candidates"] = safe_candidates
    state["risk_level"] = risk_level
    state["safety_notes"] = notes
    return state


def infer_risk_level(state: DiagnosisGraphState) -> str:
    severity = state.get("severity", "medium")
    confidence = state.get("confidence", 0.0)

    if severity == "critical":
        return "critical"
    if severity == "high" and confidence >= 0.50:
        return "high"
    if severity in {"high", "medium"}:
        return "medium"
    return "low"
