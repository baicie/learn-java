"""Checkpoint client: creates and retrieves human approval checkpoints."""

from __future__ import annotations

import json
from typing import Any

import httpx
from pydantic import ValidationError

from aiops_agent.settings import settings
from aiops_agent.workflow.contracts import AgentCheckpoint
from aiops_agent.workflow.errors import ToolError


class CheckpointClient:
    def __init__(self, base_url: str | None = None, timeout: float | None = None):
        self.base_url = (base_url or settings.workflow_api_base_url).rstrip("/")
        self.timeout = timeout or settings.workflow_request_timeout_seconds

    async def create_checkpoint(
        self,
        tenant_id: str,
        incident_id: str,
        title: str,
        reason: str,
        review_prompt: str,
        root_cause: str,
        confidence: float,
        risk_level: str,
        state_snapshot: dict[str, Any],
    ) -> AgentCheckpoint:
        url = f"{self.base_url}/internal/agent/checkpoints"
        body = {
            "tenantId": tenant_id,
            "incidentId": incident_id,
            "checkpointType": "rca_review",
            "graphVersion": settings.workflow_graph_version,
            "title": title,
            "reason": reason,
            "reviewPrompt": review_prompt,
            "rootCause": root_cause,
            "confidence": max(0.0, min(float(confidence), 1.0)),
            "riskLevel": risk_level,
            "stateSnapshotJson": self._json_dumps(state_snapshot),
            "ttlSeconds": settings.workflow_checkpoint_ttl_seconds,
            "createdBy": "agent",
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.post(
                    url,
                    json=body,
                    headers={"X-Tenant-Id": tenant_id},
                )
                response.raise_for_status()
                payload = response.json()

            return self._to_checkpoint(payload, fallback_state_snapshot=state_snapshot)

        except ToolError:
            raise
        except (httpx.HTTPError, ValueError, TypeError, ValidationError) as exc:
            raise ToolError(
                "CHECKPOINT_CREATE_FAILED",
                f"Failed to create checkpoint: {exc}",
            ) from exc

    async def get_checkpoint(
        self,
        tenant_id: str,
        checkpoint_id: str | None = None,
        resume_token: str | None = None,
    ) -> AgentCheckpoint:
        if checkpoint_id:
            url = f"{self.base_url}/internal/agent/checkpoints/{tenant_id}/{checkpoint_id}"
        elif resume_token:
            url = (
                f"{self.base_url}/internal/agent/checkpoints/"
                f"resume-token/{tenant_id}/{resume_token}"
            )
        else:
            raise ToolError(
                "CHECKPOINT_ID_REQUIRED",
                "checkpoint_id or resume_token is required",
            )

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.get(url, headers={"X-Tenant-Id": tenant_id})
                response.raise_for_status()
                payload = response.json()

            return self._to_checkpoint(payload, fallback_state_snapshot={})

        except ToolError:
            raise
        except (httpx.HTTPError, ValueError, TypeError, ValidationError) as exc:
            raise ToolError(
                "CHECKPOINT_GET_FAILED",
                f"Failed to get checkpoint: {exc}",
            ) from exc

    def _to_checkpoint(
        self,
        payload: Any,
        fallback_state_snapshot: dict[str, Any],
    ) -> AgentCheckpoint:
        data = self._extract_data(payload)

        checkpoint_id = data.get("id") or data.get("checkpointId") or data.get("checkpoint_id")
        if not checkpoint_id:
            raise ToolError(
                "CHECKPOINT_RESPONSE_INVALID",
                "checkpoint id is missing",
            )

        status = str(data.get("status") or "unknown")
        resume_token = data.get("resumeToken") or data.get("resume_token")

        state_snapshot = self._read_state_snapshot(
            data.get("stateSnapshotJson")
            or data.get("state_snapshot_json")
            or data.get("stateSnapshot")
            or data.get("state_snapshot"),
            fallback=fallback_state_snapshot,
        )

        return AgentCheckpoint(
            checkpoint_id=str(checkpoint_id),
            status=status,
            resume_token=str(resume_token) if resume_token else None,
            state_snapshot=state_snapshot,
            decision_comment=data.get("decisionComment") or data.get("decision_comment"),
        )

    def _extract_data(self, payload: Any) -> dict[str, Any]:
        if not isinstance(payload, dict):
            raise ToolError(
                "CHECKPOINT_RESPONSE_INVALID",
                "checkpoint response must be an object",
            )

        data = payload.get("data", payload)
        if not isinstance(data, dict):
            raise ToolError(
                "CHECKPOINT_RESPONSE_INVALID",
                "checkpoint response data must be an object",
            )

        return data

    def _read_state_snapshot(
        self,
        value: Any,
        fallback: dict[str, Any],
    ) -> dict[str, Any]:
        if value is None:
            return fallback

        if isinstance(value, dict):
            return value

        if isinstance(value, str):
            if not value.strip():
                return fallback
            try:
                parsed = json.loads(value)
            except json.JSONDecodeError:
                return fallback
            return parsed if isinstance(parsed, dict) else fallback

        return fallback

    def _json_dumps(self, value: dict[str, Any]) -> str:
        return json.dumps(value, ensure_ascii=False, default=str)
