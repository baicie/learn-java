"""Checkpoint client: creates and retrieves human approval checkpoints."""

from __future__ import annotations

from typing import Any

import httpx

from app.agent.contracts import AgentCheckpoint
from app.agent.errors import ToolError
from app.agent.settings import settings


class CheckpointClient:
    def __init__(self, base_url: str | None = None, timeout: float | None = None):
        self.base_url = (base_url or settings.checkpoint_api_base_url).rstrip("/")
        self.timeout = timeout or settings.request_timeout_seconds

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
            "title": title,
            "reason": reason,
            "reviewPrompt": review_prompt,
            "rootCause": root_cause,
            "confidence": confidence,
            "riskLevel": risk_level,
            "stateSnapshot": state_snapshot,
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.post(url, json=body)
                if response.status_code == 404:
                    return AgentCheckpoint(
                        checkpoint_id="agcp_fallback",
                        status="pending",
                        resume_token="agrt_fallback",
                        state_snapshot=state_snapshot,
                    )
                response.raise_for_status()
                payload = response.json()
        except httpx.HTTPError as exc:
            raise ToolError(
                "CHECKPOINT_TOOL_FAILED",
                f"Failed to create checkpoint: {exc}",
            ) from exc

        data = payload if isinstance(payload, dict) else {}
        return AgentCheckpoint(
            checkpoint_id=data.get("checkpointId", "agcp_unknown"),
            status=data.get("status", "pending"),
            resume_token=data.get("resumeToken"),
            state_snapshot=state_snapshot,
            decision_comment=data.get("decisionComment"),
        )

    async def get_checkpoint(
        self,
        tenant_id: str,
        checkpoint_id: str | None = None,
        resume_token: str | None = None,
    ) -> AgentCheckpoint:
        checkpoint_id = checkpoint_id or resume_token
        if not checkpoint_id:
            raise ToolError("CHECKPOINT_INVALID", "checkpoint_id or resume_token is required")

        url = f"{self.base_url}/internal/agent/checkpoints/{checkpoint_id}"

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.get(url, headers={"X-Tenant-Id": tenant_id})
                if response.status_code == 404:
                    return AgentCheckpoint(
                        checkpoint_id=checkpoint_id,
                        status="not_found",
                        state_snapshot={},
                    )
                response.raise_for_status()
                payload = response.json()
        except httpx.HTTPError as exc:
            raise ToolError(
                "CHECKPOINT_TOOL_FAILED",
                f"Failed to get checkpoint: {exc}",
            ) from exc

        data = payload if isinstance(payload, dict) else {}
        return AgentCheckpoint(
            checkpoint_id=data.get("checkpointId", checkpoint_id),
            status=data.get("status", "unknown"),
            resume_token=data.get("resumeToken"),
            state_snapshot=data.get("stateSnapshot", {}),
            decision_comment=data.get("decisionComment"),
        )
