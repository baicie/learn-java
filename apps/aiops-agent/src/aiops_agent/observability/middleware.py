"""FastAPI/Starlette middleware for request context and metrics."""

from __future__ import annotations

import time
import uuid
from collections.abc import Awaitable, Callable

from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware

from aiops_agent.observability.context import (
    diagnosis_grant_var,
    request_id_var,
    tenant_id_var,
    trace_id_var,
)
from aiops_agent.observability.metrics import REQUEST_COUNT, REQUEST_DURATION

REQUEST_ID_HEADER = "X-Request-Id"
TRACE_ID_HEADER = "X-Trace-Id"
TENANT_ID_HEADER = "X-Tenant-Id"
DIAGNOSIS_GRANT_HEADER = "X-AegisOps-Diagnosis-Grant"


class RequestContextMiddleware(BaseHTTPMiddleware):
    async def dispatch(
        self,
        request: Request,
        call_next: Callable[[Request], Awaitable[Response]],
    ) -> Response:
        request_id = request.headers.get(REQUEST_ID_HEADER) or "req_" + uuid.uuid4().hex
        trace_id = request.headers.get(TRACE_ID_HEADER) or request_id
        tenant_id = request.headers.get(TENANT_ID_HEADER)
        diagnosis_grant = request.headers.get(DIAGNOSIS_GRANT_HEADER)

        token_request = request_id_var.set(request_id)
        token_trace = trace_id_var.set(trace_id)
        token_tenant = tenant_id_var.set(tenant_id)
        token_diagnosis_grant = diagnosis_grant_var.set(diagnosis_grant)

        started = time.perf_counter()
        status = "500"
        response: Response | None = None

        try:
            response = await call_next(request)
            status = str(response.status_code)
            return response
        finally:
            elapsed = time.perf_counter() - started
            path = normalize_path(request.url.path)

            REQUEST_COUNT.labels(
                method=request.method,
                path=path,
                status=status,
            ).inc()

            REQUEST_DURATION.labels(
                method=request.method,
                path=path,
                status=status,
            ).observe(elapsed)

            if response is not None:
                response.headers[REQUEST_ID_HEADER] = request_id
                response.headers[TRACE_ID_HEADER] = trace_id

            request_id_var.reset(token_request)
            trace_id_var.reset(token_trace)
            tenant_id_var.reset(token_tenant)
            diagnosis_grant_var.reset(token_diagnosis_grant)


def normalize_path(path: str) -> str:
    if path.startswith("/v1/diagnose"):
        return "/v1/diagnose"
    if path.startswith("/metrics"):
        return "/metrics"
    if path.startswith("/health"):
        return "/health"
    return path
