"""RCA Agent: proposes root cause analysis based on evidence and similar cases."""

from __future__ import annotations

from app.agent.contracts import AgentMessage, EvidenceItem, SimilarCase
from app.agent.collaboration.messages import new_agent_message


class RCAAgent:
    role = "rca_agent"

    def propose(
        self,
        title: str,
        evidence: list[EvidenceItem],
        similar_cases: list[SimilarCase],
        evidence_message: AgentMessage | None = None,
    ) -> tuple[str, float, AgentMessage]:
        root_cause = self._infer_root_cause(title, evidence, similar_cases)
        confidence = self._infer_confidence(evidence, similar_cases, evidence_message)

        message = new_agent_message(
            role=self.role,
            title="RCA proposal",
            content=(
                f"Proposed root cause: {root_cause}\n"
                f"Confidence: {confidence:.2f}\n"
                f"Reason: inferred from evidence and similar cases."
            ),
            confidence=confidence,
            metadata={
                "root_cause": root_cause,
                "evidence_count": len(evidence),
                "similar_case_count": len(similar_cases),
            },
        )

        return root_cause, confidence, message

    def _infer_root_cause(
        self,
        title: str,
        evidence: list[EvidenceItem],
        similar_cases: list[SimilarCase],
    ) -> str:
        for case in similar_cases:
            if case.root_cause:
                return case.root_cause

        text = " ".join(
            [title, *[item.title for item in evidence], *[item.summary for item in evidence]]
        ).lower()

        if "timeout" in text or "latency" in text:
            return "Possible timeout or dependency latency issue"
        if "5xx" in text or "error rate" in text:
            return "Possible service error rate increase"
        if "cpu" in text or "memory" in text:
            return "Possible resource saturation"

        return "Root cause is not confirmed"

    def _infer_confidence(
        self,
        evidence: list[EvidenceItem],
        similar_cases: list[SimilarCase],
        evidence_message: AgentMessage | None,
    ) -> float:
        score = 0.35
        score += min(len(evidence), 5) * 0.08
        score += min(len(similar_cases), 3) * 0.08
        if evidence_message is not None:
            score = max(score, evidence_message.confidence)
        return round(min(score, 0.9), 2)
