from __future__ import annotations

from collections import Counter
from typing import Any

from aiops_agent.schemas import AlertContext, DiagnoseRequest

SEVERITY_WEIGHT = {
    "info": 10,
    "low": 20,
    "warning": 30,
    "critical": 40,
    "disaster": 50,
}


def severity_weight(severity: str | None) -> int:
    if not severity:
        return 10
    return SEVERITY_WEIGHT.get(severity.lower(), 10)


def summarize_incident_context(request: DiagnoseRequest) -> dict[str, Any]:
    incident = request.incident
    return {
        "incidentId": incident.id,
        "title": incident.title or "",
        "severity": incident.severity or "info",
        "status": incident.status or "unknown",
        "alertCount": incident.alertCount,
        "primaryAssetId": incident.primaryAssetId or "",
        "aggregationKey": incident.aggregationKey or "",
        "suspectedRootCause": incident.suspectedRootCause or "",
        "confidence": incident.confidence or 0,
    }


def inspect_alerts(alerts: list[AlertContext]) -> dict[str, Any]:
    if not alerts:
        return {
            "count": 0,
            "topSeverity": "info",
            "topAlertTitle": "",
            "dominantFingerprint": "",
            "dominantFingerprintCount": 0,
            "dominantAssetId": "",
            "dominantAssetCount": 0,
        }

    top = max(alerts, key=lambda item: severity_weight(item.severity))
    fingerprint_counts = Counter(a.fingerprint for a in alerts if a.fingerprint)
    asset_counts = Counter(a.assetId for a in alerts if a.assetId)

    return {
        "count": len(alerts),
        "topSeverity": top.severity or "info",
        "topAlertTitle": top.title or "",
        "dominantFingerprint": fingerprint_counts.most_common(1)[0][0]
        if fingerprint_counts
        else "",
        "dominantFingerprintCount": fingerprint_counts.most_common(1)[0][1]
        if fingerprint_counts
        else 0,
        "dominantAssetId": asset_counts.most_common(1)[0][0] if asset_counts else "",
        "dominantAssetCount": asset_counts.most_common(1)[0][1] if asset_counts else 0,
    }


def inspect_rca_evidence(request: DiagnoseRequest) -> dict[str, Any]:
    if request.rca is None:
        return {
            "hasRca": False,
            "rootCause": "",
            "confidence": 0,
            "summary": "",
            "evidenceJson": "[]",
        }

    return {
        "hasRca": True,
        "rootCause": request.rca.suspectedRootCause or "",
        "confidence": request.rca.confidence or 0,
        "summary": request.rca.summary or "",
        "evidenceJson": request.rca.evidenceJson or "[]",
    }


def query_metrics_stub(request: DiagnoseRequest) -> dict[str, Any]:
    return {
        "available": False,
        "reason": "Phase4 does not query real metrics yet.",
        "suggestedMetrics": [
            "cpu_usage",
            "memory_usage",
            "disk_io",
            "network_error_rate",
            "service_latency",
        ],
    }


def query_logs_stub(request: DiagnoseRequest) -> dict[str, Any]:
    return {
        "available": False,
        "reason": "Phase4 does not query real logs yet.",
        "suggestedQueries": [
            "error logs around incident start time",
            "deployment logs around incident start time",
            "restart/crash logs for primary asset",
        ],
    }


def search_runbooks_stub(
    request: DiagnoseRequest, alert_analysis: dict[str, Any], rca_analysis: dict[str, Any]
) -> list[str]:
    root_cause = str(rca_analysis.get("rootCause") or "").lower()
    top_title = str(alert_analysis.get("topAlertTitle") or "").lower()

    suggestions: list[str] = []

    if "cpu" in root_cause or "cpu" in top_title:
        suggestions.append("Host resource saturation investigation")

    if "memory" in root_cause or "memory" in top_title:
        suggestions.append("Memory pressure troubleshooting")

    if "disk" in root_cause or "disk" in top_title:
        suggestions.append("Disk usage and IO troubleshooting")

    if not suggestions:
        suggestions.append("Generic incident triage checklist")

    suggestions.append("Recent change and deployment verification")

    return suggestions


def safety_guard() -> list[str]:
    return [
        "Do not execute remediation automatically in Phase4.",
        "Verify AI diagnosis against metrics, logs, and RCA evidence.",
        "High-risk actions require human approval in Phase5.",
    ]
