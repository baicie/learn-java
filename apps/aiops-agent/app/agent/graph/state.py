"""Graph state definition for the diagnosis orchestrator."""

from __future__ import annotations

from typing import Any, TypedDict

from app.agent.contracts import EvidenceItem, RunbookCandidate, SimilarCase


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

    evidence: list[EvidenceItem]
    similar_cases: list[SimilarCase]
    root_cause: str
    confidence: float
    runbook_candidates: list[RunbookCandidate]
    safety_notes: list[str]
    next_steps: list[str]
    risk_level: str
    final_summary: str
    metadata: dict[str, Any]
