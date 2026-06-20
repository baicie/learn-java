"""Contracts: shared Pydantic models for the agent graph."""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


Severity = Literal["info", "low", "medium", "high", "critical"]
RiskLevel = Literal["low", "medium", "high", "critical"]


class EvidenceItem(BaseModel):
    evidence_id: str
    evidence_type: str
    title: str
    summary: str
    source: str = "unknown"
    metadata: dict[str, Any] = Field(default_factory=dict)


class SimilarCase(BaseModel):
    case_id: str
    title: str
    summary: str
    root_cause: str | None = None
    resolution: str | None = None
    score: float = 0.0
    tags: list[str] = Field(default_factory=list)


class RunbookCandidate(BaseModel):
    runbook_id: str | None = None
    title: str
    action_type: str
    target_type: str
    risk_level: RiskLevel = "medium"
    reason: str
    parameters: dict[str, Any] = Field(default_factory=dict)


class DiagnosisRequest(BaseModel):
    tenant_id: str
    incident_id: str
    title: str
    severity: Severity = "medium"
    description: str | None = None
    alert_summary: str | None = None
    tags: list[str] = Field(default_factory=list)
    enable_case_retrieval: bool = True
    enable_runbook_recommendation: bool = True


class DiagnosisResponse(BaseModel):
    contract_version: str = "agent-diagnosis.v1"
    tenant_id: str
    incident_id: str
    summary: str
    root_cause: str
    confidence: float
    severity: Severity
    risk_level: RiskLevel
    evidence: list[EvidenceItem] = Field(default_factory=list)
    similar_cases: list[SimilarCase] = Field(default_factory=list)
    runbook_candidates: list[RunbookCandidate] = Field(default_factory=list)
    safety_notes: list[str] = Field(default_factory=list)
    next_steps: list[str] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)


class HealthResponse(BaseModel):
    status: str = "ok"


class DiagnosisContractResponse(BaseModel):
    contract_version: str = "agent-diagnosis.v1"
    input_model: str = "DiagnosisRequest"
    output_model: str = "DiagnosisResponse"
    graph_version: str = "phase7.0-modular-graph"
