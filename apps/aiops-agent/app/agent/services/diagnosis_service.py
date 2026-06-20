"""Diagnosis service: orchestrates the graph with injected context."""

from __future__ import annotations

from app.agent.contracts import (
    DiagnosisRequest,
    DiagnosisResponse,
    DiagnosisResumeRequest,
)
from app.agent.graph.context import GraphContext
from app.agent.graph.orchestrator import resume_diagnosis_graph, run_diagnosis_graph
from app.agent.tools.checkpoint_client import CheckpointClient
from app.agent.tools.evidence_client import EvidenceClient
from app.agent.tools.knowledge_client import KnowledgeClient


class DiagnosisService:
    def __init__(self, context: GraphContext | None = None):
        self.context = context or GraphContext(
            evidence_client=EvidenceClient(),
            knowledge_client=KnowledgeClient(),
            checkpoint_client=CheckpointClient(),
        )

    async def diagnose(self, request: DiagnosisRequest) -> DiagnosisResponse:
        return await run_diagnosis_graph(request, self.context)

    async def resume(self, request: DiagnosisResumeRequest) -> DiagnosisResponse:
        return await resume_diagnosis_graph(request, self.context)
