"""Tests for internal auth observability header propagation."""

from __future__ import annotations

import pytest

from aiops_agent.observability.context import request_id_var, trace_id_var
from aiops_agent.settings import Settings
from aiops_agent.workflow.tools.internal_auth import internal_tool_headers


@pytest.mark.asyncio
async def test_internal_tool_headers_propagates_oauth_request_and_trace_id(monkeypatch):
    async def oauth_headers(_settings):
        return {"Authorization": "Bearer oauth-service-token"}

    monkeypatch.setattr(
        "aiops_agent.workflow.tools.internal_auth.service_credential_headers",
        oauth_headers,
    )
    token_request = request_id_var.set("req_1")
    token_trace = trace_id_var.set("trace_1")

    try:
        headers = await internal_tool_headers(
            "tenant_1",
            Settings(),
            diagnosis_grant="diagnosis-grant",
        )

        assert headers["X-Tenant-Id"] == "tenant_1"
        assert headers["Authorization"] == "Bearer oauth-service-token"
        assert headers["X-Request-Id"] == "req_1"
        assert headers["X-Trace-Id"] == "trace_1"
    finally:
        request_id_var.reset(token_request)
        trace_id_var.reset(token_trace)
