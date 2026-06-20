"""Human checkpoint graph: creates and manages human approval checkpoints."""

from __future__ import annotations

from typing import Any

from aiops_agent.workflow.contracts import EvidenceItem, SimilarCase
from aiops_agent.workflow.errors import ToolError
from aiops_agent.workflow.graph.context import GraphContext
from aiops_agent.workflow.graph.state import DiagnosisGraphState
from aiops_agent.workflow.tools.checkpoint_client import CheckpointClient


async def human_checkpoint_node(
    state: DiagnosisGraphState,
    context: GraphContext,
) -> DiagnosisGraphState:
    if not state.get("enable_human_checkpoint", False):
        state["checkpoint_required"] = False
        state["checkpoint"] = None
        state["checkpoint_status"] = "skipped"
        return state

    checkpoint_client = context.checkpoint_client or CheckpointClient()

    try:
        checkpoint = await checkpoint_client.create_checkpoint(
            tenant_id=state["tenant_id"],
            incident_id=state["incident_id"],
            title=f"Review RCA for {state.get('title', state['incident_id'])}",
            reason="Agent RCA requires human confirmation before runbook recommendation.",
            review_prompt=(
                "Please review the inferred root cause, confidence, evidence, "
                "and similar cases. Approve to continue runbook recommendation, "
                "or reject to stop this diagnosis flow."
            ),
            root_cause=state.get("root_cause", "Root cause is not confirmed"),
            confidence=float(state.get("confidence", 0.0)),
            risk_level=_infer_checkpoint_risk(state),
            state_snapshot=_snapshot_state(state),
        )
    except ToolError as exc:
        state["checkpoint_required"] = True
        state["checkpoint"] = None
        state["checkpoint_status"] = "failed"
        state["safety_notes"] = state.get("safety_notes", []) + [
            f"Checkpoint creation failed: {exc.message}"
        ]
        return state
    except Exception as exc:
        state["checkpoint_required"] = True
        state["checkpoint"] = None
        state["checkpoint_status"] = "failed"
        state["safety_notes"] = state.get("safety_notes", []) + [
            f"Checkpoint creation failed: {exc}"
        ]
        return state

    state["checkpoint_required"] = True
    state["checkpoint"] = checkpoint.checkpoint_id
    state["checkpoint_status"] = checkpoint.status
    state["resume_token"] = checkpoint.resume_token
    state["risk_level"] = _infer_checkpoint_risk(state)

    return state


def _infer_checkpoint_risk(state: DiagnosisGraphState) -> str:
    severity = state.get("severity", "medium")
    confidence = float(state.get("confidence", 0.0))

    if severity == "critical":
        return "critical"
    if severity == "high" and confidence >= 0.65:
        return "high"
    if severity in {"high", "medium"}:
        return "medium"
    return "low"


def _snapshot_state(state: DiagnosisGraphState) -> dict[str, Any]:
    snapshot: dict[str, Any] = {}

    for key, value in state.items():
        if key in {"checkpoint_required", "checkpoint", "checkpoint_status", "resume_token"}:
            continue

        if key == "evidence":
            snapshot[key] = [
                item.model_dump(mode="json") if isinstance(item, EvidenceItem) else item
                for item in value
            ]
            continue

        if key == "similar_cases":
            snapshot[key] = [
                item.model_dump(mode="json") if isinstance(item, SimilarCase) else item
                for item in value
            ]
            continue

        if key == "agent_messages":
            snapshot[key] = [
                item.model_dump(mode="json") if hasattr(item, "model_dump") else item
                for item in value
            ]
            continue

        snapshot[key] = value

    return snapshot
