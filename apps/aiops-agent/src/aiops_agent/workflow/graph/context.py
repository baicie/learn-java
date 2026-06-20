from __future__ import annotations

from dataclasses import dataclass

from aiops_agent.workflow.tools.checkpoint_client import CheckpointClient
from aiops_agent.workflow.tools.evidence_client import EvidenceClient
from aiops_agent.workflow.tools.knowledge_client import KnowledgeClient
from aiops_agent.workflow.tools.memory_client import MemoryClient


@dataclass(slots=True)
class GraphContext:
    evidence_client: EvidenceClient
    knowledge_client: KnowledgeClient
    checkpoint_client: CheckpointClient | None = None
    memory_client: MemoryClient | None = None
