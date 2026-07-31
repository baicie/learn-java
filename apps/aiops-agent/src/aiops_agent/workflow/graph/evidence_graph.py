"""evidence_graph: fetches evidence (metrics, logs, changes) from the evidence API."""

from __future__ import annotations

from aiops_agent.settings import settings
from aiops_agent.workflow.contracts import EvidenceItem
from aiops_agent.workflow.errors import ToolError
from aiops_agent.workflow.graph.context import GraphContext
from aiops_agent.workflow.graph.state import DiagnosisGraphState


async def fetch_evidence_node(
    state: DiagnosisGraphState,
    context: GraphContext,
) -> DiagnosisGraphState:
    tenant_id = state["tenant_id"]
    incident_id = state["incident_id"]
    trace_id = state["trace_id"]

    try:
        evidence = await context.evidence_client.fetch_evidence(
            tenant_id,
            incident_id,
            trace_id,
            primary_asset_id=state.get("primary_asset_id"),
            started_at=state.get("started_at"),
            last_seen_at=state.get("last_seen_at"),
        )
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
    except Exception as exc:
        evidence = [
            EvidenceItem(
                evidence_id="evidence_tool_unexpected_error",
                evidence_type="tool_error",
                title="Evidence fetch failed",
                summary=str(exc),
                source="agent",
                metadata={"code": "EVIDENCE_TOOL_UNEXPECTED_ERROR"},
            )
        ]

    state["evidence"] = evidence[: settings.workflow_max_evidence_items]
    return state


def build_evidence_query(state: DiagnosisGraphState) -> str:
    parts = [
        state.get("title", ""),
        state.get("alert_summary") or "",
        state.get("description") or "",
        " ".join(state.get("tags", [])),
    ]
    return "\n".join(part for part in parts if part)
