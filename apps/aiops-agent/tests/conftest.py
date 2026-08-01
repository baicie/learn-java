from __future__ import annotations

import pytest

from aiops_agent.observability.context import diagnosis_grant_var


@pytest.fixture(autouse=True)
def diagnosis_grant_context():
    token = diagnosis_grant_var.set("test-diagnosis-grant")
    try:
        yield
    finally:
        diagnosis_grant_var.reset(token)
