"""Tests for checkpoint_client HTTP integration."""

from __future__ import annotations

import pytest
import respx
from httpx import Response

from aiops_agent.workflow.tools.checkpoint_client import CheckpointClient


@pytest.fixture(autouse=True)
def oauth_service_headers(monkeypatch):
    async def headers(_settings):
        return {"Authorization": "Bearer oauth-service-token"}

    monkeypatch.setattr(
        "aiops_agent.workflow.tools.internal_auth.service_credential_headers",
        headers,
    )


@pytest.mark.asyncio
@respx.mock
async def test_create_checkpoint_parses_api_response_data_id():
    route = respx.post("http://java/internal/agent/checkpoints").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "id": "agcp_1",
                    "status": "pending",
                    "resumeToken": "agrt_1",
                    "stateSnapshotJson": '{"incident_id":"inc_1"}',
                }
            },
        )
    )

    client = CheckpointClient(base_url="http://java")

    checkpoint = await client.create_checkpoint(
        tenant_id="tenant_1",
        incident_id="inc_1",
        title="Review RCA",
        reason="Need review",
        review_prompt="Approve RCA?",
        root_cause="redis timeout",
        confidence=0.8,
        risk_level="high",
        state_snapshot={"incident_id": "inc_1"},
    )

    assert route.called
    assert checkpoint.checkpoint_id == "agcp_1"
    assert checkpoint.status == "pending"
    assert checkpoint.resume_token == "agrt_1"
    assert checkpoint.state_snapshot["incident_id"] == "inc_1"

    request_json = route.calls[0].request.content.decode()
    assert "stateSnapshotJson" in request_json
    assert "graphVersion" in request_json
    assert "ttlSeconds" in request_json


@pytest.mark.asyncio
@respx.mock
async def test_get_checkpoint_uses_tenant_checkpoint_route():
    respx.get("http://java/internal/agent/checkpoints/tenant_1/agcp_1").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "id": "agcp_1",
                    "status": "approved",
                    "resumeToken": "agrt_1",
                    "stateSnapshotJson": '{"incident_id":"inc_1"}',
                }
            },
        )
    )

    client = CheckpointClient(base_url="http://java")

    checkpoint = await client.get_checkpoint(
        tenant_id="tenant_1",
        checkpoint_id="agcp_1",
    )

    assert checkpoint.checkpoint_id == "agcp_1"
    assert checkpoint.status == "approved"
    assert checkpoint.state_snapshot["incident_id"] == "inc_1"


@pytest.mark.asyncio
@respx.mock
async def test_get_checkpoint_uses_resume_token_route():
    respx.get("http://java/internal/agent/checkpoints/resume-token/tenant_1/agrt_1").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "id": "agcp_1",
                    "status": "approved",
                    "resumeToken": "agrt_1",
                    "stateSnapshotJson": "{}",
                }
            },
        )
    )

    client = CheckpointClient(base_url="http://java")

    checkpoint = await client.get_checkpoint(
        tenant_id="tenant_1",
        resume_token="agrt_1",
    )

    assert checkpoint.checkpoint_id == "agcp_1"
    assert checkpoint.status == "approved"


@pytest.mark.asyncio
@respx.mock
async def test_checkpoint_client_rejects_missing_id():
    respx.post("http://java/internal/agent/checkpoints").mock(
        return_value=Response(200, json={"data": {"status": "pending"}})
    )

    client = CheckpointClient(base_url="http://java")

    from aiops_agent.workflow.errors import ToolError

    with pytest.raises(ToolError) as exc_info:
        await client.create_checkpoint(
            tenant_id="tenant_1",
            incident_id="inc_1",
            title="Review RCA",
            reason="Need review",
            review_prompt="Approve RCA?",
            root_cause="redis timeout",
            confidence=0.8,
            risk_level="high",
            state_snapshot={},
        )

    assert "checkpoint id is missing" in str(exc_info.value.message)


@pytest.mark.asyncio
@respx.mock
async def test_create_checkpoint_reads_state_snapshot_json_from_string():
    respx.post("http://java/internal/agent/checkpoints").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "id": "agcp_2",
                    "status": "pending",
                    "stateSnapshotJson": '{"root_cause":"timeout"}',
                }
            },
        )
    )

    client = CheckpointClient(base_url="http://java")

    checkpoint = await client.create_checkpoint(
        tenant_id="tenant_1",
        incident_id="inc_1",
        title="RCA",
        reason="review",
        review_prompt="ok?",
        root_cause="timeout",
        confidence=0.7,
        risk_level="medium",
        state_snapshot={"fallback": "yes"},
    )

    assert checkpoint.checkpoint_id == "agcp_2"
    assert checkpoint.state_snapshot["root_cause"] == "timeout"


@pytest.mark.asyncio
@respx.mock
async def test_create_checkpoint_falls_back_when_state_snapshot_json_empty():
    respx.post("http://java/internal/agent/checkpoints").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "id": "agcp_3",
                    "status": "pending",
                }
            },
        )
    )

    client = CheckpointClient(base_url="http://java")

    checkpoint = await client.create_checkpoint(
        tenant_id="tenant_1",
        incident_id="inc_1",
        title="RCA",
        reason="review",
        review_prompt="ok?",
        root_cause="timeout",
        confidence=0.5,
        risk_level="low",
        state_snapshot={"key": "value"},
    )

    assert checkpoint.checkpoint_id == "agcp_3"
    assert checkpoint.state_snapshot["key"] == "value"
