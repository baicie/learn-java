"""Fake clients for testing."""

from __future__ import annotations

from app.agent.contracts import AgentCheckpoint, AgentMemory, EvidenceItem, SimilarCase


class FakeEvidenceClient:
    def __init__(self, evidence: list[EvidenceItem] | None = None):
        self.evidence = evidence or []
        self.called = False

    async def fetch_evidence(self, tenant_id: str, incident_id: str) -> list[EvidenceItem]:
        self.called = True
        return self.evidence


class FailingEvidenceClient:
    async def fetch_evidence(self, tenant_id: str, incident_id: str) -> list[EvidenceItem]:
        raise RuntimeError("boom")


class FakeKnowledgeClient:
    def __init__(self, cases: list[SimilarCase] | None = None):
        self.cases = cases or []
        self.called = False
        self.last_query: str | None = None

    async def search_cases(
        self,
        tenant_id: str,
        query: str,
        tags: list[str],
        top_k: int,
    ) -> list[SimilarCase]:
        self.called = True
        self.last_query = query
        return self.cases


class FailingKnowledgeClient:
    async def search_cases(
        self,
        tenant_id: str,
        query: str,
        tags: list[str],
        top_k: int,
    ) -> list[SimilarCase]:
        raise RuntimeError("boom")


class FakeCheckpointClient:
    def __init__(self):
        self.created = False
        self.checkpoint = AgentCheckpoint(
            checkpoint_id="agcp_1",
            status="pending",
            resume_token="agrt_1",
            state_snapshot={},
        )

    async def create_checkpoint(
        self,
        tenant_id: str,
        incident_id: str,
        title: str,
        reason: str,
        review_prompt: str,
        root_cause: str,
        confidence: float,
        risk_level: str,
        state_snapshot: dict,
    ) -> AgentCheckpoint:
        self.created = True
        self.checkpoint = AgentCheckpoint(
            checkpoint_id="agcp_1",
            status="pending",
            resume_token="agrt_1",
            state_snapshot=state_snapshot,
        )
        return self.checkpoint

    async def get_checkpoint(
        self,
        tenant_id: str,
        checkpoint_id: str | None = None,
        resume_token: str | None = None,
    ) -> AgentCheckpoint:
        return self.checkpoint


class ApprovedCheckpointClient(FakeCheckpointClient):
    async def get_checkpoint(
        self,
        tenant_id: str,
        checkpoint_id: str | None = None,
        resume_token: str | None = None,
    ) -> AgentCheckpoint:
        checkpoint = self.checkpoint
        return AgentCheckpoint(
            checkpoint_id=checkpoint.checkpoint_id,
            status="approved",
            resume_token=checkpoint.resume_token,
            state_snapshot=checkpoint.state_snapshot,
        )


class RejectedCheckpointClient(FakeCheckpointClient):
    async def get_checkpoint(
        self,
        tenant_id: str,
        checkpoint_id: str | None = None,
        resume_token: str | None = None,
    ) -> AgentCheckpoint:
        checkpoint = self.checkpoint
        return AgentCheckpoint(
            checkpoint_id=checkpoint.checkpoint_id,
            status="rejected",
            resume_token=checkpoint.resume_token,
            state_snapshot=checkpoint.state_snapshot,
            decision_comment="root cause is wrong",
        )


class FailingCheckpointClient:
    async def create_checkpoint(self, **kwargs) -> AgentCheckpoint:
        raise RuntimeError("checkpoint client unavailable")

    async def get_checkpoint(
        self,
        tenant_id: str,
        checkpoint_id: str | None = None,
        resume_token: str | None = None,
    ) -> AgentCheckpoint:
        raise RuntimeError("checkpoint client unavailable")


class FakeMemoryClient:
    def __init__(self, memories: list[AgentMemory] | None = None):
        self.memories = memories or []
        self.search_called = False
        self.create_called = False

    async def search_memories(
        self,
        tenant_id: str,
        query: str,
        scope_type: str = "tenant",
        scope_id: str | None = None,
        tags: list[str] | None = None,
        memory_types: list[str] | None = None,
        top_k: int = 5,
    ) -> list[AgentMemory]:
        self.search_called = True
        return self.memories[:top_k]

    async def create_memory(
        self,
        tenant_id: str,
        scope_type: str,
        scope_id: str | None,
        memory_type: str,
        source_type: str,
        source_id: str | None,
        title: str,
        content: str,
        tags: list[str],
        confidence: float,
    ) -> AgentMemory:
        self.create_called = True
        memory = AgentMemory(
            memory_id="agm_1",
            title=title,
            content=content,
            memory_type=memory_type,
            scope_type=scope_type,
            scope_id=scope_id,
            score=1.0,
            confidence=confidence,
            tags=tags,
        )
        self.memories.append(memory)
        return memory
