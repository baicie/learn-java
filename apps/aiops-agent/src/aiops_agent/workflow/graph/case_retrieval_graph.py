"""case_retrieval_graph: searches for similar historical incident cases."""

from __future__ import annotations

from aiops_agent.settings import settings
from aiops_agent.workflow.errors import ToolError
from aiops_agent.workflow.graph.context import GraphContext
from aiops_agent.workflow.graph.evidence_graph import build_evidence_query
from aiops_agent.workflow.graph.state import DiagnosisGraphState


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
            top_k=settings.workflow_max_similar_cases,
        )
    except ToolError:
        cases = []
    except Exception:
        cases = []

    state["similar_cases"] = cases[: settings.workflow_max_similar_cases]
    return state
