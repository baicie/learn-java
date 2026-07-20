"""Prometheus metrics definitions and renderer."""

from __future__ import annotations

from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest

REQUEST_COUNT = Counter(
    "aiops_agent_http_requests_total",
    "AI Ops Agent HTTP request count",
    ["method", "path", "status"],
)

REQUEST_DURATION = Histogram(
    "aiops_agent_http_request_duration_seconds",
    "AI Ops Agent HTTP request duration seconds",
    ["method", "path", "status"],
    buckets=(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10),
)

DIAGNOSIS_COUNT = Counter(
    "aiops_agent_diagnosis_total",
    "AI Ops Agent diagnosis count",
    ["status"],
)

TOOL_CALL_COUNT = Counter(
    "aiops_agent_tool_calls_total",
    "AI Ops Agent tool call count",
    ["tool", "status"],
)

DIFY_REQUEST_COUNT = Counter(
    "aiops_agent_dify_requests_total",
    "Dify workflow request count",
    ["capability", "status"],
)

DIFY_REQUEST_DURATION = Histogram(
    "aiops_agent_dify_request_duration_seconds",
    "Dify workflow request duration seconds",
    ["capability", "status"],
    buckets=(0.1, 0.25, 0.5, 1, 2.5, 5, 10, 30, 60, 90),
)

DIFY_FALLBACK_COUNT = Counter(
    "aiops_agent_dify_fallback_total",
    "Dify workflow deterministic fallback count",
    ["capability", "reason"],
)

DIFY_TOKEN_COUNT = Counter(
    "aiops_agent_dify_tokens_total",
    "Dify workflow token count",
    ["capability"],
)


def render_metrics() -> bytes:
    return generate_latest()


def metrics_content_type() -> str:
    return CONTENT_TYPE_LATEST
