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


def test_json_log_formatter_whitelists_security_event_fields():
    record = logging.makeLogRecord(
        {
            "name": "aiops_agent.service_auth",
            "levelno": logging.WARNING,
            "levelname": "WARNING",
            "msg": "Internal service authentication rejected",
            "eventType": "internal_auth_forbidden",
            "severity": "critical",
            "httpStatus": 403,
            "requiredScope": "agent:work-record",
            "serviceId": "svc:aiops-worker",
            "requestPath": "/v1/diagnose",
            "token": "service-token-must-not-leak",
            "authorization": "Bearer service-token-must-not-leak",
            "claims": {"sub": "sensitive-claims-must-not-leak"},
            "diagnosisGrant": "diagnosis-grant-must-not-leak",
        }
    )

    text = JsonLogFormatter().format(record)
    payload = json.loads(text)

    assert payload["eventType"] == "internal_auth_forbidden"
    assert payload["severity"] == "critical"
    assert payload["httpStatus"] == 403
    assert payload["requiredScope"] == "agent:work-record"
    assert payload["serviceId"] == "svc:aiops-worker"
    assert payload["requestPath"] == "/v1/diagnose"
    for forbidden_field in ("token", "authorization", "claims", "diagnosisGrant"):
        assert forbidden_field not in payload
    for secret in (
        "service-token-must-not-leak",
        "sensitive-claims-must-not-leak",
        "diagnosis-grant-must-not-leak",
    ):
        assert secret not in text
