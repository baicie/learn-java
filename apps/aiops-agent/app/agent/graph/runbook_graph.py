"""runbook_graph: recommends runbook candidates based on root cause."""

from __future__ import annotations

from app.agent.contracts import RunbookCandidate
from app.agent.graph.state import DiagnosisGraphState


def recommend_runbook_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
    if not state.get("enable_runbook_recommendation", True):
        state["runbook_candidates"] = []
        return state

    root_cause = state.get("root_cause", "").lower()
    candidates: list[RunbookCandidate] = []

    if "timeout" in root_cause or "latency" in root_cause:
        candidates.append(
            RunbookCandidate(
                title="Check dependency latency and connection pool",
                action_type="manual",
                target_type="service",
                risk_level="medium",
                reason="Root cause indicates timeout or latency issue",
                parameters={"checks": ["connection_pool", "dependency_latency", "error_rate"]},
            )
        )

    if "error rate" in root_cause or "service" in root_cause:
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

    state["runbook_candidates"] = candidates
    return state
