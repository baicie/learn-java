from __future__ import annotations

import logging
from collections.abc import AsyncIterator, Callable

from fastapi import Depends, FastAPI, Header, HTTPException, Request, Response, Security, status
from fastapi.openapi.utils import get_openapi
from fastapi.security import APIKeyHeader

from aiops_agent.contract import (
    load_contract_schema,
    validate_request_contract,
    validate_response_contract,
)
from aiops_agent.diagnosis_grant import DiagnosisGrantError, DiagnosisGrantVerifier
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


diagnosis_grant_verifier = DiagnosisGrantVerifier(settings)
diagnosis_grant_header = APIKeyHeader(
    name="X-AegisOps-Diagnosis-Grant",
    auto_error=False,
    scheme_name="DiagnosisGrant",
)

DIAGNOSIS_AUTH_RESPONSES = {
    401: {"description": "Missing or invalid diagnosis grant"},
    403: {"description": "Diagnosis grant does not authorize this request"},
}


def require_diagnosis_grant(scope: str) -> Callable:
    async def dependency(
        request: Request,
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
                },
            )
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="diagnosis authorization grant is required",
            )
        payload = await request.json()
        is_resume = request.url.path.endswith("/resume")
        context = (
            {
                "tenant_id": payload.get("tenant_id"),
                "incident_id": payload.get("incident_id"),
                "diagnosis_id": payload.get("diagnosis_id"),
                "trace_id": payload.get("trace_id"),
            }
            if is_resume
            else {
                "tenant_id": payload.get("tenantId"),
                "incident_id": payload.get("incidentId"),
                "diagnosis_id": payload.get("diagnosisId"),
                "trace_id": payload.get("traceId"),
            }
        )
        try:
            diagnosis_grant_verifier.verify(
                x_aegisops_diagnosis_grant.strip(),
                required_scope=scope,
                **context,
            )
        except DiagnosisGrantError as exc:
            forbidden = "scope" in str(exc) or "context" in str(exc)
            http_status = status.HTTP_403_FORBIDDEN if forbidden else status.HTTP_401_UNAUTHORIZED
            logger.warning(
                "Diagnosis authorization grant rejected",
                extra={
                    "eventType": "diagnosis_grant_invalid",
                    "severity": "critical",
                    "httpStatus": http_status,
                    "requestPath": request.url.path,
                    "reason": str(exc),
                },
            )
            raise HTTPException(
                status_code=http_status,
                detail="diagnosis authorization grant is invalid",
            ) from exc
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
        Depends(require_diagnosis_grant("diagnosis:execute")),
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
        Depends(require_diagnosis_grant("diagnosis:resume")),
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
    schema.setdefault("components", {}).setdefault("securitySchemes", {})["WorkloadMtls"] = {
        "type": "mutualTLS"
    }
    diagnosis_security = [{"WorkloadMtls": [], "DiagnosisGrant": []}]
    for path in ("/v1/diagnose", "/v1/diagnose/resume"):
        schema["paths"][path]["post"]["security"] = diagnosis_security
    schema["paths"]["/v1/work-record/generate"]["post"]["security"] = [
        {"WorkloadMtls": []}
    ]
    app.openapi_schema = schema
    return schema


app.openapi = custom_openapi
