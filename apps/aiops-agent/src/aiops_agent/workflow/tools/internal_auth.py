"""Shared headers for internal Java tool calls."""

from __future__ import annotations

from aiops_agent.observability.context import get_diagnosis_grant
from aiops_agent.observability.headers import observability_headers

HEADER_TENANT_ID = "X-Tenant-Id"
HEADER_DIAGNOSIS_GRANT = "X-AegisOps-Diagnosis-Grant"


async def internal_tool_headers(
    tenant_id: str,
    *,
    diagnosis_grant: str | None = None,
) -> dict[str, str]:
    if not tenant_id or not tenant_id.strip():
        raise ValueError("tenant_id is required for internal tool call")

    resolved_grant = (
        diagnosis_grant if diagnosis_grant is not None else get_diagnosis_grant()
    )
    if resolved_grant is None or not resolved_grant.strip():
        raise ValueError("diagnosis grant is required for internal tool call")

    headers = {HEADER_TENANT_ID: tenant_id.strip()}
    headers[HEADER_DIAGNOSIS_GRANT] = resolved_grant
    headers.update(observability_headers())
    return headers
