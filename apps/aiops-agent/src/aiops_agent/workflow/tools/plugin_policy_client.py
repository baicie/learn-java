"""Plugin tool allowlist client for internal Java policy checks."""

from __future__ import annotations

from typing import Any

import httpx

from aiops_agent import mtls
from aiops_agent.settings import settings
from aiops_agent.workflow.tools.internal_auth import internal_tool_headers


class PluginToolPolicyError(Exception):
    pass


class PluginToolDeniedError(PluginToolPolicyError):
    def __init__(self, tool_key: str, reason: str):
        super().__init__(reason)
        self.tool_key = tool_key
        self.reason = reason


class PluginPolicyClient:
    def __init__(self, base_url: str | None = None, timeout: float | None = None):
        self.base_url = (base_url or settings.workflow_api_base_url).rstrip("/")
        self.timeout = timeout or settings.workflow_request_timeout_seconds

    async def authorize_tool(self, tenant_id: str, tool_key: str) -> bool:
        url = f"{self.base_url}/internal/agent/plugins/tools/authorize"
        body = {
            "tenantId": tenant_id,
            "toolKey": tool_key,
        }

        async with httpx.AsyncClient(
            timeout=self.timeout, **mtls.mtls_httpx_kwargs(settings)
        ) as client:
            response = await client.post(
                url,
                json=body,
                headers=await internal_tool_headers(tenant_id),
            )
            response.raise_for_status()
            payload = response.json()

        data = self._extract_data(payload)
        allowed = bool(data.get("allowed", False))
        reason = str(data.get("reason") or "")

        if not allowed:
            raise PluginToolDeniedError(tool_key, reason or "tool denied by plugin policy")

        return True

    def _extract_data(self, payload: Any) -> dict[str, Any]:
        if not isinstance(payload, dict):
            raise PluginToolPolicyError("plugin policy response must be object")
        data = payload.get("data", payload)
        if not isinstance(data, dict):
            raise PluginToolPolicyError("plugin policy response data must be object")
        return data
