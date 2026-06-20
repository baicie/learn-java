"""Tests for memory graph nodes."""

from __future__ import annotations

import pytest

from app.agent.contracts import AgentMemory, RunbookCandidate
from app.agent.graph.memory_graph import retrieve_memory_node, write_memory_node
from tests.fakes import FakeMemoryClient


@pytest.mark.asyncio
async def test_retrieve_memory_disabled():
    client = FakeMemoryClient()
    state = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "title": "Order timeout",
        "tags": ["redis"],
        "enable_agent_memory": False,
    }

    result = await retrieve_memory_node(state, client)

    assert result["memories"] == []
    assert client.search_called is False


@pytest.mark.asyncio
async def test_retrieve_memory_enabled():
    client = FakeMemoryClient(
        [
            AgentMemory(
                memory_id="agm_1",
                title="Redis timeout pattern",
                content="Redis timeout caused order service 5xx.",
                memory_type="root_cause_pattern",
                score=0.9,
                confidence=0.8,
                tags=["redis"],
            )
        ]
    )

    state = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "title": "Order timeout",
        "tags": ["redis"],
        "enable_agent_memory": True,
    }

    result = await retrieve_memory_node(state, client)

    assert client.search_called is True
    assert len(result["memories"]) == 1


@pytest.mark.asyncio
async def test_write_memory_rejected_when_low_confidence():
    client = FakeMemoryClient()
    state = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "title": "Order timeout",
        "root_cause": "redis timeout",
        "confidence": 0.3,
        "enable_agent_memory_write": True,
    }

    result = await write_memory_node(state, client)

    assert result["memory_write_status"] == "rejected_by_policy"
    assert client.create_called is False


@pytest.mark.asyncio
async def test_write_memory_created_when_policy_allows():
    client = FakeMemoryClient()
    state = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "title": "Order timeout",
        "severity": "high",
        "root_cause": "redis timeout",
        "confidence": 0.8,
        "risk_level": "high",
        "tags": ["redis"],
        "runbook_candidates": [
            RunbookCandidate(
                title="Check redis latency",
                action_type="manual",
                target_type="service",
                reason="timeout",
            )
        ],
        "enable_agent_memory_write": True,
    }

    result = await write_memory_node(state, client)

    assert result["memory_write_status"] == "created"
    assert client.create_called is True
