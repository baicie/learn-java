"""Knowledge client: searches similar incident cases from the knowledge base."""

from __future__ import annotations

import httpx

from app.agent.contracts import SimilarCase
from app.agent.errors import ToolError
from app.agent.settings import settings


class KnowledgeClient:
    def __init__(self, base_url: str | None = None, timeout: float | None = None):
        self.base_url = (base_url or settings.knowledge_api_base_url).rstrip("/")
        self.timeout = timeout or settings.request_timeout_seconds

    async def search_cases(
        self,
        tenant_id: str,
        query: str,
        tags: list[str],
        top_k: int,
    ) -> list[SimilarCase]:
        url = f"{self.base_url}/internal/agent/tools/search-cases"
        body = {
            "query": query,
            "tags": tags,
            "topK": top_k,
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.post(url, json=body, headers={"X-Tenant-Id": tenant_id})
                if response.status_code == 404:
                    return []
                response.raise_for_status()
                payload = response.json()
        except httpx.HTTPError as exc:
            raise ToolError("KNOWLEDGE_TOOL_FAILED", f"Failed to search cases: {exc}") from exc

        data = payload.get("data", payload)
        results = data.get("results", [])
        return [
            SimilarCase(
                case_id=item.get("sourceId", item.get("caseId", "")),
                title=item.get("title", ""),
                summary=item.get("content", ""),
                root_cause=item.get("rootCause"),
                resolution=item.get("resolution"),
                score=float(item.get("score", 0.0)),
                tags=item.get("tags", []),
            )
            for item in results
        ]
