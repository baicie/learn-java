"""Evidence client: fetches evidence from the internal evidence API."""

from __future__ import annotations

import httpx

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
        except httpx.HTTPError as exc:
            raise ToolError("EVIDENCE_TOOL_FAILED", f"Failed to fetch evidence: {exc}") from exc

        data = payload.get("data", payload)
        items = data.get("items", data if isinstance(data, list) else [])
        return [EvidenceItem.model_validate(item) for item in items]
