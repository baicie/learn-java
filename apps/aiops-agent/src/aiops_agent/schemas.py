from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, Field


class IncidentContext(BaseModel):
    id: str
    title: str | None = None
    summary: str | None = None
    severity: str | None = None
    status: str | None = None
    source: str | None = None
    primaryAssetId: str | None = None
    aggregationKey: str | None = None
    alertCount: int = 0
    suspectedRootCause: str | None = None
    confidence: float | None = None
    startedAt: datetime | None = None
    detectedAt: datetime | None = None
    lastSeenAt: datetime | None = None


class AlertContext(BaseModel):
    id: str
    source: str | None = None
    sourceEventId: str | None = None
    severity: str | None = None
    title: str | None = None
    description: str | None = None
    assetId: str | None = None
    entityType: str | None = None
    entityName: str | None = None
    fingerprint: str | None = None
    labelsJson: str | None = None
    startsAt: datetime | None = None


class RcaContext(BaseModel):
    id: str
    suspectedRootCause: str | None = None
    confidence: float | None = None
    summary: str | None = None
    evidenceJson: str | None = None
    modelVersion: str | None = None
    createdAt: datetime | None = None


class DiagnoseRequest(BaseModel):
    tenantId: str
    incidentId: str
    incident: IncidentContext
    alerts: list[AlertContext] = Field(default_factory=list)
    rca: RcaContext | None = None
    locale: str = "zh-CN"
    traceId: str | None = None


class DiagnoseResponse(BaseModel):
    provider: str = "aiops-agent"
    model: str = "langgraph-deterministic"
    agentName: str = "aegisops_diagnosis_graph"
    summary: str
    rootCause: str
    impact: str
    nextSteps: list[str] = Field(default_factory=list)
    runbookSuggestions: list[str] = Field(default_factory=list)
    risks: list[str] = Field(default_factory=list)
    raw: dict[str, Any] = Field(default_factory=dict)


class HealthResponse(BaseModel):
    ok: bool
    provider: str
    model: str
    agentName: str
