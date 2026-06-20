"""Agent message ledger: create and append structured agent messages."""

from __future__ import annotations

import uuid
from typing import Any

from aiops_agent.settings import settings
from aiops_agent.workflow.contracts import AgentMessage, AgentRole


def new_agent_message(
    role: AgentRole,
    title: str,
    content: str,
    confidence: float = 0.0,
    metadata: dict[str, Any] | None = None,
) -> AgentMessage:
    return AgentMessage(
        message_id="agm_" + uuid.uuid4().hex,
        role=role,
        title=title,
        content=content,
        confidence=max(0.0, min(confidence, 1.0)),
        metadata=metadata or {},
    )


def append_agent_message(
    messages: list[AgentMessage],
    message: AgentMessage,
) -> list[AgentMessage]:
    next_messages = [*messages, message]
    return next_messages[-settings.workflow_max_agent_messages :]
