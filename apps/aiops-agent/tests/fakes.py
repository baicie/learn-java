"""Fake clients for testing."""

from __future__ import annotations

from app.agent.contracts import EvidenceItem, SimilarCase


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
