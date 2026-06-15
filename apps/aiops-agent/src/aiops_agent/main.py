from __future__ import annotations

from fastapi import Depends, FastAPI, Header, HTTPException, status

from aiops_agent.schemas import DiagnoseRequest, DiagnoseResponse, HealthResponse
from aiops_agent.service import DiagnosisService
from aiops_agent.settings import settings

app = FastAPI(title="AegisOps LangGraph Agent Runtime", version="0.1.0")


def verify_internal_token(x_aegisops_internal_token: str | None = Header(default=None)) -> None:
    if x_aegisops_internal_token != settings.internal_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="invalid internal token",
        )


def diagnosis_service() -> DiagnosisService:
    return DiagnosisService(settings)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        ok=True,
        provider=settings.provider,
        model=settings.model,
        agentName=settings.agent_name,
    )


@app.post(
    "/v1/diagnose",
    response_model=DiagnoseResponse,
    dependencies=[Depends(verify_internal_token)],
)
def diagnose(
    request: DiagnoseRequest,
    service: DiagnosisService = Depends(diagnosis_service),
) -> DiagnoseResponse:
    return service.diagnose(request)
