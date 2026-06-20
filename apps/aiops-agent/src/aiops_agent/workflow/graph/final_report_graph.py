"""Final report graph: assembles the DiagnosisResponse."""

from __future__ import annotations

from aiops_agent.settings import settings
from aiops_agent.workflow.graph.state import DiagnosisGraphState


def final_report_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
    title = state.get("title", "")
    root_cause = state.get("root_cause", "Root cause is not confirmed")
    confidence = float(state.get("confidence", 0.0))

    collaboration = (
        "multi-agent" if state.get("enable_multi_agent_collaboration", False) else "single-agent"
    )

    state["final_summary"] = (
        f"Incident '{title}' diagnosis completed by {collaboration} graph. "
        f"Root cause: {root_cause}. Confidence={confidence:.2f}."
    )

    if not state.get("next_steps"):
        state["next_steps"] = build_next_steps(state)

    metadata = state.get("metadata", {})
    metadata["graph_version"] = settings.workflow_graph_version
    metadata["graph_modules"] = [
        "evidence_graph",
        "case_retrieval_graph",
        "rca_graph",
        "multi_agent_graph",
        "human_checkpoint_graph",
        "runbook_graph",
        "safety_graph",
        "final_report_graph",
        "memory_graph",
    ]
    metadata["collaboration_mode"] = (
        "multi_agent" if state.get("enable_multi_agent_collaboration", False) else "single_agent"
    )
    state["metadata"] = metadata
    return state


def build_next_steps(state: DiagnosisGraphState) -> list[str]:
    if state.get("checkpoint_status") == "pending":
        return [
            "Human checkpoint is pending.",
            "Review the RCA checkpoint and approve or reject it.",
        ]

    if state.get("checkpoint_status") == "rejected":
        return [
            "Human checkpoint was rejected.",
            "Revise diagnosis or collect additional evidence.",
        ]

    if state.get("checkpoint_status") == "failed":
        return [
            "Checkpoint creation failed.",
            "Check Java checkpoint API or internal network policy.",
        ]

    steps = [
        "Review evidence and confirm root cause.",
        "Review recommended runbook candidates.",
    ]

    if state.get("enable_multi_agent_collaboration", False):
        steps.insert(0, "Review multi-agent message ledger.")

    if state.get("risk_level") in {"high", "critical"}:
        steps.append("Require human approval before any remediation.")

    if float(state.get("confidence", 0.0)) < 0.6:
        steps.append("Collect additional metrics, logs, and recent change events.")

    return steps
