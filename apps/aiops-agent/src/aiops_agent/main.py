from __future__ import annotations

from fastapi import Depends, FastAPI, Header, HTTPException, Response, status

from aiops_agent.contract import (
    load_contract_schema,
    validate_request_contract,
    validate_response_contract,
)
from aiops_agent.observability.logging import configure_json_logging
from aiops_agent.observability.metrics import metrics_content_type, render_metrics
from aiops_agent.observability.middleware import RequestContextMiddleware
from aiops_agent.schemas import (
    ContractResponse,
    DiagnoseRequest,
    DiagnoseResponse,
    HealthResponse,
)
from aiops_agent.service import DiagnosisService
from aiops_agent.settings import settings
from aiops_agent.workflow.contracts import (
    DiagnosisResponse as WorkflowDiagnosisResponse,
)
from aiops_agent.workflow.contracts import DiagnosisResumeRequest

configure_json_logging(settings.log_level)

app = FastAPI(title="AegisOps LangGraph Agent Runtime", version="0.7.3")
if settings.observability_enabled:
    app.add_middleware(RequestContextMiddleware)


def verify_internal_token(x_aegisops_internal_token: str | None = Header(default=None)) -> None:
    if x_aegisops_internal_token != settings.internal_agent_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="invalid internal token",
        )


def verify_contract_version(x_aegisops_contract_version: str | None = Header(default=None)) -> None:
    if x_aegisops_contract_version is None:
        return

    if x_aegisops_contract_version != settings.contract_version:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"unsupported contract version: {x_aegisops_contract_version}",
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
        contractVersion=settings.contract_version,
        generationMode=settings.normalized_generation_mode(),
    )


@app.get("/v1/contracts/diagnosis", response_model=ContractResponse)
def diagnosis_contract() -> ContractResponse:
    return ContractResponse(
        contractVersion=settings.contract_version,
        requestSchema=load_contract_schema("diagnosis-request.schema.json"),
        responseSchema=load_contract_schema("diagnosis-response.schema.json"),
    )


@app.post(
    "/v1/diagnose",
    response_model=DiagnoseResponse,
    dependencies=[Depends(verify_internal_token), Depends(verify_contract_version)],
)
async def diagnose(
    request: DiagnoseRequest,
    service: DiagnosisService = Depends(diagnosis_service),
) -> DiagnoseResponse:
    validate_request_contract(request, settings)
    response = await service.diagnose(request)
    validate_response_contract(response, settings)
    return response


@app.post(
    "/v1/diagnose/resume",
    response_model=WorkflowDiagnosisResponse,
    dependencies=[Depends(verify_internal_token), Depends(verify_contract_version)],
)
async def diagnose_resume(
    request: DiagnosisResumeRequest,
    service: DiagnosisService = Depends(diagnosis_service),
) -> WorkflowDiagnosisResponse:
    return await service.resume(request)


@app.get("/metrics")
async def metrics() -> Response:
    return Response(content=render_metrics(), media_type=metrics_content_type())
