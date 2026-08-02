"""Memory client: interacts with the Java agent memory API."""

from __future__ import annotations

from typing import Any

import httpx

from aiops_agent import mtls
from aiops_agent.settings import settings
from aiops_agent.workflow.contracts import AgentMemory
from aiops_agent.workflow.errors import ToolError
from aiops_agent.workflow.tools.internal_auth import internal_tool_headers
from aiops_agent.workflow.tools.plugin_policy_guard import PluginToolPolicyGuard
from aiops_agent.workflow.tools.tool_keys import MEMORY_CREATE, MEMORY_SEARCH


class MemoryClient:
    def __init__(
        self,
        base_url: str | None = None,
        timeout: float | None = None,
        policy_guard: PluginToolPolicyGuard | None = None,
    ):
        self.base_url = (base_url or settings.workflow_api_base_url).rstrip("/")
        self.timeout = timeout or settings.workflow_request_timeout_seconds
        self.policy_guard = policy_guard or PluginToolPolicyGuard()

    async def search_memories(
        self,
        tenant_id: str,
        query: str,
        scope_type: str = "tenant",
        scope_id: str | None = None,
        tags: list[str] | None = None,
        memory_types: list[str] | None = None,
        top_k: int = 5,
    ) -> list[AgentMemory]:
        await self.policy_guard.require_allowed(tenant_id, MEMORY_SEARCH)
        url = f"{self.base_url}/internal/agent/memories/search"
        body = {
            "tenantId": tenant_id,
            "query": query,
            "scopeType": scope_type,
            "scopeId": scope_id,
            "memoryTypes": memory_types or [],
            "tags": tags or [],
            "topK": top_k,
            "createdBy": "agent",
        }

        try:
            async with httpx.AsyncClient(
                timeout=self.timeout, **mtls.mtls_httpx_kwargs(settings)
            ) as client:
                headers = await internal_tool_headers(tenant_id)
                response = await client.post(url, json=body, headers=headers)
                response.raise_for_status()
                payload = response.json()
        except (httpx.HTTPError, ValueError, TypeError) as exc:
            raise ToolError("MEMORY_SEARCH_FAILED", f"Failed to search memories: {exc}") from exc

        data = self._extract_data(payload)
        results = data.get("results", [])
        if not isinstance(results, list):
            raise ToolError("MEMORY_RESPONSE_INVALID", "memory search results must be array")

        return [self._to_memory(item) for item in results if isinstance(item, dict)]

    async def create_memory(
        self,
        tenant_id: str,
        scope_type: str,
        scope_id: str | None,
        memory_type: str,
        source_type: str,
        source_id: str | None,
        title: str,
        content: str,
        tags: list[str],
        confidence: float,
    ) -> AgentMemory:
        await self.policy_guard.require_allowed(tenant_id, MEMORY_CREATE)
        url = f"{self.base_url}/internal/agent/memories"
        body = {
            "tenantId": tenant_id,
            "scopeType": scope_type,
            "scopeId": scope_id,
            "memoryType": memory_type,
            "sourceType": source_type,
            "sourceId": source_id,
            "title": title,
            "content": content,
            "tags": tags,
            "confidence": max(0.0, min(confidence, 1.0)),
            "createdBy": "agent",
        }

        try:
            async with httpx.AsyncClient(
                timeout=self.timeout, **mtls.mtls_httpx_kwargs(settings)
            ) as client:
                headers = await internal_tool_headers(tenant_id)
                response = await client.post(url, json=body, headers=headers)
                response.raise_for_status()
                payload = response.json()
        except (httpx.HTTPError, ValueError, TypeError) as exc:
            raise ToolError("MEMORY_CREATE_FAILED", f"Failed to create memory: {exc}") from exc

        data = self._extract_data(payload)
        return self._to_memory(data)

    def _extract_data(self, payload: Any) -> dict[str, Any]:
        if not isinstance(payload, dict):
            raise ToolError("MEMORY_RESPONSE_INVALID", "memory response must be object")

        data = payload.get("data", payload)
        if not isinstance(data, dict):
            raise ToolError("MEMORY_RESPONSE_INVALID", "memory response data must be object")
        return data

    def _to_memory(self, item: dict[str, Any]) -> AgentMemory:
        return AgentMemory(
            memory_id=str(item.get("id") or item.get("memoryId") or item.get("memory_id") or ""),
            title=str(item.get("title") or ""),
            content=str(item.get("content") or ""),
            memory_type=str(item.get("memoryType") or item.get("memory_type") or ""),
            scope_type=str(item.get("scopeType") or item.get("scope_type") or "tenant"),
            scope_id=item.get("scopeId") or item.get("scope_id"),
            score=float(item.get("score") or 0.0),
            confidence=float(item.get("confidence") or 0.0),
            tags=item.get("tags") if isinstance(item.get("tags"), list) else [],
        )
