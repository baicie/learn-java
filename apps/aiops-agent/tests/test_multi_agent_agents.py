"""Tests for multi-agent role-based agents."""

from __future__ import annotations

from app.agent.collaboration.evidence_agent import EvidenceAgent
from app.agent.collaboration.rca_agent import RCAAgent
from app.agent.collaboration.reviewer_agent import ReviewerAgent
from app.agent.collaboration.runbook_agent import RunbookAgent
from app.agent.collaboration.safety_agent import SafetyAgent
from app.agent.contracts import EvidenceItem, RunbookCandidate, SimilarCase


def test_evidence_agent_reviews_evidence_and_cases():
    agent = EvidenceAgent()

    message = agent.review(
        title="Order timeout",
        evidence=[
            EvidenceItem(
                evidence_id="ev_1",
                evidence_type="log",
                title="Redis timeout",
                summary="redis timeout happened",
            )
        ],
        similar_cases=[
            SimilarCase(
                case_id="case_1",
                title="Redis timeout case",
                summary="case",
                root_cause="redis timeout",
                score=0.9,
            )
        ],
    )

    assert message.role == "evidence_agent"
    assert message.confidence > 0.3
    assert "Redis timeout" in message.content


def test_rca_agent_prefers_similar_case_root_cause():
    agent = RCAAgent()

    root_cause, confidence, message = agent.propose(
        title="Order timeout",
        evidence=[],
        similar_cases=[
            SimilarCase(
                case_id="case_1",
                title="case",
                summary="summary",
                root_cause="redis timeout",
                score=0.9,
            )
        ],
    )

    assert root_cause == "redis timeout"
    assert confidence > 0.3
    assert message.role == "rca_agent"


def test_runbook_agent_proposes_manual_candidate_for_timeout():
    agent = RunbookAgent()

    candidates, message = agent.propose(
        root_cause="Possible timeout or dependency latency issue",
        enable_runbook_recommendation=True,
    )

    assert len(candidates) == 1
    assert candidates[0].action_type == "manual"
    assert message.role == "runbook_agent"


def test_safety_agent_blocks_unsafe_action_type_and_text():
    agent = SafetyAgent()

    safe, risk_level, notes, message = agent.review(
        severity="high",
        confidence=0.8,
        candidates=[
            RunbookCandidate(
                title="Restart service",
                action_type="ssh",
                target_type="service",
                risk_level="high",
                reason="unsafe",
            ),
            RunbookCandidate(
                title="Clean cache",
                action_type="manual",
                target_type="service",
                risk_level="high",
                reason="run rm -rf /tmp/cache",
            ),
            RunbookCandidate(
                title="Collect logs",
                action_type="manual",
                target_type="service",
                risk_level="low",
                reason="safe",
            ),
        ],
    )

    assert len(safe) == 1
    assert safe[0].title == "Collect logs"
    assert risk_level == "high"
    assert len(notes) >= 2
    assert message.role == "safety_agent"


def test_reviewer_agent_generates_next_steps():
    agent = ReviewerAgent()

    next_steps, message = agent.finalize(
        root_cause="redis timeout",
        confidence=0.8,
        risk_level="high",
        runbook_candidates=[
            RunbookCandidate(
                title="Check latency",
                action_type="manual",
                target_type="service",
                reason="timeout",
            )
        ],
        safety_notes=["safe"],
    )

    assert any("approval" in step.lower() for step in next_steps)
    assert message.role == "reviewer_agent"
