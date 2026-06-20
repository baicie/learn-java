"""evidence_graph: fetches evidence (metrics, logs, changes) from the evidence API."""

from __future__ import annotations

from app.agent.contracts import EvidenceItem
from app.agent.errors import ToolError
from app.agent.graph.context import GraphContext
from app.agent.graph.state import DiagnosisGraphState
from app.agent.settings import settings


async def fetch_evidence_node(
    state: DiagnosisGraphState,
    context: GraphContext,
) -> DiagnosisGraphState:
    tenant_id = state["tenant_id"]
    incident_id = state["incident_id"]

    try:
        evidence = await context.evidence_client.fetch_evidence(tenant_id, incident_id)
    except ToolError as exc:
        evidence = [
            EvidenceItem(
                evidence_id="evidence_tool_error",
                evidence_type="tool_error",
                title="Evidence fetch failed",
                summary=exc.message,
                source="agent",
                metadata={"code": exc.code},
            )
        ]

    state["evidence"] = evidence[: settings.max_evidence_items]
    return state


def build_evidence_query(state: DiagnosisGraphState) -> str:
    parts = [
        state.get("title", ""),
        state.get("alert_summary") or "",
        state.get("description") or "",
        " ".join(state.get("tags", [])),
    ]
    return "\n".join(part for part in parts if part)
