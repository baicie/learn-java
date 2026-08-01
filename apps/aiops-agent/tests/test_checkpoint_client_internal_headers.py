from __future__ import annotations

import pytest
import respx
from httpx import Response

from aiops_agent.workflow.tools.checkpoint_client import CheckpointClient
from aiops_agent.workflow.tools.internal_auth import (
    HEADER_DIAGNOSIS_GRANT,
    HEADER_TENANT_ID,
)


@pytest.mark.asyncio
@respx.mock
async def test_checkpoint_client_sends_mtls_grant_headers():
    route = respx.post("http://java/internal/agent/checkpoints").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "id": "agcp_1",
                    "status": "pending",
                    "resumeToken": "agrt_1",
                    "stateSnapshotJson": "{}",
                }
            },
        )
    )

    client = CheckpointClient(base_url="http://java")

    await client.create_checkpoint(
        tenant_id="tenant_1",
        incident_id="inc_1",
        title="Review RCA",
        reason="Need review",
        review_prompt="Approve?",
        root_cause="redis timeout",
        confidence=0.8,
        risk_level="high",
        state_snapshot={},
    )

    assert route.called
    assert route.calls[0].request.headers[HEADER_TENANT_ID] == "tenant_1"
    assert route.calls[0].request.headers[HEADER_DIAGNOSIS_GRANT] == "test-diagnosis-grant"
    assert "Authorization" not in route.calls[0].request.headers
