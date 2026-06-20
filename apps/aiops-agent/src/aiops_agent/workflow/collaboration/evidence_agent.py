"""Evidence Agent: reviews collected evidence and similar cases."""

from __future__ import annotations

from aiops_agent.workflow.collaboration.messages import new_agent_message
from aiops_agent.workflow.contracts import AgentMessage, EvidenceItem, SimilarCase


class EvidenceAgent:
    role = "evidence_agent"

    def review(
        self,
        title: str,
        evidence: list[EvidenceItem],
        similar_cases: list[SimilarCase],
    ) -> AgentMessage:
        evidence_summary = self._summarize_evidence(evidence)
        case_summary = self._summarize_cases(similar_cases)

        confidence = 0.3
        confidence += min(len(evidence), 5) * 0.08
        confidence += min(len(similar_cases), 3) * 0.06

        return new_agent_message(
            role=self.role,
            title="Evidence review",
            content=(
                f"Incident: {title}\n"
                f"Evidence summary: {evidence_summary}\n"
                f"Similar case summary: {case_summary}"
            ),
            confidence=min(confidence, 0.85),
            metadata={
                "evidence_count": len(evidence),
                "similar_case_count": len(similar_cases),
            },
        )

    def _summarize_evidence(self, evidence: list[EvidenceItem]) -> str:
        if not evidence:
            return "No evidence collected."

        return "; ".join(
            f"{item.evidence_type}:{item.title}:{item.summary}" for item in evidence[:5]
        )

    def _summarize_cases(self, cases: list[SimilarCase]) -> str:
        if not cases:
            return "No similar case found."

        return "; ".join(
            f"{case.title}: root_cause={case.root_cause or 'unknown'} score={case.score}"
            for case in cases[:3]
        )
