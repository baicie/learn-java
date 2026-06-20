"""Runbook Agent: proposes advisory runbook candidates based on root cause."""

from __future__ import annotations

from aiops_agent.workflow.collaboration.messages import new_agent_message
from aiops_agent.workflow.contracts import AgentMessage, RunbookCandidate


class RunbookAgent:
    role = "runbook_agent"

    def propose(
        self,
        root_cause: str,
        enable_runbook_recommendation: bool,
    ) -> tuple[list[RunbookCandidate], AgentMessage]:
        if not enable_runbook_recommendation:
            return [], new_agent_message(
                role=self.role,
                title="Runbook recommendation skipped",
                content="Runbook recommendation is disabled by request.",
                confidence=1.0,
            )

        candidates: list[RunbookCandidate] = []
        root = root_cause.lower()

        if "timeout" in root or "latency" in root:
            candidates.append(
                RunbookCandidate(
                    title="Check dependency latency and connection pool",
                    action_type="manual",
                    target_type="service",
                    risk_level="medium",
                    reason="Root cause indicates timeout or latency issue",
                    parameters={
                        "checks": [
                            "dependency_latency",
                            "connection_pool",
                            "error_rate",
                        ]
                    },
                )
            )

        if "error rate" in root or "service" in root:
            candidates.append(
                RunbookCandidate(
                    title="Verify service health and recent deployment",
                    action_type="manual",
                    target_type="service",
                    risk_level="medium",
                    reason="Root cause indicates service degradation",
                    parameters={"checks": ["service_health", "deployment", "logs"]},
                )
            )

        if not candidates:
            candidates.append(
                RunbookCandidate(
                    title="Collect more evidence before remediation",
                    action_type="manual",
                    target_type="incident",
                    risk_level="low",
                    reason="Root cause is not confirmed",
                    parameters={"checks": ["metrics", "logs", "changes"]},
                )
            )

        message = new_agent_message(
            role=self.role,
            title="Runbook proposal",
            content=f"Proposed {len(candidates)} advisory runbook candidate(s).",
            confidence=0.7 if candidates else 0.3,
            metadata={"candidate_count": len(candidates)},
        )

        return candidates, message
