from __future__ import annotations

import logging
from collections.abc import AsyncIterator, Callable

from fastapi import Depends, FastAPI, Header, HTTPException, Request, Response, Security, status
from fastapi.openapi.utils import get_openapi
from fastapi.security import APIKeyHeader, HTTPAuthorizationCredentials, HTTPBearer

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
    ServiceAuthProbeResponse,
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
logger = logging.getLogger(__name__)

app = FastAPI(title="AegisOps LangGraph Agent Runtime", version="0.7.3")
if settings.observability_enabled:
    app.add_middleware(RequestContextMiddleware)


service_authenticator = ServiceAuthenticator(settings)
service_bearer = HTTPBearer(auto_error=False, bearerFormat="JWT", scheme_name="HTTPBearer")
diagnosis_grant_header = APIKeyHeader(
    name="X-AegisOps-Diagnosis-Grant",
    auto_error=False,
    scheme_name="DiagnosisGrant",
)

SERVICE_AUTH_RESPONSES = {
    401: {"description": "Missing or invalid service credentials"},
    403: {"description": "Service credentials do not grant the required scope"},
    503: {"description": "Service authentication provider is unavailable"},
}
DIAGNOSIS_AUTH_RESPONSES = {
    **SERVICE_AUTH_RESPONSES,
    401: {"description": "Missing or invalid service credentials, or missing diagnosis grant"},
}


def require_service_scope(scope: str) -> Callable:
    async def dependency(
        credentials: HTTPAuthorizationCredentials | None = Security(service_bearer),
    ) -> ServicePrincipal:
        authorization = None
        if credentials is not None:
            authorization = f"{credentials.scheme} {credentials.credentials}"
        return await service_authenticator.authenticate(
            authorization=authorization,
            required_scope=scope,
        )

    return dependency


def require_diagnosis_grant(scope: str) -> Callable:
    service_dependency = require_service_scope(scope)

    async def dependency(
        request: Request,
        principal: ServicePrincipal = Depends(service_dependency),
        x_aegisops_diagnosis_grant: str | None = Security(diagnosis_grant_header),
    ) -> AsyncIterator[None]:
        if x_aegisops_diagnosis_grant is None or not x_aegisops_diagnosis_grant.strip():
            logger.warning(
                "Diagnosis authorization grant is missing",
                extra={
                    "eventType": "diagnosis_grant_missing",
                    "severity": "critical",
                    "httpStatus": status.HTTP_401_UNAUTHORIZED,
                    "requestPath": request.url.path,
                    "serviceId": principal.service_id,
                },
            )
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="diagnosis authorization grant is required",
            )
        token = diagnosis_grant_var.set(x_aegisops_diagnosis_grant)
        try:
            yield
        finally:
            diagnosis_grant_var.reset(token)

    return dependency


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


@app.post(
    "/v1/auth/probe",
    response_model=ServiceAuthProbeResponse,
    responses=SERVICE_AUTH_RESPONSES,
)
async def service_auth_probe(
    principal: ServicePrincipal = Depends(require_service_scope("agent:diagnose")),
) -> ServiceAuthProbeResponse:
    return ServiceAuthProbeResponse(ok=True, serviceId=principal.service_id)


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
    responses=DIAGNOSIS_AUTH_RESPONSES,
    dependencies=[
        Depends(require_diagnosis_grant("agent:diagnose")),
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
    responses=DIAGNOSIS_AUTH_RESPONSES,
    dependencies=[
        Depends(require_diagnosis_grant("agent:resume")),
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
    responses=SERVICE_AUTH_RESPONSES,
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


def custom_openapi() -> dict:
    if app.openapi_schema is not None:
        return app.openapi_schema

    schema = get_openapi(
        title=app.title,
        version=app.version,
        routes=app.routes,
    )
    diagnosis_security = [{"HTTPBearer": [], "DiagnosisGrant": []}]
    for path in ("/v1/diagnose", "/v1/diagnose/resume"):
        schema["paths"][path]["post"]["security"] = diagnosis_security
    app.openapi_schema = schema
    return schema


app.openapi = custom_openapi
