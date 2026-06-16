from __future__ import annotations

from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph

from aiops_agent.eval import evaluate_diagnosis
from aiops_agent.evidence import EvidenceClient, create_evidence_client, unavailable_bundle
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
    safety_guard,
    search_runbooks_stub,
    summarize_incident_context,
)
from aiops_agent.trace import AgentTracer


class DiagnosisState(TypedDict, total=False):
    request: DiagnoseRequest
    incident_summary: dict[str, Any]
    alert_analysis: dict[str, Any]
    rca_analysis: dict[str, Any]
    metrics: dict[str, Any]
    logs: dict[str, Any]
    changes: dict[str, Any]
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


def query_evidence(settings: Settings, evidence_client: EvidenceClient | None = None):
    def _node(state: DiagnosisState) -> DiagnosisState:
        client = evidence_client or create_evidence_client(settings)

        try:
            bundle = client.query(state["request"])
        except Exception as exc:
            bundle = unavailable_bundle(
                f"Evidence client failed: {type(exc).__name__}: {exc}"
            )

        return {
            "metrics": bundle.metrics,
            "logs": bundle.logs,
            "changes": bundle.changes,
        }

    return _node


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

    metrics = state.get("metrics", {})
    logs = state.get("logs", {})
    changes = state.get("changes", {})

    root_cause = (
        _root_cause_from_change_evidence(changes)
        or rca.get("rootCause")
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

    metric_hint = _metric_hint(metrics)
    log_hint = _log_hint(logs)
    change_hint = _change_hint(changes)

    summary = (
        f"Incident {request.incidentId} is {incident.get('status', 'unknown')} "
        f"with severity {incident.get('severity', alerts.get('topSeverity', 'info'))}. "
        f"{alerts.get('count', 0)} linked alert(s) were analyzed. "
        f"{metric_hint} {log_hint} {change_hint}"
    ).strip()

    impact = (
        f"The primary impact may be concentrated on {dominant_asset}. "
        "Downstream services may be affected if this asset is part of a dependency path."
    )

    next_steps = [
        f"Confirm whether the dominant fingerprint `{dominant_fingerprint}` is still firing.",
        f"Check the primary asset `{dominant_asset}` around the incident start time.",
        "Compare metrics before and after incident detection.",
        "Review error log patterns around the incident window.",
        "Review recent deployments, restarts, configuration changes, and dependency health.",
        "Validate RCA and evidence before taking remediation action.",
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
        "metrics": metrics,
        "logs": logs,
        "changes": changes,
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
            changes=state.get("changes", {}),
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
                    "metrics": state.get("metrics", {}),
                    "logs": state.get("logs", {}),
                    "changes": state.get("changes", {}),
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


def build_diagnosis_graph(
    settings: Settings,
    llm_client: LlmClient | None = None,
    evidence_client: EvidenceClient | None = None,
    tracer: AgentTracer | None = None,
):
    graph = StateGraph(DiagnosisState)
    active_tracer = tracer

    def node(name: str, step_type: str, func):
        if active_tracer is None:
            return func
        return active_tracer.wrap(name, step_type, func)

    graph.add_node("load_context", node("load_context", "node", load_context))
    graph.add_node("analyze_alerts", node("analyze_alerts", "node", analyze_alerts))
    graph.add_node("analyze_rca", node("analyze_rca", "node", analyze_rca))
    graph.add_node("query_evidence", node("query_evidence", "tool", query_evidence(settings, evidence_client)))
    graph.add_node("search_runbooks", node("search_runbooks", "tool", search_runbooks))
    graph.add_node("safety_check", node("safety_check", "safety", safety_check))
    graph.add_node("generate_diagnosis", node("generate_diagnosis", "llm" if settings.normalized_generation_mode() == "openai-compatible" else "node", generate_diagnosis(settings, llm_client)))

    graph.add_edge(START, "load_context")
    graph.add_edge("load_context", "analyze_alerts")
    graph.add_edge("analyze_alerts", "analyze_rca")
    graph.add_edge("analyze_rca", "query_evidence")
    graph.add_edge("query_evidence", "search_runbooks")
    graph.add_edge("search_runbooks", "safety_check")
    graph.add_edge("safety_check", "generate_diagnosis")
    graph.add_edge("generate_diagnosis", END)

    return graph.compile()


def run_diagnosis_graph(
    request: DiagnoseRequest,
    settings: Settings,
    llm_client: LlmClient | None = None,
    evidence_client: EvidenceClient | None = None,
) -> DiagnoseResponse:
    tracer = AgentTracer(settings, request, enabled=settings.trace_enabled)

    compiled = build_diagnosis_graph(settings, llm_client, evidence_client, tracer)
    result = compiled.invoke({"request": request})

    diagnosis = result.get("diagnosis")
    if not isinstance(diagnosis, DiagnoseResponse):
        raise RuntimeError("diagnosis graph did not return DiagnoseResponse")

    return _attach_observability(diagnosis, tracer, settings)


def _attach_observability(
    diagnosis: DiagnoseResponse,
    tracer: AgentTracer,
    settings: Settings,
) -> DiagnoseResponse:
    raw = dict(diagnosis.raw or {})
    safety = raw.get("safety") or {}
    fallback_reason = str(raw.get("fallbackReason") or "")

    agent_run = tracer.finish(
        status="completed",
        provider=diagnosis.provider,
        model=diagnosis.model,
        fallback_reason=fallback_reason,
        safety=safety,
    )

    if agent_run:
        raw["agentRun"] = agent_run

    if settings.eval_enabled:
        raw["agentEval"] = evaluate_diagnosis(diagnosis.model_copy(update={"raw": raw}))

    return diagnosis.model_copy(update={"raw": raw})


def _metric_hint(metrics: dict[str, Any]) -> str:
    if metrics.get("available"):
        count = len(metrics.get("series") or [])
        return f"{count} metric series were available."
    return "Metric evidence is unavailable."


def _log_hint(logs: dict[str, Any]) -> str:
    if logs.get("available"):
        count = len(logs.get("patterns") or [])
        return f"{count} log pattern(s) were found."
    return "Log evidence is unavailable."


def _change_hint(changes: dict[str, Any]) -> str:
    if changes.get("available"):
        count = len(changes.get("events") or [])
        return f"{count} recent change event(s) were found."
    return "Change evidence is unavailable."


def _root_cause_from_change_evidence(changes: dict[str, Any]) -> str | None:
    if not changes.get("available"):
        return None

    events = changes.get("events") or []
    if not events:
        return None

    first = events[0]
    title = first.get("title") if isinstance(first, dict) else None
    change_type = first.get("changeType") if isinstance(first, dict) else None

    if title:
        return f"Recent {change_type or 'change'} may be related: {title}"

    return None
