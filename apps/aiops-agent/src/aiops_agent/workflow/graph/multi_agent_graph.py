"""Multi-agent graph nodes: RCA and recommendation nodes with role-based agent collaboration."""

from __future__ import annotations

from aiops_agent.workflow.collaboration.evidence_agent import EvidenceAgent
from aiops_agent.workflow.collaboration.messages import append_agent_message
from aiops_agent.workflow.collaboration.rca_agent import RCAAgent
from aiops_agent.workflow.collaboration.reviewer_agent import ReviewerAgent
from aiops_agent.workflow.collaboration.runbook_agent import RunbookAgent
from aiops_agent.workflow.collaboration.safety_agent import SafetyAgent
from aiops_agent.workflow.graph.state import DiagnosisGraphState


def multi_agent_rca_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
    messages = state.get("agent_messages", [])

    evidence_agent = EvidenceAgent()
    rca_agent = RCAAgent()

    evidence_message = evidence_agent.review(
        title=state.get("title", ""),
        evidence=state.get("evidence", []),
        similar_cases=state.get("similar_cases", []),
    )
    messages = append_agent_message(messages, evidence_message)

    root_cause, confidence, rca_message = rca_agent.propose(
        title=state.get("title", ""),
        evidence=state.get("evidence", []),
        similar_cases=state.get("similar_cases", []),
        memories=state.get("memories", []),
        evidence_message=evidence_message,
    )
    messages = append_agent_message(messages, rca_message)

    state["root_cause"] = root_cause
    state["confidence"] = confidence
    state["agent_messages"] = messages
    return state


def multi_agent_recommendation_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
    messages = state.get("agent_messages", [])

    runbook_agent = RunbookAgent()
    safety_agent = SafetyAgent()
    reviewer_agent = ReviewerAgent()

    candidates, runbook_message = runbook_agent.propose(
        root_cause=state.get("root_cause", "Root cause is not confirmed"),
        enable_runbook_recommendation=state.get("enable_runbook_recommendation", True),
    )
    messages = append_agent_message(messages, runbook_message)

    safe_candidates, risk_level, safety_notes, safety_message = safety_agent.review(
        severity=state.get("severity", "medium"),
        confidence=float(state.get("confidence", 0.0)),
        candidates=candidates,
    )
    messages = append_agent_message(messages, safety_message)

    next_steps, reviewer_message = reviewer_agent.finalize(
        root_cause=state.get("root_cause", "Root cause is not confirmed"),
        confidence=float(state.get("confidence", 0.0)),
        risk_level=risk_level,
        runbook_candidates=safe_candidates,
        safety_notes=safety_notes,
    )
    messages = append_agent_message(messages, reviewer_message)

    state["runbook_candidates"] = safe_candidates
    state["risk_level"] = risk_level
    state["safety_notes"] = safety_notes
    state["next_steps"] = next_steps
    state["agent_messages"] = messages
    return state
