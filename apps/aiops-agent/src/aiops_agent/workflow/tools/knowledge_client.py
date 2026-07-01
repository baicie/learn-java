"""Knowledge client: searches similar incident cases from the knowledge base."""

from __future__ import annotations

import json
import re
from typing import Any

import httpx
from pydantic import ValidationError

from aiops_agent.settings import settings
from aiops_agent.workflow.contracts import SimilarCase
from aiops_agent.workflow.errors import ToolError
from aiops_agent.workflow.tools.internal_auth import internal_tool_headers
from aiops_agent.workflow.tools.plugin_policy_guard import PluginToolPolicyGuard
from aiops_agent.workflow.tools.tool_keys import KNOWLEDGE_SEARCH_CASES


class KnowledgeClient:
    def __init__(
        self,
        base_url: str | None = None,
        timeout: float | None = None,
        policy_guard: PluginToolPolicyGuard | None = None,
    ):
        self.base_url = (base_url or settings.workflow_api_base_url).rstrip("/")
        self.timeout = timeout or settings.workflow_request_timeout_seconds
        self.policy_guard = policy_guard or PluginToolPolicyGuard()

    async def search_cases(
        self,
        tenant_id: str,
        query: str,
        tags: list[str],
        top_k: int,
    ) -> list[SimilarCase]:
        await self.policy_guard.require_allowed(tenant_id, KNOWLEDGE_SEARCH_CASES)
        url = f"{self.base_url}/internal/agent/tools/search-cases"
        body = {
            "query": query,
            "tags": tags,
            "topK": top_k,
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout, trust_env=False) as client:
                headers = internal_tool_headers(tenant_id)
                response = await client.post(url, json=body, headers=headers)
                if response.status_code == 404:
                    return []
                response.raise_for_status()
                payload = response.json()

            results = self._extract_results(payload)
            return [self._to_similar_case(item) for item in results]

        except ToolError:
            raise
        except (httpx.HTTPError, ValueError, TypeError, ValidationError) as exc:
            raise ToolError(
                "KNOWLEDGE_TOOL_FAILED",
                f"Failed to search cases: {exc}",
            ) from exc

    def _extract_results(self, payload: Any) -> list[dict[str, Any]]:
        if not isinstance(payload, dict):
            raise ToolError(
                "KNOWLEDGE_TOOL_RESPONSE_INVALID",
                "Knowledge response must be an object",
            )

        data = payload.get("data", payload)
        if not isinstance(data, dict):
            raise ToolError(
                "KNOWLEDGE_TOOL_RESPONSE_INVALID",
                "Knowledge response data must be an object",
            )

        results = data.get("results", [])
        if not isinstance(results, list):
            raise ToolError(
                "KNOWLEDGE_TOOL_RESPONSE_INVALID",
                "Knowledge response results must be an array",
            )

        normalized: list[dict[str, Any]] = []
        for item in results:
            if not isinstance(item, dict):
                raise ToolError(
                    "KNOWLEDGE_TOOL_RESPONSE_INVALID",
                    "Knowledge result item must be an object",
                )
            normalized.append(item)

        return normalized

    def _to_similar_case(self, item: dict[str, Any]) -> SimilarCase:
        content = str(item.get("content") or "")
        metadata = self._read_metadata(item.get("metadataJson"))

        root_cause = (
            item.get("rootCause")
            or metadata.get("rootCause")
            or self._extract_labeled_value(content, "Root Cause")
        )

        resolution = (
            item.get("resolution")
            or metadata.get("resolution")
            or self._extract_labeled_value(content, "Resolution")
        )

        tags = item.get("tags") or metadata.get("tags") or []
        if not isinstance(tags, list):
            tags = []

        return SimilarCase(
            case_id=str(item.get("sourceId") or item.get("caseId") or ""),
            title=str(item.get("title") or ""),
            summary=content,
            root_cause=root_cause,
            resolution=resolution,
            score=self._safe_float(item.get("score")),
            tags=[str(tag) for tag in tags],
        )

    def _read_metadata(self, value: Any) -> dict[str, Any]:
        if value is None:
            return {}

        if isinstance(value, dict):
            return value

        if isinstance(value, str) and value.strip():
            try:
                data = json.loads(value)
                return data if isinstance(data, dict) else {}
            except json.JSONDecodeError:
                return {}

        return {}

    def _extract_labeled_value(self, content: str, label: str) -> str | None:
        # Supports Phase6.2 chunk text such as:
        # Root Cause: redis timeout
        # Resolution: restart service
        pattern = re.compile(rf"(?im)^\s*{re.escape(label)}\s*:\s*(.+?)\s*$")
        match = pattern.search(content)
        if not match:
            return None

        value = match.group(1).strip()
        return value or None

    def _safe_float(self, value: Any) -> float:
        if value is None:
            return 0.0
        return float(value)
