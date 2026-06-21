"""Tests for metrics endpoint."""

from __future__ import annotations

from aiops_agent.observability.metrics import metrics_content_type, render_metrics


def test_render_metrics_returns_prometheus_payload():
    payload = render_metrics()

    assert isinstance(payload, bytes)
    assert b"# HELP" in payload
    assert "text/plain" in metrics_content_type()
