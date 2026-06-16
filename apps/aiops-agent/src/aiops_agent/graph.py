from __future__ import annotations

from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph

from aiops_agent.llm import (
    LlmClient,
    OpenAiCompatibleLlmClient,
    diagnosis_response_from_draft,
    parse_diagnosis_json,
)
from aiops_agent.prompt import build_diagnosis_prompt
from aiops_agent.safety import apply_safety_boundary
from aiops_agent.schemas import DiagnoseRequest, DiagnoseResponse
from aiops_agent.settings import Settings
from aiops_agent.tools import (
    inspect_alerts,
    inspect_rca_evidence,
    query_logs_stub,
    query_metrics_stub,
    safety_guard,
    search_runbooks_stub,
    summarize_incident_context,
)


class DiagnosisState(TypedDict, total=False):
    request: DiagnoseRequest
    incident_summary: dict[str, Any]
    alert_analysis: dict[str, Any]
    rca_analysis: dict[str, Any]
    metrics: dict[str, Any]
    logs: dict[str, Any]
    runbook_suggestions: list[str]
    risks: list[str]
    diagnosis: DiagnoseResponse


def load_context(state: DiagnosisState) -> DiagnosisState:
    request = state["request"]
    return {
        "incident_summary": summarize_incident_context(request),
    }


def analyze_alerts(state: DiagnosisState) -> DiagnosisState:
    request = state["request"]
    return {
        "alert_analysis": inspect_alerts(request.alerts),
    }


def analyze_rca(state: DiagnosisState) -> DiagnosisState:
    request = state["request"]
    return {
        "rca_analysis": inspect_rca_evidence(request),
    }


def query_metrics(state: DiagnosisState) -> DiagnosisState:
    return {
        "metrics": query_metrics_stub(state["request"]),
    }


def query_logs(state: DiagnosisState) -> DiagnosisState:
    return {
        "logs": query_logs_stub(state["request"]),
    }


def search_runbooks(state: DiagnosisState) -> DiagnosisState:
    return {
        "runbook_suggestions": search_runbooks_stub(
            state["request"],
            state.get("alert_analysis", {}),
            state.get("rca_analysis", {}),
        )
    }


def safety_check(state: DiagnosisState) -> DiagnosisState:
    return {
        "risks": safety_guard(),
    }


def deterministic_diagnosis(
    state: DiagnosisState,
    settings: Settings,
    fallback_reason: str | None = None,
) -> DiagnoseResponse:
    request = state["request"]
    incident = state.get("incident_summary", {})
    alerts = state.get("alert_analysis", {})
    rca = state.get("rca_analysis", {})
    runbooks = state.get("runbook_suggestions", [])
    risks = state.get("risks", [])

    root_cause = (
        rca.get("rootCause")
        or incident.get("suspectedRootCause")
        or "No strong root cause has been confirmed. Start from the dominant alert and primary asset."
    )

    dominant_asset = (
        alerts.get("dominantAssetId")
        or incident.get("primaryAssetId")
        or "unknown asset"
    )

    dominant_fingerprint = (
        alerts.get("dominantFingerprint")
        or incident.get("aggregationKey")
        or "unknown fingerprint"
    )

    summary = (
        f"Incident {request.incidentId} is {incident.get('status', 'unknown')} "
        f"with severity {incident.get('severity', alerts.get('topSeverity', 'info'))}. "
        f"{alerts.get('count', 0)} linked alert(s) were analyzed."
    )

    impact = (
        f"The primary impact may be concentrated on {dominant_asset}. "
        "Downstream services may be affected if this asset is part of a dependency path."
    )

    next_steps = [
        f"Confirm whether the dominant fingerprint `{dominant_fingerprint}` is still firing.",
        f"Check the primary asset `{dominant_asset}` around the incident start time.",
        "Compare metrics before and after incident detection.",
        "Review recent deployments, restarts, configuration changes, and dependency health.",
        "Validate RCA evidence before taking remediation action.",
    ]

    raw = {
        "graph": "aegisops_diagnosis_graph",
        "contractVersion": settings.contract_version,
        "traceId": request.traceId,
        "generationMode": "deterministic",
        "fallbackReason": fallback_reason or "",
        "incident": incident,
        "alerts": alerts,
        "rca": rca,
        "metrics": state.get("metrics", {}),
        "logs": state.get("logs", {}),
    }

    response = DiagnoseResponse(
        contractVersion=settings.contract_version,
        provider=settings.provider,
        model=settings.model,
        agentName=settings.agent_name,
        summary=summary,
        rootCause=str(root_cause),
        impact=impact,
        nextSteps=next_steps,
        runbookSuggestions=runbooks,
        risks=risks,
        raw=raw,
    )

    return apply_safety_boundary(response)


def generate_diagnosis(settings: Settings, llm_client: LlmClient | None = None):
    def _node(state: DiagnosisState) -> DiagnosisState:
        if settings.normalized_generation_mode() != "openai-compatible":
            return {
                "diagnosis": deterministic_diagnosis(state, settings),
            }

        client = llm_client or OpenAiCompatibleLlmClient(settings)

        messages = build_diagnosis_prompt(
            request=state["request"],
            incident_summary=state.get("incident_summary", {}),
            alert_analysis=state.get("alert_analysis", {}),
            rca_analysis=state.get("rca_analysis", {}),
            metrics=state.get("metrics", {}),
            logs=state.get("logs", {}),
            runbook_suggestions=state.get("runbook_suggestions", []),
            risks=state.get("risks", []),
        )

        try:
            content = client.complete_json(messages)
            draft = parse_diagnosis_json(content)
            response = diagnosis_response_from_draft(
                draft,
                settings,
                raw={
                    "graph": "aegisops_diagnosis_graph",
                    "contractVersion": settings.contract_version,
                    "traceId": state["request"].traceId,
                    "generationMode": "openai-compatible",
                    "llmContent": content,
                },
                provider="openai-compatible",
            )

            return {
                "diagnosis": apply_safety_boundary(response),
            }
        except Exception as exc:
            return {
                "diagnosis": deterministic_diagnosis(
                    state,
                    settings,
                    fallback_reason=f"{type(exc).__name__}: {exc}",
                )
            }

    return _node


def build_diagnosis_graph(settings: Settings, llm_client: LlmClient | None = None):
    graph = StateGraph(DiagnosisState)

    graph.add_node("load_context", load_context)
    graph.add_node("analyze_alerts", analyze_alerts)
    graph.add_node("analyze_rca", analyze_rca)
    graph.add_node("query_metrics", query_metrics)
    graph.add_node("query_logs", query_logs)
    graph.add_node("search_runbooks", search_runbooks)
    graph.add_node("safety_check", safety_check)
    graph.add_node("generate_diagnosis", generate_diagnosis(settings, llm_client))

    graph.add_edge(START, "load_context")
    graph.add_edge("load_context", "analyze_alerts")
    graph.add_edge("analyze_alerts", "analyze_rca")
    graph.add_edge("analyze_rca", "query_metrics")
    graph.add_edge("query_metrics", "query_logs")
    graph.add_edge("query_logs", "search_runbooks")
    graph.add_edge("search_runbooks", "safety_check")
    graph.add_edge("safety_check", "generate_diagnosis")
    graph.add_edge("generate_diagnosis", END)

    return graph.compile()


def run_diagnosis_graph(
    request: DiagnoseRequest,
    settings: Settings,
    llm_client: LlmClient | None = None,
) -> DiagnoseResponse:
    compiled = build_diagnosis_graph(settings, llm_client)
    result = compiled.invoke({"request": request})
    diagnosis = result.get("diagnosis")
    if not isinstance(diagnosis, DiagnoseResponse):
        raise RuntimeError("diagnosis graph did not return DiagnoseResponse")
    return diagnosis
