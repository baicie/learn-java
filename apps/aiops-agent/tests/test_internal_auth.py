from __future__ import annotations

import pytest

from aiops_agent.settings import Settings
from aiops_agent.workflow.tools.internal_auth import (
    HEADER_INTERNAL_AGENT_TOKEN,
    HEADER_TENANT_ID,
    internal_tool_headers,
)


def test_internal_tool_headers_contains_tenant_and_token():
    test_settings = Settings(internal_agent_token="secret-token")

    headers = internal_tool_headers("tenant_1", test_settings)

    assert headers[HEADER_TENANT_ID] == "tenant_1"
    assert headers[HEADER_INTERNAL_AGENT_TOKEN] == "secret-token"


def test_internal_tool_headers_strips_tenant():
    test_settings = Settings(internal_agent_token="secret-token")

    headers = internal_tool_headers(" tenant_1 ", test_settings)

    assert headers[HEADER_TENANT_ID] == "tenant_1"


def test_internal_tool_headers_rejects_blank_tenant():
    test_settings = Settings(internal_agent_token="secret-token")

    with pytest.raises(ValueError):
        internal_tool_headers(" ", test_settings)
