"""Compatibility mapping between the Java v1 contract and the Phase 7 workflow."""

from __future__ import annotations

from typing import Any

from aiops_agent.schemas import AlertContext, DiagnoseRequest, DiagnoseResponse
from aiops_agent.settings import Settings
from aiops_agent.workflow.contracts import (
    DiagnosisRequest as WorkflowDiagnosisRequest,
)
from aiops_agent.workflow.contracts import (
    DiagnosisResponse as WorkflowDiagnosisResponse,
)


def to_workflow_request(
    request: DiagnoseRequest,
    settings: Settings,
) -> WorkflowDiagnosisRequest:
    incident = request.incident
    description_parts = [incident.summary]
    if request.rca is not None:
        description_parts.extend([request.rca.summary, request.rca.suspectedRootCause])

    alert_titles = [alert.title for alert in request.alerts if alert.title]

    return WorkflowDiagnosisRequest(
        tenant_id=request.tenantId,
        incident_id=request.incidentId,
        title=incident.title or f"Incident {request.incidentId}",
        severity=_normalize_severity(incident.severity),
        description="\n".join(part for part in description_parts if part) or None,
        alert_summary="; ".join(alert_titles) or None,
        tags=_derive_tags(request),
        enable_case_retrieval=settings.workflow_case_retrieval_enabled,
        enable_runbook_recommendation=True,
        enable_human_checkpoint=settings.workflow_human_checkpoint_enabled,
        enable_multi_agent_collaboration=settings.workflow_multi_agent_enabled,
        enable_agent_memory=settings.workflow_memory_enabled,
        enable_agent_memory_write=settings.workflow_memory_write_enabled,
    )


def to_contract_response(
    request: DiagnoseRequest,
    result: WorkflowDiagnosisResponse,
    settings: Settings,
) -> DiagnoseResponse:
    workflow_raw = result.model_dump(mode="json")
    workflow_raw["graphVersion"] = result.metadata.get(
        "graph_version",
        settings.workflow_graph_version,
    )

    return DiagnoseResponse(
        contractVersion=settings.contract_version,
        provider=settings.provider,
        model=settings.model,
        agentName=settings.agent_name,
        summary=result.summary,
        rootCause=result.root_cause,
        impact=_impact_summary(request, result),
        nextSteps=result.next_steps,
        runbookSuggestions=[candidate.title for candidate in result.runbook_candidates],
        risks=result.safety_notes,
        raw={
            "traceId": request.traceId,
            "generationMode": settings.normalized_generation_mode(),
            "fallbackReason": "",
            "workflow": workflow_raw,
            "metrics": _evidence_section(result, "metric"),
            "logs": _evidence_section(result, "log"),
            "changes": _evidence_section(result, "change"),
        },
    )


def _derive_tags(request: DiagnoseRequest) -> list[str]:
    incident = request.incident
    tags: list[str] = []

    tags.extend(
        [
            incident.source,
            incident.primaryAssetId,
            incident.aggregationKey,
            incident.severity,
            incident.status,
        ]
    )

    for alert in request.alerts:
        tags.extend(_alert_tags(alert))

    if request.rca is not None:
        tags.extend(
            [
                request.rca.modelVersion,
                "has-rca",
            ]
        )

    return _normalize_tags(tags)


def _alert_tags(alert: AlertContext) -> list[str | None]:
    return [
        alert.source,
        alert.severity,
        alert.assetId,
        alert.entityType,
        alert.entityName,
        alert.fingerprint,
    ]


def _normalize_tags(values: list[str | None]) -> list[str]:
    normalized: list[str] = []
    seen: set[str] = set()

    for value in values:
        tag = _normalize_tag(value)
        if not tag or tag in seen:
            continue
        seen.add(tag)
        normalized.append(tag)

    return normalized[:20]


def _normalize_tag(value: str | None) -> str:
    if value is None:
        return ""

    tag = value.strip().lower()
    if not tag:
        return ""

    chars: list[str] = []
    prev_dash = False

    for char in tag:
        if char.isalnum() or "\u4e00" <= char <= "\u9fff":
            chars.append(char)
            prev_dash = False
        else:
            if not prev_dash:
                chars.append("-")
                prev_dash = True

    return "".join(chars).strip("-")


def _normalize_severity(value: str | None) -> str:
    normalized = (value or "medium").strip().lower()
    aliases = {
        "warning": "medium",
        "average": "medium",
        "disaster": "critical",
    }
    normalized = aliases.get(normalized, normalized)
    if normalized not in {"info", "low", "medium", "high", "critical"}:
        return "medium"
    return normalized


def _impact_summary(
    request: DiagnoseRequest,
    result: WorkflowDiagnosisResponse,
) -> str:
    asset = request.incident.primaryAssetId or "unknown asset"
    return (
        f"Incident severity is {result.severity}; the primary impact may be "
        f"concentrated on {asset}."
    )


def _evidence_section(
    result: WorkflowDiagnosisResponse,
    evidence_type: str,
) -> dict[str, Any]:
    items = [
        item.model_dump(mode="json")
        for item in result.evidence
        if item.evidence_type == evidence_type
    ]
    return {"available": bool(items), "items": items}
