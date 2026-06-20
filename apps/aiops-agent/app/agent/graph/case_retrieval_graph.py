"""case_retrieval_graph: searches for similar historical incident cases."""

from __future__ import annotations

from app.agent.errors import ToolError
from app.agent.graph.context import GraphContext
from app.agent.graph.evidence_graph import build_evidence_query
from app.agent.graph.state import DiagnosisGraphState
from app.agent.settings import settings


async def retrieve_cases_node(
    state: DiagnosisGraphState,
    context: GraphContext,
) -> DiagnosisGraphState:
    if not state.get("enable_case_retrieval", True):
        state["similar_cases"] = []
        return state

    query = build_evidence_query(state)

    try:
        cases = await context.knowledge_client.search_cases(
            tenant_id=state["tenant_id"],
            query=query,
            tags=state.get("tags", []),
            top_k=settings.max_similar_cases,
        )
    except ToolError:
        cases = []

    state["similar_cases"] = cases[: settings.max_similar_cases]
    return state
