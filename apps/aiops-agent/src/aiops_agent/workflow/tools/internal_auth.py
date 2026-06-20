"""Shared headers for internal Java tool calls."""

from __future__ import annotations

from aiops_agent.settings import Settings, settings

HEADER_TENANT_ID = "X-Tenant-Id"
HEADER_INTERNAL_AGENT_TOKEN = "X-AIOPS-INTERNAL-TOKEN"


def internal_tool_headers(
    tenant_id: str,
    current_settings: Settings | None = None,
) -> dict[str, str]:
    cfg = current_settings or settings

    if not tenant_id or not tenant_id.strip():
        raise ValueError("tenant_id is required for internal tool call")

    return {
        HEADER_TENANT_ID: tenant_id.strip(),
        HEADER_INTERNAL_AGENT_TOKEN: cfg.internal_agent_token,
    }
