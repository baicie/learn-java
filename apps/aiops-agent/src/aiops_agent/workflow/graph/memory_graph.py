"""memory_graph: retrieve and write agent memories."""

from __future__ import annotations

from aiops_agent.settings import settings
from aiops_agent.workflow.contracts import AgentMemory
from aiops_agent.workflow.errors import ToolError
from aiops_agent.workflow.graph.evidence_graph import build_evidence_query
from aiops_agent.workflow.graph.state import DiagnosisGraphState
from aiops_agent.workflow.tools.memory_client import MemoryClient


async def retrieve_memory_node(
    state: DiagnosisGraphState, memory_client: MemoryClient | None
) -> DiagnosisGraphState:
    if not state.get("enable_agent_memory", False):
        state["memories"] = []
        return state

    client = memory_client or MemoryClient()
    query = build_evidence_query(state)

    try:
        memories = await client.search_memories(
            tenant_id=state["tenant_id"],
            query=query,
            scope_type="tenant",
            scope_id=None,
            tags=state.get("tags", []),
            memory_types=[],
            top_k=settings.workflow_max_memories,
        )
    except ToolError as exc:
        state["memories"] = []
        state["safety_notes"] = state.get("safety_notes", []) + [
            f"Memory retrieval failed: {exc.message}"
        ]
        return state
    except Exception as exc:
        state["memories"] = []
        state["safety_notes"] = state.get("safety_notes", []) + [f"Memory retrieval failed: {exc}"]
        return state

    state["memories"] = memories[: settings.workflow_max_memories]
    return state


async def write_memory_node(
    state: DiagnosisGraphState, memory_client: MemoryClient | None
) -> DiagnosisGraphState:
    if not state.get("enable_agent_memory_write", False):
        state["memory_write_status"] = "skipped"
        return state

    if not _should_write_memory(state):
        state["memory_write_status"] = "rejected_by_policy"
        return state

    client = memory_client or MemoryClient()

    try:
        await client.create_memory(
            tenant_id=state["tenant_id"],
            scope_type="tenant",
            scope_id=None,
            memory_type="root_cause_pattern",
            source_type="diagnosis",
            source_id=state["incident_id"],
            title=f"Root cause pattern: {state.get('title', state['incident_id'])}",
            content=_build_memory_content(state),
            tags=state.get("tags", []),
            confidence=float(state.get("confidence", 0.0)),
        )
    except ToolError as exc:
        state["memory_write_status"] = "failed"
        state["safety_notes"] = state.get("safety_notes", []) + [
            f"Memory write failed: {exc.message}"
        ]
        return state
    except Exception as exc:
        state["memory_write_status"] = "failed"
        state["safety_notes"] = state.get("safety_notes", []) + [f"Memory write failed: {exc}"]
        return state

    state["memory_write_status"] = "created"
    return state


def enrich_root_cause_with_memory(
    root_cause: str,
    memories: list[AgentMemory],
) -> str:
    if not memories:
        return root_cause

    top = memories[0]
    if top.score < 0.5:
        return root_cause

    return f"{root_cause}. Related memory: {top.title}"


def _should_write_memory(state: DiagnosisGraphState) -> bool:
    confidence = float(state.get("confidence", 0.0))
    root_cause = state.get("root_cause", "")

    if confidence < 0.60:
        return False

    if not root_cause or root_cause == "Root cause is not confirmed":
        return False

    if state.get("checkpoint_status") in {"pending", "rejected", "failed"}:
        return False

    return True


def _build_memory_content(state: DiagnosisGraphState) -> str:
    runbooks = state.get("runbook_candidates", [])
    runbook_titles = [item.title for item in runbooks[:3]]

    return (
        f"Incident: {state.get('title', '')}\n"
        f"Severity: {state.get('severity', 'medium')}\n"
        f"Root Cause: {state.get('root_cause', '')}\n"
        f"Confidence: {state.get('confidence', 0.0)}\n"
        f"Risk Level: {state.get('risk_level', 'medium')}\n"
        f"Suggested Runbooks: {', '.join(runbook_titles)}\n"
    )
