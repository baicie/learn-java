"""Contracts: shared Pydantic models for the agent graph."""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field

Severity = Literal["info", "low", "medium", "high", "critical"]
RiskLevel = Literal["low", "medium", "high", "critical"]
AgentRole = Literal[
    "evidence_agent",
    "rca_agent",
    "runbook_agent",
    "safety_agent",
    "reviewer_agent",
]


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


class AgentMessage(BaseModel):
    message_id: str
    role: AgentRole
    title: str
    content: str
    confidence: float = 0.0
    metadata: dict[str, Any] = Field(default_factory=dict)


class AgentCheckpoint(BaseModel):
    checkpoint_id: str
    status: str
    resume_token: str | None = None
    state_snapshot: dict[str, Any] = Field(default_factory=dict)
    decision_comment: str | None = None


class AgentMemory(BaseModel):
    memory_id: str
    title: str
    content: str
    memory_type: str
    scope_type: str = "tenant"
    scope_id: str | None = None
    score: float = 0.0
    confidence: float = 0.0
    tags: list[str] = Field(default_factory=list)


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
    enable_human_checkpoint: bool = False
    enable_multi_agent_collaboration: bool = False
    enable_agent_memory: bool = False
    enable_agent_memory_write: bool = False


class DiagnosisResumeRequest(BaseModel):
    tenant_id: str
    checkpoint_id: str | None = None
    resume_token: str | None = None


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
    checkpoint_required: bool = False
    checkpoint_id: str | None = None
    checkpoint_status: str | None = None
    agent_messages: list[AgentMessage] = Field(default_factory=list)
    memories: list[AgentMemory] = Field(default_factory=list)
    memory_write_status: str | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)


class HealthResponse(BaseModel):
    status: str = "ok"


class DiagnosisContractResponse(BaseModel):
    contract_version: str = "agent-diagnosis.v1"
    input_model: str = "DiagnosisRequest"
    output_model: str = "DiagnosisResponse"
    graph_version: str = "phase7.3-agent-memory"
