"""Tests for observability Helm values and templates."""

from __future__ import annotations

from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]


def load_yaml(name: str) -> dict:
    with (ROOT / name).open("r", encoding="utf-8") as file:
        return yaml.safe_load(file)


def test_observability_values_exist():
    values = load_yaml("values.yaml")

    assert values["observability"]["enabled"] is True
    assert values["observability"]["metrics"]["enabled"] is True
    assert "serviceMonitor" in values["observability"]
    assert "prometheusRule" in values["observability"]
    assert "grafanaDashboard" in values["observability"]


def test_observability_templates_exist():
    assert (ROOT / "templates" / "servicemonitor.yaml").exists()
    assert (ROOT / "templates" / "prometheusrule.yaml").exists()
    assert (ROOT / "templates" / "grafana-dashboard.yaml").exists()


def test_configmap_contains_observability_env():
    configmap = (ROOT / "templates" / "configmap.yaml").read_text(encoding="utf-8")

    assert "AIOPS_OBSERVABILITY_ENABLED" in configmap
    assert "AIOPS_HTTP_METRICS_ENABLED" in configmap
    assert "AIOPS_AGENT_OBSERVABILITY_ENABLED" in configmap
    assert "AIOPS_AGENT_LOG_LEVEL" in configmap
