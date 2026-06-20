"""Orchestrator: assembles all sub-graphs into the diagnosis workflow."""

from __future__ import annotations

from langgraph.graph import END, StateGraph

from app.agent.contracts import DiagnosisRequest, DiagnosisResponse
from app.agent.graph.case_retrieval_graph import retrieve_cases_node
from app.agent.graph.context import GraphContext
from app.agent.graph.evidence_graph import fetch_evidence_node
from app.agent.graph.final_report_graph import final_report_node
from app.agent.graph.rca_graph import analyze_rca_node
from app.agent.graph.runbook_graph import recommend_runbook_node
from app.agent.graph.safety_graph import safety_review_node
from app.agent.graph.state import DiagnosisGraphState


def build_diagnosis_graph(context: GraphContext):
    graph = StateGraph(DiagnosisGraphState)

    async def evidence_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
        return await fetch_evidence_node(state, context)

    async def case_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
        return await retrieve_cases_node(state, context)

    graph.add_node("evidence", evidence_node)
    graph.add_node("case_retrieval", case_node)
    graph.add_node("rca", analyze_rca_node)
    graph.add_node("runbook", recommend_runbook_node)
    graph.add_node("safety", safety_review_node)
    graph.add_node("final_report", final_report_node)

    graph.set_entry_point("evidence")
    graph.add_edge("evidence", "case_retrieval")
    graph.add_edge("case_retrieval", "rca")
    graph.add_edge("rca", "runbook")
    graph.add_edge("runbook", "safety")
    graph.add_edge("safety", "final_report")
    graph.add_edge("final_report", END)

    return graph.compile()


async def run_diagnosis_graph(
    request: DiagnosisRequest,
    context: GraphContext,
) -> DiagnosisResponse:
    app = build_diagnosis_graph(context)
    initial_state: DiagnosisGraphState = {
        "tenant_id": request.tenant_id,
        "incident_id": request.incident_id,
        "title": request.title,
        "severity": request.severity,
        "description": request.description,
        "alert_summary": request.alert_summary,
        "tags": request.tags,
        "enable_case_retrieval": request.enable_case_retrieval,
        "enable_runbook_recommendation": request.enable_runbook_recommendation,
        "evidence": [],
        "similar_cases": [],
        "runbook_candidates": [],
        "safety_notes": [],
        "next_steps": [],
        "metadata": {},
    }

    final_state = await app.ainvoke(initial_state)

    return DiagnosisResponse(
        tenant_id=request.tenant_id,
        incident_id=request.incident_id,
        summary=final_state.get("final_summary", ""),
        root_cause=final_state.get("root_cause", "Root cause is not confirmed"),
        confidence=float(final_state.get("confidence", 0.0)),
        severity=request.severity,
        risk_level=final_state.get("risk_level", "medium"),
        evidence=final_state.get("evidence", []),
        similar_cases=final_state.get("similar_cases", []),
        runbook_candidates=final_state.get("runbook_candidates", []),
        safety_notes=final_state.get("safety_notes", []),
        next_steps=final_state.get("next_steps", []),
        metadata=final_state.get("metadata", {}),
    )
