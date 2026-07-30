"""Shared headers for internal Java tool calls."""

from __future__ import annotations

from aiops_agent.observability.context import get_diagnosis_grant
from aiops_agent.observability.headers import observability_headers
from aiops_agent.service_credentials import service_credential_headers
from aiops_agent.settings import Settings, settings

HEADER_TENANT_ID = "X-Tenant-Id"
HEADER_INTERNAL_AGENT_TOKEN = "X-AIOPS-INTERNAL-TOKEN"
HEADER_DIAGNOSIS_GRANT = "X-AegisOps-Diagnosis-Grant"


async def internal_tool_headers(
    tenant_id: str,
    current_settings: Settings | None = None,
    diagnosis_grant: str | None = None,
) -> dict[str, str]:
    cfg = current_settings or settings

    if not tenant_id or not tenant_id.strip():
        raise ValueError("tenant_id is required for internal tool call")

    resolved_grant = diagnosis_grant or get_diagnosis_grant()
    if cfg.diagnosis_grant_required and not resolved_grant:
        raise ValueError("diagnosis grant is required for internal tool call")
    if diagnosis_grant is not None and not diagnosis_grant.strip():
        raise ValueError("diagnosis grant is required for internal tool call")

    headers = {HEADER_TENANT_ID: tenant_id.strip()}
    headers.update(await service_credential_headers(cfg))
    if resolved_grant:
        headers[HEADER_DIAGNOSIS_GRANT] = resolved_grant
    headers.update(observability_headers())
    return headers
