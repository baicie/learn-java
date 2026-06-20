"""Fake clients for testing."""

from __future__ import annotations

from app.agent.contracts import AgentCheckpoint, EvidenceItem, SimilarCase


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
