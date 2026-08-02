"""Regression tests for the single-package agent runtime layout."""

from __future__ import annotations

import importlib.util
from pathlib import Path


def test_phase7_workflow_is_installed_under_canonical_package():
    assert importlib.util.find_spec("aiops_agent.workflow.graph.orchestrator") is not None


def test_legacy_top_level_app_package_is_removed():
    project_root = Path(__file__).resolve().parents[1]

    assert not (project_root / "app").exists()


def test_workflow_reuses_canonical_service_and_settings():
    workflow_root = Path(__file__).resolve().parents[1] / "src" / "aiops_agent" / "workflow"

    assert not (workflow_root / "settings.py").exists()
    assert not (workflow_root / "services").exists()


def test_legacy_internal_oauth_modules_are_removed():
    package_root = Path(__file__).resolve().parents[1] / "src" / "aiops_agent"

    assert not (package_root / "service_auth.py").exists()
    assert not (package_root / "service_credentials.py").exists()
