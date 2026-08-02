"""Orchestrator: assembles all sub-graphs into the diagnosis workflow."""

from __future__ import annotations

from langgraph.graph import END, StateGraph

from aiops_agent.workflow.contracts import (
    DiagnosisRequest,
    DiagnosisResponse,
    DiagnosisResumeRequest,
)
from aiops_agent.workflow.graph.case_retrieval_graph import retrieve_cases_node
from aiops_agent.workflow.graph.context import GraphContext
from aiops_agent.workflow.graph.evidence_graph import fetch_evidence_node
from aiops_agent.workflow.graph.final_report_graph import final_report_node
from aiops_agent.workflow.graph.human_checkpoint_graph import human_checkpoint_node
from aiops_agent.workflow.graph.memory_graph import retrieve_memory_node, write_memory_node
from aiops_agent.workflow.graph.multi_agent_graph import (
    multi_agent_rca_node,
    multi_agent_recommendation_node,
)
from aiops_agent.workflow.graph.rca_graph import analyze_rca_node
from aiops_agent.workflow.graph.runbook_graph import recommend_runbook_node
from aiops_agent.workflow.graph.safety_graph import safety_review_node
from aiops_agent.workflow.graph.state import DiagnosisGraphState


def build_diagnosis_graph(context: GraphContext):
    graph = StateGraph(DiagnosisGraphState)

    async def memory_retrieval_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
        return await retrieve_memory_node(state, context.memory_client)

    async def memory_write_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
        return await write_memory_node(state, context.memory_client)

    async def evidence_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
        return await fetch_evidence_node(state, context)

    async def case_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
        return await retrieve_cases_node(state, context)

    async def checkpoint_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
        return await human_checkpoint_node(state, context)

    graph.add_node("memory_retrieval", memory_retrieval_node)
    graph.add_node("evidence", evidence_node)
    graph.add_node("case_retrieval", case_node)
    graph.add_node("rca", analyze_rca_node)
    graph.add_node("multi_agent_rca", multi_agent_rca_node)
    graph.add_node("human_checkpoint", checkpoint_node)
    graph.add_node("runbook", recommend_runbook_node)
    graph.add_node("multi_agent_recommendation", multi_agent_recommendation_node)
    graph.add_node("safety", safety_review_node)
    graph.add_node("final_report", final_report_node)
    graph.add_node("memory_write", memory_write_node)

    graph.set_entry_point("memory_retrieval")
    graph.add_edge("memory_retrieval", "evidence")
    graph.add_edge("evidence", "case_retrieval")

    graph.add_conditional_edges(
        "case_retrieval",
        _route_to_rca,
        {
            "multi_agent_rca": "multi_agent_rca",
            "rca": "rca",
        },
    )

    graph.add_edge("rca", "human_checkpoint")
    graph.add_edge("multi_agent_rca", "human_checkpoint")

    graph.add_conditional_edges(
        "human_checkpoint",
        _route_after_checkpoint,
        {
            "multi_agent_recommendation": "multi_agent_recommendation",
            "runbook": "runbook",
            "final_report": "final_report",
        },
    )

    graph.add_edge("runbook", "safety")
    graph.add_edge("safety", "final_report")
    graph.add_edge("multi_agent_recommendation", "final_report")
    graph.add_edge("final_report", "memory_write")
    graph.add_edge("memory_write", END)

    return graph.compile()


def build_resume_graph(context: GraphContext):
    graph = StateGraph(DiagnosisGraphState)

    async def memory_write_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
        return await write_memory_node(state, context.memory_client)

    graph.add_node("resume_router", _identity_node)
    graph.add_node("runbook", recommend_runbook_node)
    graph.add_node("multi_agent_recommendation", multi_agent_recommendation_node)
    graph.add_node("safety", safety_review_node)
    graph.add_node("final_report", final_report_node)
    graph.add_node("memory_write", memory_write_node)

    graph.set_entry_point("resume_router")
    graph.add_conditional_edges(
        "resume_router",
        _route_after_resume,
        {
            "multi_agent_recommendation": "multi_agent_recommendation",
            "runbook": "runbook",
        },
    )
    graph.add_edge("runbook", "safety")
    graph.add_edge("safety", "final_report")
    graph.add_edge("multi_agent_recommendation", "final_report")
    graph.add_edge("final_report", "memory_write")
    graph.add_edge("memory_write", END)

    return graph.compile()


async def run_diagnosis_graph(
    request: DiagnosisRequest,
    context: GraphContext,
) -> DiagnosisResponse:
    app = build_diagnosis_graph(context)
    initial_state: DiagnosisGraphState = {
        "tenant_id": request.tenant_id,
        "incident_id": request.incident_id,
        "diagnosis_id": request.diagnosis_id,
        "trace_id": request.trace_id,
        "title": request.title,
        "severity": request.severity,
        "description": request.description,
        "alert_summary": request.alert_summary,
        "tags": request.tags,
        "primary_asset_id": request.primary_asset_id,
        "started_at": request.started_at,
        "last_seen_at": request.last_seen_at,
        "enable_case_retrieval": request.enable_case_retrieval,
        "enable_runbook_recommendation": request.enable_runbook_recommendation,
        "enable_human_checkpoint": request.enable_human_checkpoint,
        "enable_multi_agent_collaboration": request.enable_multi_agent_collaboration,
        "enable_agent_memory": request.enable_agent_memory,
        "enable_agent_memory_write": request.enable_agent_memory_write,
        "checkpoint_required": False,
        "checkpoint": None,
        "checkpoint_status": None,
        "resume_token": None,
        "evidence": [],
        "similar_cases": [],
        "memories": [],
        "runbook_candidates": [],
        "safety_notes": [],
        "next_steps": [],
        "agent_messages": [],
        "memory_write_status": None,
        "metadata": {},
    }

    final_state = await app.ainvoke(initial_state)
    return _to_response(final_state)


async def resume_diagnosis_graph(
    request: DiagnosisResumeRequest,
    context: GraphContext,
) -> DiagnosisResponse:
    checkpoint_client = context.checkpoint_client
    if checkpoint_client is None:
        from aiops_agent.workflow.tools.checkpoint_client import CheckpointClient

        checkpoint_client = CheckpointClient()

    checkpoint = await checkpoint_client.get_checkpoint(
        tenant_id=request.tenant_id,
        checkpoint_id=request.checkpoint_id,
        resume_token=request.resume_token,
    )

    expected_context = {
        "tenant_id": request.tenant_id,
        "incident_id": request.incident_id,
        "diagnosis_id": request.diagnosis_id,
        "trace_id": request.trace_id,
    }
    if any(
        checkpoint.state_snapshot.get(name) != value
        for name, value in expected_context.items()
    ):
        raise ValueError("checkpoint context does not match diagnosis grant context")

    state = DiagnosisGraphState(**checkpoint.state_snapshot)
    state["checkpoint"] = checkpoint.checkpoint_id
    state["resume_token"] = checkpoint.resume_token
    state["checkpoint_status"] = checkpoint.status
    state["checkpoint_required"] = False
    state.setdefault("agent_messages", [])
    state.setdefault("memories", [])
    state.setdefault("memory_write_status", None)

    if checkpoint.status == "rejected":
        state["runbook_candidates"] = []
        state["safety_notes"] = state.get("safety_notes", []) + [
            "Human checkpoint rejected. Runbook recommendation is stopped."
        ]
        state["next_steps"] = ["Revise diagnosis or collect additional evidence."]
        final_report_node(state)
        return _to_response(state)

    if checkpoint.status != "approved":
        state["checkpoint_required"] = True
        state["safety_notes"] = state.get("safety_notes", []) + [
            f"Checkpoint is not approved. Current status={checkpoint.status}."
        ]
        final_report_node(state)
        return _to_response(state)

    app = build_resume_graph(context)
    final_state = await app.ainvoke(state)
    return _to_response(final_state)


def _route_to_rca(state: DiagnosisGraphState) -> str:
    if state.get("enable_multi_agent_collaboration", False):
        return "multi_agent_rca"
    return "rca"


def _route_after_checkpoint(state: DiagnosisGraphState) -> str:
    status = state.get("checkpoint_status")
    if status not in {"approved", "skipped"}:
        return "final_report"

    if state.get("enable_multi_agent_collaboration", False):
        return "multi_agent_recommendation"

    return "runbook"


def _route_after_resume(state: DiagnosisGraphState) -> str:
    if state.get("enable_multi_agent_collaboration", False):
        return "multi_agent_recommendation"
    return "runbook"


def _identity_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
    return state


def _to_response(final_state: DiagnosisGraphState) -> DiagnosisResponse:
    return DiagnosisResponse(
        tenant_id=final_state["tenant_id"],
        incident_id=final_state["incident_id"],
        summary=final_state.get("final_summary", ""),
        root_cause=final_state.get("root_cause", "Root cause is not confirmed"),
        confidence=float(final_state.get("confidence", 0.0)),
        severity=final_state.get("severity", "medium"),
        risk_level=final_state.get("risk_level", "medium"),
        evidence=final_state.get("evidence", []),
        similar_cases=final_state.get("similar_cases", []),
        runbook_candidates=final_state.get("runbook_candidates", []),
        safety_notes=final_state.get("safety_notes", []),
        next_steps=final_state.get("next_steps", []),
        checkpoint_required=bool(final_state.get("checkpoint_required", False)),
        checkpoint_id=final_state.get("checkpoint"),
        checkpoint_status=final_state.get("checkpoint_status"),
        agent_messages=final_state.get("agent_messages", []),
        memories=final_state.get("memories", []),
        memory_write_status=final_state.get("memory_write_status"),
        metadata=final_state.get("metadata", {}),
    )
