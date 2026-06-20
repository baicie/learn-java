"""Graph state definition for the diagnosis orchestrator."""

from __future__ import annotations

from typing import Any, TypedDict

from app.agent.contracts import AgentMemory, AgentMessage, EvidenceItem, RunbookCandidate, SimilarCase


class DiagnosisGraphState(TypedDict, total=False):
    tenant_id: str
    incident_id: str
    title: str
    severity: str
    description: str | None
    alert_summary: str | None
    tags: list[str]
    enable_case_retrieval: bool
    enable_runbook_recommendation: bool
    enable_human_checkpoint: bool
    enable_multi_agent_collaboration: bool
    enable_agent_memory: bool
    enable_agent_memory_write: bool

    evidence: list[EvidenceItem]
    similar_cases: list[SimilarCase]
    memories: list[AgentMemory]
    root_cause: str
    confidence: float
    runbook_candidates: list[RunbookCandidate]
    safety_notes: list[str]
    next_steps: list[str]
    risk_level: str
    final_summary: str
    checkpoint_required: bool
    checkpoint: str | None
    checkpoint_status: str | None
    resume_token: str | None
    agent_messages: list[AgentMessage]
    memory_write_status: str | None
    metadata: dict[str, Any]
