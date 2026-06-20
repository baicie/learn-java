from __future__ import annotations

from dataclasses import dataclass

from app.agent.tools.checkpoint_client import CheckpointClient
from app.agent.tools.evidence_client import EvidenceClient
from app.agent.tools.knowledge_client import KnowledgeClient
from app.agent.tools.memory_client import MemoryClient


@dataclass(slots=True)
class GraphContext:
    evidence_client: EvidenceClient
    knowledge_client: KnowledgeClient
    checkpoint_client: CheckpointClient | None = None
    memory_client: MemoryClient | None = None
