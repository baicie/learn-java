"""final_report_graph: assembles the final DiagnosisResponse."""

from __future__ import annotations

from app.agent.graph.state import DiagnosisGraphState
from app.agent.settings import settings


def final_report_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
    title = state.get("title", "")
    root_cause = state.get("root_cause", "Root cause is not confirmed")
    confidence = state.get("confidence", 0.0)

    state["final_summary"] = (
        f"Incident '{title}' diagnosis completed. "
        f"Root cause: {root_cause}. Confidence={confidence:.2f}."
    )

    state["next_steps"] = build_next_steps(state)

    metadata = state.get("metadata", {})
    metadata["graph_version"] = settings.graph_version
    metadata["graph_modules"] = [
        "evidence_graph",
        "case_retrieval_graph",
        "rca_graph",
        "runbook_graph",
        "safety_graph",
        "final_report_graph",
    ]
    state["metadata"] = metadata
    return state


def build_next_steps(state: DiagnosisGraphState) -> list[str]:
    steps = [
        "Review evidence and confirm root cause.",
        "Review recommended runbook candidates.",
    ]

    if state.get("risk_level") in {"high", "critical"}:
        steps.append("Require human approval before any remediation.")

    if state.get("confidence", 0.0) < 0.6:
        steps.append("Collect additional metrics, logs, and recent change events.")

    return steps
