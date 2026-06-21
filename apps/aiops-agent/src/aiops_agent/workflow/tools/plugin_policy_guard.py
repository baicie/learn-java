"""Guard for optional plugin tool allowlist enforcement."""

from __future__ import annotations

from aiops_agent.settings import settings
from aiops_agent.workflow.tools.plugin_policy_client import PluginPolicyClient


class PluginToolPolicyGuard:
    def __init__(self, client: PluginPolicyClient | None = None, enabled: bool | None = None):
        self.client = client or PluginPolicyClient()
        self.enabled = (
            settings.workflow_plugin_tool_policy_enabled if enabled is None else enabled
        )

    async def require_allowed(self, tenant_id: str, tool_key: str) -> None:
        if not self.enabled:
            return
        await self.client.authorize_tool(tenant_id, tool_key)
