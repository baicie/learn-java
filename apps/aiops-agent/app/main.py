"""FastAPI entry point for the Phase 7.2 multi-agent agent."""

from __future__ import annotations

from fastapi import FastAPI

from app.agent.contracts import (
    DiagnosisContractResponse,
    DiagnosisRequest,
    DiagnosisResumeRequest,
    DiagnosisResponse,
    HealthResponse,
)
from app.agent.services.diagnosis_service import DiagnosisService

app = FastAPI(title="AegisOps Agent", version="0.7.2")

diagnosis_service = DiagnosisService()


@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    return HealthResponse()


@app.get("/v1/contracts/diagnosis", response_model=DiagnosisContractResponse)
async def diagnosis_contract() -> DiagnosisContractResponse:
    return DiagnosisContractResponse()


@app.post("/v1/diagnose", response_model=DiagnosisResponse)
async def diagnose(request: DiagnosisRequest) -> DiagnosisResponse:
    return await diagnosis_service.diagnose(request)


@app.post("/v1/diagnose/resume", response_model=DiagnosisResponse)
async def diagnose_resume(request: DiagnosisResumeRequest) -> DiagnosisResponse:
    return await diagnosis_service.resume(request)
