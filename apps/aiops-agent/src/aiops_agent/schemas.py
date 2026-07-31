from __future__ import annotations

from datetime import date, datetime
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
    matchedRules: list[str] = Field(default_factory=list)
    evidenceRefs: list[str] = Field(default_factory=list)
    modelVersion: str | None = None
    createdAt: datetime | None = None


class EvidenceContext(BaseModel):
    id: str
    evidenceKey: str
    source: str | None = None
    evidenceType: str
    title: str | None = None
    summary: str | None = None
    timeRangeStart: datetime | None = None
    timeRangeEnd: datetime | None = None
    confidence: float | None = None
    payloadJson: str | None = None


class TimelineContext(BaseModel):
    id: str
    eventTime: datetime | None = None
    eventType: str | None = None
    title: str | None = None
    description: str | None = None
    source: str | None = None
    payloadJson: str | None = None


class DiagnoseRequest(BaseModel):
    contractVersion: str = "agent-diagnosis.v1"
    tenantId: str
    incidentId: str
    incident: IncidentContext
    alerts: list[AlertContext] = Field(default_factory=list)
    rca: RcaContext | None = None
    evidence: list[EvidenceContext] = Field(default_factory=list)
    timeline: list[TimelineContext] = Field(default_factory=list)
    locale: str = "zh-CN"
    traceId: str


class DiagnoseResponse(BaseModel):
    contractVersion: str = "agent-diagnosis.v1"
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
    contractVersion: str
    generationMode: str


class ServiceAuthProbeResponse(BaseModel):
    ok: bool
    serviceId: str


class ContractResponse(BaseModel):
    contractVersion: str
    requestSchema: dict[str, Any]
    responseSchema: dict[str, Any]


class WorkRecordItem(BaseModel):
    id: str
    title: str
    status: str
    recordTime: datetime
    ownerName: str | None = None
    fields: dict[str, Any] = Field(default_factory=dict)
    relations: list[dict[str, Any]] = Field(default_factory=list)


class WorkRecordGenerateRequest(BaseModel):
    contractVersion: str = "work-record-generation.v1"
    generationType: str
    tenantId: str
    resourceId: str
    actorId: str | None = None
    periodStart: date | None = None
    periodEnd: date | None = None
    locale: str = "zh-CN"
    promptVersion: str = "work-record-summary-v1"
    records: list[WorkRecordItem] = Field(default_factory=list, max_length=5000)
    statistics: dict[str, Any] = Field(default_factory=dict)
    traceId: str


class WorkRecordGenerateResponse(BaseModel):
    contractVersion: str = "work-record-generation.v1"
    provider: str
    model: str
    promptVersion: str
    markdown: str = Field(min_length=1, max_length=100000)
    warnings: list[str] = Field(default_factory=list)
    providerRunId: str | None = None
    providerWorkflowId: str | None = None
    providerWorkflowVersion: str | None = None
    providerDurationMs: int | None = Field(default=None, ge=0)
    providerTotalTokens: int | None = Field(default=None, ge=0)
    fallbackReason: str | None = None
    raw: dict[str, Any] = Field(default_factory=dict)
