from __future__ import annotations

from aiops_agent.graph import run_diagnosis_graph
from aiops_agent.schemas import DiagnoseRequest, DiagnoseResponse
from aiops_agent.settings import Settings


class DiagnosisService:
    def __init__(self, settings: Settings):
        self.settings = settings

    def diagnose(self, request: DiagnoseRequest) -> DiagnoseResponse:
        return run_diagnosis_graph(request, self.settings)
