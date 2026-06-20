"""Safety Agent: reviews runbook candidates for safety risks and forbidden actions."""

from __future__ import annotations

from app.agent.contracts import AgentMessage, RunbookCandidate
from app.agent.collaboration.messages import new_agent_message


FORBIDDEN_ACTION_TYPES = {
    "shell",
    "ssh",
    "ansible",
    "webhook",
    "delete",
    "rollback",
    "restart",
}

FORBIDDEN_TEXT_PATTERNS = {
    "rm -rf",
    "kubectl delete",
    "drop database",
    "shutdown",
    "reboot",
}


class SafetyAgent:
    role = "safety_agent"

    def review(
        self,
        severity: str,
        confidence: float,
        candidates: list[RunbookCandidate],
    ) -> tuple[list[RunbookCandidate], str, list[str], AgentMessage]:
        safe_candidates: list[RunbookCandidate] = []
        notes: list[str] = []

        for candidate in candidates:
            if self._is_blocked(candidate):
                notes.append(
                    f"Blocked unsafe candidate: {candidate.title} ({candidate.action_type})"
                )
                continue
            safe_candidates.append(candidate)

        risk_level = self._infer_risk_level(severity, confidence)

        notes.append(
            "Agent output is advisory only. Execution must go through approval and runner."
        )

        message = new_agent_message(
            role=self.role,
            title="Safety review",
            content=(
                f"Safety review completed. "
                f"safe={len(safe_candidates)} blocked={len(candidates) - len(safe_candidates)} "
                f"risk={risk_level}"
            ),
            confidence=1.0,
            metadata={
                "safe_candidate_count": len(safe_candidates),
                "blocked_candidate_count": len(candidates) - len(safe_candidates),
                "risk_level": risk_level,
            },
        )

        return safe_candidates, risk_level, notes, message

    def _is_blocked(self, candidate: RunbookCandidate) -> bool:
        if candidate.action_type.lower() in FORBIDDEN_ACTION_TYPES:
            return True

        text = " ".join(
            [
                candidate.title,
                candidate.reason,
                str(candidate.parameters),
            ]
        ).lower()

        return any(pattern in text for pattern in FORBIDDEN_TEXT_PATTERNS)

    def _infer_risk_level(self, severity: str, confidence: float) -> str:
        if severity == "critical":
            return "critical"
        if severity == "high" and confidence >= 0.50:
            return "high"
        if severity in {"high", "medium"}:
            return "medium"
        return "low"
