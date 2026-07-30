from __future__ import annotations

from collections.abc import AsyncIterator, Callable

from fastapi import Depends, FastAPI, Header, HTTPException, Response, status

from aiops_agent.contract import (
    load_contract_schema,
    validate_request_contract,
    validate_response_contract,
)
from aiops_agent.observability.context import diagnosis_grant_var
from aiops_agent.observability.logging import configure_json_logging
from aiops_agent.observability.metrics import metrics_content_type, render_metrics
from aiops_agent.observability.middleware import RequestContextMiddleware
from aiops_agent.schemas import (
    ContractResponse,
    DiagnoseRequest,
    DiagnoseResponse,
    HealthResponse,
    WorkRecordGenerateRequest,
    WorkRecordGenerateResponse,
)
from aiops_agent.service import DiagnosisService
from aiops_agent.service_auth import ServiceAuthenticator, ServicePrincipal
from aiops_agent.settings import settings
from aiops_agent.work_record_generation import WorkRecordGenerationService
from aiops_agent.workflow.contracts import (
    DiagnosisResponse as WorkflowDiagnosisResponse,
)
from aiops_agent.workflow.contracts import DiagnosisResumeRequest

configure_json_logging(settings.log_level)

app = FastAPI(title="AegisOps LangGraph Agent Runtime", version="0.7.3")
if settings.observability_enabled:
    app.add_middleware(RequestContextMiddleware)


service_authenticator = ServiceAuthenticator(settings)


def require_service_scope(scope: str) -> Callable:
    async def dependency(
        authorization: str | None = Header(default=None),
        x_aegisops_internal_token: str | None = Header(default=None),
    ) -> ServicePrincipal:
        return await service_authenticator.authenticate(
            authorization=authorization,
            static_token=x_aegisops_internal_token,
            required_scope=scope,
        )

    return dependency


async def diagnosis_grant_context(
    x_aegisops_diagnosis_grant: str | None = Header(default=None),
) -> AsyncIterator[None]:
    if settings.diagnosis_grant_required and not x_aegisops_diagnosis_grant:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="diagnosis authorization grant is required",
        )
    token = diagnosis_grant_var.set(x_aegisops_diagnosis_grant)
    try:
        yield
    finally:
        diagnosis_grant_var.reset(token)


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


def work_record_generation_service() -> WorkRecordGenerationService:
    return WorkRecordGenerationService(settings)


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
    dependencies=[
        Depends(require_service_scope("agent:diagnose")),
        Depends(diagnosis_grant_context),
        Depends(verify_contract_version),
    ],
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
    dependencies=[
        Depends(require_service_scope("agent:resume")),
        Depends(diagnosis_grant_context),
        Depends(verify_contract_version),
    ],
)
async def diagnose_resume(
    request: DiagnosisResumeRequest,
    service: DiagnosisService = Depends(diagnosis_service),
) -> WorkflowDiagnosisResponse:
    return await service.resume(request)


@app.post(
    "/v1/work-record/generate",
    response_model=WorkRecordGenerateResponse,
    dependencies=[Depends(require_service_scope("agent:work-record"))],
)
async def generate_work_record(
    request: WorkRecordGenerateRequest,
    service: WorkRecordGenerationService = Depends(work_record_generation_service),
) -> WorkRecordGenerateResponse:
    if request.contractVersion != "work-record-generation.v1":
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="unsupported work-record generation contract",
        )
    return await service.generate(request)


@app.get("/metrics")
async def metrics() -> Response:
    return Response(content=render_metrics(), media_type=metrics_content_type())
