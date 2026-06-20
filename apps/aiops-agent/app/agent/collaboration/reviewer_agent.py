"""Reviewer Agent: finalizes diagnosis with next steps and advisory summary."""

from __future__ import annotations

from app.agent.contracts import AgentMessage, RunbookCandidate
from app.agent.collaboration.messages import new_agent_message


class ReviewerAgent:
    role = "reviewer_agent"

    def finalize(
        self,
        root_cause: str,
        confidence: float,
        risk_level: str,
        runbook_candidates: list[RunbookCandidate],
        safety_notes: list[str],
    ) -> tuple[list[str], AgentMessage]:
        next_steps = [
            "Review multi-agent RCA and evidence messages.",
            "Confirm whether the proposed root cause is acceptable.",
        ]

        if runbook_candidates:
            next_steps.append(
                "Review advisory runbook candidates before creating automation plan."
            )

        if risk_level in {"high", "critical"}:
            next_steps.append("Require human approval before any remediation.")

        if confidence < 0.6:
            next_steps.append("Collect additional metrics, logs, and recent change events.")

        if safety_notes:
            next_steps.append("Review safety notes before proceeding.")

        message = new_agent_message(
            role=self.role,
            title="Final reviewer summary",
            content=(
                f"Reviewer accepted final advisory diagnosis. "
                f"root_cause={root_cause}; confidence={confidence:.2f}; risk={risk_level}."
            ),
            confidence=confidence,
            metadata={
                "risk_level": risk_level,
                "runbook_candidate_count": len(runbook_candidates),
                "safety_note_count": len(safety_notes),
            },
        )

        return next_steps, message
