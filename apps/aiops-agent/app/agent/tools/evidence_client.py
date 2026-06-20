"""Evidence client: fetches evidence from the internal evidence API."""

from __future__ import annotations

from typing import Any

import httpx
from pydantic import ValidationError

from app.agent.contracts import EvidenceItem
from app.agent.errors import ToolError
from app.agent.settings import settings


class EvidenceClient:
    def __init__(self, base_url: str | None = None, timeout: float | None = None):
        self.base_url = (base_url or settings.evidence_api_base_url).rstrip("/")
        self.timeout = timeout or settings.request_timeout_seconds

    async def fetch_evidence(self, tenant_id: str, incident_id: str) -> list[EvidenceItem]:
        url = f"{self.base_url}/internal/agent/incidents/{incident_id}/evidence"

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.get(url, headers={"X-Tenant-Id": tenant_id})
                if response.status_code == 404:
                    return []
                response.raise_for_status()
                payload = response.json()

            items = self._extract_items(payload)
            return [EvidenceItem.model_validate(item) for item in items]

        except ToolError:
            raise
        except (httpx.HTTPError, ValueError, TypeError, ValidationError) as exc:
            raise ToolError(
                "EVIDENCE_TOOL_FAILED",
                f"Failed to fetch evidence: {exc}",
            ) from exc

    def _extract_items(self, payload: Any) -> list[dict[str, Any]]:
        if isinstance(payload, list):
            return self._ensure_dict_items(payload)

        if not isinstance(payload, dict):
            raise ToolError(
                "EVIDENCE_TOOL_RESPONSE_INVALID",
                "Evidence response must be an object or an array",
            )

        data = payload.get("data", payload)

        if isinstance(data, list):
            return self._ensure_dict_items(data)

        if isinstance(data, dict):
            items = data.get("items", [])
            if not isinstance(items, list):
                raise ToolError(
                    "EVIDENCE_TOOL_RESPONSE_INVALID",
                    "Evidence response data.items must be an array",
                )
            return self._ensure_dict_items(items)

        raise ToolError(
            "EVIDENCE_TOOL_RESPONSE_INVALID",
            "Evidence response data must be an object or an array",
        )

    def _ensure_dict_items(self, items: list[Any]) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        for item in items:
            if not isinstance(item, dict):
                raise ToolError(
                    "EVIDENCE_TOOL_RESPONSE_INVALID",
                    "Evidence item must be an object",
                )
            result.append(item)
        return result
