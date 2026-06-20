"""Canonical diagnosis service for legacy LLM and Phase 7 workflow strategies."""

from __future__ import annotations

from aiops_agent.eval import evaluate_diagnosis
from aiops_agent.evidence import EvidenceClient as LegacyEvidenceClient
from aiops_agent.graph import run_diagnosis_graph as run_legacy_diagnosis_graph
from aiops_agent.llm import LlmClient
from aiops_agent.safety import apply_safety_boundary
from aiops_agent.schemas import DiagnoseRequest, DiagnoseResponse
from aiops_agent.settings import Settings
from aiops_agent.trace import AgentTracer
from aiops_agent.workflow.compat import to_contract_response, to_workflow_request
from aiops_agent.workflow.contracts import DiagnosisResumeRequest, EvidenceItem
from aiops_agent.workflow.graph.context import GraphContext
from aiops_agent.workflow.graph.orchestrator import (
    resume_diagnosis_graph,
    run_diagnosis_graph,
)
from aiops_agent.workflow.tools.checkpoint_client import CheckpointClient
from aiops_agent.workflow.tools.evidence_client import EvidenceClient
from aiops_agent.workflow.tools.knowledge_client import KnowledgeClient
from aiops_agent.workflow.tools.memory_client import MemoryClient


class DiagnosisService:
    def __init__(
        self,
        settings: Settings,
        llm_client: LlmClient | None = None,
        evidence_client: LegacyEvidenceClient | None = None,
    ):
        self.settings = settings
        self.llm_client = llm_client
        self.evidence_client = evidence_client

    async def diagnose(self, request: DiagnoseRequest) -> DiagnoseResponse:
        if self.settings.normalized_generation_mode() == "openai-compatible":
            return run_legacy_diagnosis_graph(
                request,
                self.settings,
                self.llm_client,
                self.evidence_client,
            )

        tracer = AgentTracer(
            self.settings,
            request,
            enabled=self.settings.trace_enabled,
        )
        step = tracer.start_step("phase7_workflow", "workflow")

        try:
            workflow_request = to_workflow_request(request, self.settings)
            result = await run_diagnosis_graph(
                workflow_request,
                self._workflow_context(request),
            )
            response = apply_safety_boundary(to_contract_response(request, result, self.settings))
            tracer.finish_step(step, "completed", "phase7 workflow completed")
        except Exception as exc:
            tracer.finish_step(
                step,
                "failed",
                error_message=f"{type(exc).__name__}: {exc}",
            )
            raise

        raw = dict(response.raw)
        if self.settings.eval_enabled:
            raw["agentEval"] = evaluate_diagnosis(response)
        if self.settings.trace_enabled:
            raw["agentRun"] = tracer.finish(
                "completed",
                response.provider,
                response.model,
                "",
                raw.get("safety", {}),
            )
        return response.model_copy(update={"raw": raw})

    async def resume(self, request: DiagnosisResumeRequest):
        return await resume_diagnosis_graph(request, self._workflow_context())

    def _workflow_context(self, request: DiagnoseRequest | None = None) -> GraphContext:
        base_url = self.settings.workflow_api_base_url
        evidence_client = (
            EvidenceClient(base_url=base_url)
            if self.settings.workflow_evidence_enabled
            else _RequestEvidenceClient(request)
        )
        knowledge_client = (
            KnowledgeClient(base_url=base_url)
            if self.settings.workflow_case_retrieval_enabled
            else _DisabledKnowledgeClient()
        )
        return GraphContext(
            evidence_client=evidence_client,
            knowledge_client=knowledge_client,
            checkpoint_client=CheckpointClient(base_url=base_url),
            memory_client=MemoryClient(base_url=base_url),
        )


class _RequestEvidenceClient:
    def __init__(self, request: DiagnoseRequest | None):
        self.request = request

    async def fetch_evidence(self, tenant_id: str, incident_id: str) -> list[EvidenceItem]:
        if self.request is None:
            return []

        evidence: list[EvidenceItem] = []
        if self.request.rca is not None:
            summary = (
                self.request.rca.suspectedRootCause
                or self.request.rca.summary
                or "RCA evidence is available"
            )
            evidence.append(
                EvidenceItem(
                    evidence_id=self.request.rca.id,
                    evidence_type="rca",
                    title="Existing RCA result",
                    summary=summary,
                    source="aiops-server",
                    metadata={"confidence": self.request.rca.confidence},
                )
            )

        evidence.extend(
            EvidenceItem(
                evidence_id=alert.id,
                evidence_type="alert",
                title=alert.title or "Alert",
                summary=alert.description or alert.title or "Alert evidence",
                source=alert.source or "alert",
                metadata={"severity": alert.severity, "assetId": alert.assetId},
            )
            for alert in self.request.alerts
        )
        return evidence


class _DisabledKnowledgeClient:
    async def search_cases(
        self,
        tenant_id: str,
        query: str,
        tags: list[str],
        top_k: int,
    ) -> list:
        return []
