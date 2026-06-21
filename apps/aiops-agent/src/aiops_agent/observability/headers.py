"""Helper to inject request/trace IDs into downstream HTTP headers."""

from __future__ import annotations

from aiops_agent.observability.context import get_request_id, get_trace_id

REQUEST_ID_HEADER = "X-Request-Id"
TRACE_ID_HEADER = "X-Trace-Id"


def observability_headers() -> dict[str, str]:
    headers: dict[str, str] = {}

    request_id = get_request_id()
    trace_id = get_trace_id()

    if request_id:
        headers[REQUEST_ID_HEADER] = request_id
    if trace_id:
        headers[TRACE_ID_HEADER] = trace_id

    return headers
