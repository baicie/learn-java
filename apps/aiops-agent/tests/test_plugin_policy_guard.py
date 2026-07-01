from __future__ import annotations

import pytest

from aiops_agent.workflow.tools.plugin_policy_client import PluginToolDeniedError
from aiops_agent.workflow.tools.plugin_policy_guard import PluginToolPolicyGuard


class FakePolicyClient:
    def __init__(self, should_allow: bool = True):
        self.should_allow = should_allow
        self.called = False
        self.last_tenant_id = None
        self.last_tool_key = None

    async def authorize_tool(self, tenant_id: str, tool_key: str) -> bool:
        self.called = True
        self.last_tenant_id = tenant_id
        self.last_tool_key = tool_key
        if not self.should_allow:
            raise PluginToolDeniedError(tool_key, "denied")
        return True


@pytest.mark.asyncio
async def test_guard_skips_when_disabled():
    client = FakePolicyClient()
    guard = PluginToolPolicyGuard(client=client, enabled=False)

    await guard.require_allowed("tenant_1", "memory.search")

    assert client.called is False


@pytest.mark.asyncio
async def test_guard_calls_client_when_enabled():
    client = FakePolicyClient()
    guard = PluginToolPolicyGuard(client=client, enabled=True)

    await guard.require_allowed("tenant_1", "memory.search")

    assert client.called is True
    assert client.last_tenant_id == "tenant_1"
    assert client.last_tool_key == "memory.search"


@pytest.mark.asyncio
async def test_guard_passes_through_allow():
    client = FakePolicyClient(should_allow=True)
    guard = PluginToolPolicyGuard(client=client, enabled=True)

    await guard.require_allowed("tenant_1", "evidence.fetch")


@pytest.mark.asyncio
async def test_guard_passes_through_denial():
    client = FakePolicyClient(should_allow=False)
    guard = PluginToolPolicyGuard(client=client, enabled=True)

    with pytest.raises(PluginToolDeniedError):
        await guard.require_allowed("tenant_1", "memory.create")
