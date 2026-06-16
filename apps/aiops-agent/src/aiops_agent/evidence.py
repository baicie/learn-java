from __future__ import annotations

from typing import Any, Protocol

import httpx
from pydantic import BaseModel, Field

from aiops_agent.schemas import DiagnoseRequest
from aiops_agent.settings import Settings


class EvidenceBundle(BaseModel):
    metrics: dict[str, Any] = Field(default_factory=dict)
    logs: dict[str, Any] = Field(default_factory=dict)
    changes: dict[str, Any] = Field(default_factory=dict)


class EvidenceClient(Protocol):
    def query(self, request: DiagnoseRequest) -> EvidenceBundle:
        ...


def _unavailable_metrics(reason: str) -> dict[str, Any]:
    return {"available": False, "reason": reason, "series": []}


def _unavailable_logs(reason: str) -> dict[str, Any]:
    return {"available": False, "reason": reason, "patterns": []}


def _unavailable_changes(reason: str) -> dict[str, Any]:
    return {"available": False, "reason": reason, "events": []}


class DisabledEvidenceClient:
    def query(self, request: DiagnoseRequest) -> EvidenceBundle:
        return EvidenceBundle(
            metrics=_unavailable_metrics("Evidence client is disabled."),
            logs=_unavailable_logs("Evidence client is disabled."),
            changes=_unavailable_changes("Evidence client is disabled."),
        )


class HttpEvidenceClient:
    def __init__(self, settings: Settings):
        self.settings = settings

    def query(self, request: DiagnoseRequest) -> EvidenceBundle:
        if not self.settings.evidence_enabled:
            return DisabledEvidenceClient().query(request)

        base_url = self.settings.normalized_evidence_base_url()
        if not base_url:
            return DisabledEvidenceClient().query(request)

        payload = build_evidence_query_payload(request)

        try:
            response = httpx.post(
                f"{base_url}/query",
                headers={
                    "Content-Type": "application/json",
                    "X-AegisOps-Internal-Token": self.settings.evidence_internal_token,
                },
                json=payload,
                timeout=self.settings.evidence_timeout_seconds,
            )
            response.raise_for_status()
            data = response.json()

            return EvidenceBundle(
                metrics=data.get("metrics") or {},
                logs=data.get("logs") or {},
                changes=data.get("changes") or {},
            )
        except Exception as exc:
            reason = f"Evidence query failed: {type(exc).__name__}: {exc}"
            return EvidenceBundle(
                metrics=_unavailable_metrics(reason),
                logs=_unavailable_logs(reason),
                changes=_unavailable_changes(reason),
            )


def build_evidence_query_payload(request: DiagnoseRequest) -> dict[str, Any]:
    return {
        "contractVersion": request.contractVersion,
        "tenantId": request.tenantId,
        "incidentId": request.incidentId,
        "traceId": request.traceId,
        "primaryAssetId": request.incident.primaryAssetId,
        "startedAt": request.incident.startedAt.isoformat() if request.incident.startedAt else None,
        "lastSeenAt": request.incident.lastSeenAt.isoformat() if request.incident.lastSeenAt else None,
        "alertFingerprints": [alert.fingerprint for alert in request.alerts if alert.fingerprint],
        "alertTitles": [alert.title for alert in request.alerts if alert.title],
    }


def create_evidence_client(settings: Settings) -> EvidenceClient:
    if not settings.evidence_enabled:
        return DisabledEvidenceClient()
    return HttpEvidenceClient(settings)
