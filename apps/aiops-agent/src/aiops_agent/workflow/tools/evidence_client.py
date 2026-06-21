"""Evidence client for the Java internal evidence-query contract."""

from __future__ import annotations

import json
from typing import Any

import httpx

from aiops_agent.settings import settings
from aiops_agent.workflow.contracts import EvidenceItem
from aiops_agent.workflow.errors import ToolError
from aiops_agent.workflow.tools.internal_auth import internal_tool_headers
from aiops_agent.workflow.tools.plugin_policy_guard import PluginToolPolicyGuard
from aiops_agent.workflow.tools.tool_keys import EVIDENCE_FETCH


class EvidenceClient:
    def __init__(
        self,
        base_url: str | None = None,
        timeout: float | None = None,
        policy_guard: PluginToolPolicyGuard | None = None,
    ):
        self.base_url = (base_url or settings.workflow_api_base_url).rstrip("/")
        self.timeout = timeout or settings.workflow_request_timeout_seconds
        self.policy_guard = policy_guard or PluginToolPolicyGuard()

    async def fetch_evidence(self, tenant_id: str, incident_id: str) -> list[EvidenceItem]:
        await self.policy_guard.require_allowed(tenant_id, EVIDENCE_FETCH)
        url = f"{self.base_url}/internal/agent/evidence/query"
        body = {
            "contractVersion": settings.contract_version,
            "tenantId": tenant_id,
            "incidentId": incident_id,
            "traceId": f"agent-{incident_id}",
            "primaryAssetId": None,
            "startedAt": None,
            "lastSeenAt": None,
            "alertFingerprints": [],
            "alertTitles": [],
            "serviceNames": [],
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.post(
                    url,
                    headers={
                        "Content-Type": "application/json",
                        **internal_tool_headers(tenant_id, settings),
                    },
                    json=body,
                )
                response.raise_for_status()
                payload = response.json()

            return self._to_evidence_items(payload, incident_id)
        except ToolError:
            raise
        except (httpx.HTTPError, ValueError, TypeError) as exc:
            raise ToolError(
                "EVIDENCE_TOOL_FAILED",
                f"Failed to fetch evidence: {exc}",
            ) from exc

    def _to_evidence_items(
        self,
        payload: Any,
        incident_id: str,
    ) -> list[EvidenceItem]:
        if not isinstance(payload, dict):
            raise ToolError(
                "EVIDENCE_TOOL_RESPONSE_INVALID",
                "Evidence response must be an object",
            )

        items: list[EvidenceItem] = []
        section_specs = (
            ("metrics", "series", "metric", "victoriametrics"),
            ("logs", "patterns", "log", "clickhouse"),
            ("changes", "events", "change", "change-event"),
        )
        for section_name, list_name, evidence_type, source in section_specs:
            section = payload.get(section_name)
            if section is None:
                continue
            if not isinstance(section, dict):
                self._invalid(f"Evidence {section_name} section must be an object")

            values = section.get(list_name, [])
            if not isinstance(values, list):
                self._invalid(f"Evidence {section_name}.{list_name} must be an array")

            for index, value in enumerate(values):
                if not isinstance(value, dict):
                    self._invalid(f"Evidence {section_name} item must be an object")
                items.append(
                    self._to_item(
                        incident_id,
                        index,
                        evidence_type,
                        source,
                        value,
                    )
                )
        return items

    def _to_item(
        self,
        incident_id: str,
        index: int,
        evidence_type: str,
        source: str,
        value: dict[str, Any],
    ) -> EvidenceItem:
        title = str(
            value.get("title")
            or value.get("name")
            or value.get("severity")
            or value.get("changeType")
            or evidence_type
        )
        summary = str(
            value.get("summary")
            or value.get("sample")
            or value.get("reason")
            or json.dumps(value, ensure_ascii=False, sort_keys=True)
        )
        return EvidenceItem(
            evidence_id=f"{incident_id}:{evidence_type}:{index}",
            evidence_type=evidence_type,
            title=title,
            summary=summary,
            source=source,
            metadata=value,
        )

    def _invalid(self, message: str) -> None:
        raise ToolError("EVIDENCE_TOOL_RESPONSE_INVALID", message)
