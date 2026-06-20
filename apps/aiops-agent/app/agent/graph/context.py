"""Graph context: injectable dependencies for graph nodes."""

from __future__ import annotations

from dataclasses import dataclass

from app.agent.tools.evidence_client import EvidenceClient
from app.agent.tools.knowledge_client import KnowledgeClient


@dataclass(slots=True)
class GraphContext:
    evidence_client: EvidenceClient
    knowledge_client: KnowledgeClient
