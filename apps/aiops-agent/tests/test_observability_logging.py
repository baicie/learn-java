"""Tests for JSON log formatter."""

from __future__ import annotations

import json
import logging

from aiops_agent.observability.context import request_id_var, tenant_id_var, trace_id_var
from aiops_agent.observability.logging import JsonLogFormatter


def test_json_log_formatter_includes_context_fields():
    token_request = request_id_var.set("req_1")
    token_trace = trace_id_var.set("trace_1")
    token_tenant = tenant_id_var.set("tenant_1")

    try:
        record = logging.LogRecord(
            name="test",
            level=logging.INFO,
            pathname=__file__,
            lineno=1,
            msg="hello",
            args=(),
            exc_info=None,
        )

        text = JsonLogFormatter().format(record)
        payload = json.loads(text)

        assert payload["message"] == "hello"
        assert payload["requestId"] == "req_1"
        assert payload["traceId"] == "trace_1"
        assert payload["tenantId"] == "tenant_1"
    finally:
        request_id_var.reset(token_request)
        trace_id_var.reset(token_trace)
        tenant_id_var.reset(token_tenant)
