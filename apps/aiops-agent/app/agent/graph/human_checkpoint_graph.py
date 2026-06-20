"""Human checkpoint graph: creates and manages human approval checkpoints."""

from __future__ import annotations

from typing import Any

from app.agent.contracts import AgentCheckpoint
from app.agent.graph.context import GraphContext
from app.agent.graph.state import DiagnosisGraphState


async def human_checkpoint_node(
    state: DiagnosisGraphState,
    context: GraphContext,
) -> DiagnosisGraphState:
    if not state.get("enable_human_checkpoint", False):
        state["checkpoint_required"] = False
        state["checkpoint_id"] = None
        state["checkpoint_status"] = "skipped"
        return state

    checkpoint = await context.checkpoint_client.create_checkpoint(
        tenant_id=state["tenant_id"],
        incident_id=state["incident_id"],
        title=state.get("title", ""),
        reason="RCA review checkpoint",
        review_prompt="Please review the root cause analysis and evidence.",
        root_cause=state.get("root_cause", "Root cause is not confirmed"),
        confidence=float(state.get("confidence", 0.0)),
        risk_level=state.get("risk_level", "medium"),
        state_snapshot=_snapshot_state(state),
    )

    state["checkpoint_required"] = True
    state["checkpoint"] = checkpoint.checkpoint_id
    state["checkpoint_status"] = checkpoint.status
    state["resume_token"] = checkpoint.resume_token

    return state


def _snapshot_state(state: DiagnosisGraphState) -> dict[str, Any]:
    return {
        key: value
        for key, value in state.items()
        if key
        not in {
            "checkpoint_required",
            "checkpoint",
            "checkpoint_status",
            "resume_token",
        }
    }
