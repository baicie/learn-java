from __future__ import annotations

from aiops_agent.evidence import EvidenceClient
from aiops_agent.graph import run_diagnosis_graph
from aiops_agent.llm import LlmClient
from aiops_agent.schemas import DiagnoseRequest, DiagnoseResponse
from aiops_agent.settings import Settings


class DiagnosisService:
    def __init__(
        self,
        settings: Settings,
        llm_client: LlmClient | None = None,
        evidence_client: EvidenceClient | None = None,
    ):
        self.settings = settings
        self.llm_client = llm_client
        self.evidence_client = evidence_client

    def diagnose(self, request: DiagnoseRequest) -> DiagnoseResponse:
        return run_diagnosis_graph(
            request,
            self.settings,
            self.llm_client,
            self.evidence_client,
        )
