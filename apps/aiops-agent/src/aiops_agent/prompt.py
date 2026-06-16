from __future__ import annotations

import json
from typing import Any

from aiops_agent.schemas import DiagnoseRequest


def build_diagnosis_prompt(
    request: DiagnoseRequest,
    incident_summary: dict[str, Any],
    alert_analysis: dict[str, Any],
    rca_analysis: dict[str, Any],
    metrics: dict[str, Any],
    logs: dict[str, Any],
    changes: dict[str, Any],
    runbook_suggestions: list[str],
    risks: list[str],
) -> list[dict[str, str]]:
    system = (
        "You are AegisOps Diagnosis Agent. "
        "You diagnose AIOps incidents from structured evidence. "
        "You must not execute commands. "
        "You must not modify systems. "
        "You must not restart services, rollback deployments, delete resources, or close incidents. "
        "All remediation is suggestion-only in Phase4.3. "
        "Return strict JSON only. Do not wrap JSON in markdown."
    )

    user_payload = {
        "contractVersion": request.contractVersion,
        "locale": request.locale,
        "tenantId": request.tenantId,
        "incidentId": request.incidentId,
        "traceId": request.traceId,
        "evidence": {
            "incident": incident_summary,
            "alerts": alert_analysis,
            "rca": rca_analysis,
            "metrics": metrics,
            "logs": logs,
            "changes": changes,
            "runbookSuggestions": runbook_suggestions,
            "safetyRisks": risks,
        },
        "outputSchema": {
            "summary": "string, concise incident diagnosis summary",
            "rootCause": "string, most likely root cause based on evidence",
            "impact": "string, possible business or technical impact",
            "nextSteps": ["string, manual troubleshooting step"],
            "runbookSuggestions": ["string, runbook name or direction"],
            "risks": ["string, risk, caveat, or safety warning"],
        },
        "rules": [
            "Return JSON only.",
            "Do not include markdown fences.",
            "Do not invent metrics, logs, or changes.",
            "If metrics/logs/changes are unavailable, say so.",
            "Separate confirmed evidence from hypothesis.",
            "Do not suggest automatic execution.",
            "Do not suggest destructive commands.",
            "Prefer safe verification steps before remediation.",
        ],
    }

    return [
        {"role": "system", "content": system},
        {"role": "user", "content": json.dumps(user_payload, ensure_ascii=False, indent=2)},
    ]
