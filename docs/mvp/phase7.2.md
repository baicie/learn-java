# Phase7.2：Multi-Agent Collaboration

> Phase7.2 目标：把 Phase7.0/7.1 的单 Graph 推理拆成 **多角色协作式诊断**。
> 这一阶段仍然运行在同一个 Python Agent Runtime 内，不做多进程、不做多服务、不调 Runner、不执行自动化。
> 多 Agent 的含义是：多个角色 Agent 在同一个 graph state 上协作，分别负责证据、RCA、Runbook、Safety、Review。

---

# 1. Phase7.2 定位

前置阶段：

```txt id="7qpu3f"
Phase7.0 Agent Graph Modularization
Phase7.1 Human-in-the-loop Checkpoint
```

Phase7.2 增加：

```txt id="m6sc2i"
Evidence Agent
RCA Agent
Runbook Agent
Safety Agent
Reviewer Agent
Agent Message Ledger
Multi-Agent Orchestration Node
```

最终流程：

```txt id="w9sdu5"
input
  -> evidence_graph
  -> case_retrieval_graph
  -> multi_agent_rca_node
       ├── EvidenceAgent.review()
       └── RCAAgent.propose()
  -> human_checkpoint_graph
       ├── pending/rejected -> final_report_graph
       └── approved/skipped -> multi_agent_recommendation_node
             ├── RunbookAgent.propose()
             ├── SafetyAgent.review()
             └── ReviewerAgent.finalize()
  -> final_report_graph
```

---

# 2. 不做什么

```txt id="928bkq"
1. 不做多进程 Agent
2. 不做多服务 Agent
3. 不做真实 LLM 自动辩论
4. 不调用 Runner
5. 不创建 execution
6. 不创建 automation_plan
7. 不做自动修复
8. 不做自动回滚
9. 不做长期 Memory
```

Phase7.2 只做：

```txt id="gg5hys"
同一个 Python Runtime 内的多角色协作
```

---

# 3. 新增目录结构

```txt id="5nt6pz"
apps/aiops-agent/
  app/
    agent/
      collaboration/
        __init__.py
        messages.py
        evidence_agent.py
        rca_agent.py
        runbook_agent.py
        safety_agent.py
        reviewer_agent.py
      graph/
        multi_agent_graph.py
```

---

# 4. Contract 修改

## 4.1 修改 `contracts.py`

路径：

```txt id="d7cpe9"
apps/aiops-agent/src/aiops_agent/workflow/contracts.py
```

在原文件中追加以下类型，并扩展 `DiagnosisRequest / DiagnosisResponse`。

```python id="dolm1s"
from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


Severity = Literal["info", "low", "medium", "high", "critical"]
RiskLevel = Literal["low", "medium", "high", "critical"]
AgentRole = Literal[
    "evidence_agent",
    "rca_agent",
    "runbook_agent",
    "safety_agent",
    "reviewer_agent",
]


class EvidenceItem(BaseModel):
    evidence_id: str
    evidence_type: str
    title: str
    summary: str
    source: str = "unknown"
    metadata: dict[str, Any] = Field(default_factory=dict)


class SimilarCase(BaseModel):
    case_id: str
    title: str
    summary: str
    root_cause: str | None = None
    resolution: str | None = None
    score: float = 0.0
    tags: list[str] = Field(default_factory=list)


class RunbookCandidate(BaseModel):
    runbook_id: str | None = None
    title: str
    action_type: str
    target_type: str
    risk_level: RiskLevel = "medium"
    reason: str
    parameters: dict[str, Any] = Field(default_factory=dict)


class AgentMessage(BaseModel):
    message_id: str
    role: AgentRole
    title: str
    content: str
    confidence: float = 0.0
    metadata: dict[str, Any] = Field(default_factory=dict)


class AgentCheckpoint(BaseModel):
    checkpoint_id: str
    status: str
    resume_token: str | None = None
    state_snapshot: dict[str, Any] = Field(default_factory=dict)
    decision_comment: str | None = None


class DiagnosisRequest(BaseModel):
    tenant_id: str
    incident_id: str
    title: str
    severity: Severity = "medium"
    description: str | None = None
    alert_summary: str | None = None
    tags: list[str] = Field(default_factory=list)
    enable_case_retrieval: bool = True
    enable_runbook_recommendation: bool = True
    enable_human_checkpoint: bool = False
    enable_multi_agent_collaboration: bool = False


class DiagnosisResumeRequest(BaseModel):
    tenant_id: str
    checkpoint_id: str | None = None
    resume_token: str | None = None


class DiagnosisResponse(BaseModel):
    contract_version: str = "agent-diagnosis.v1"
    tenant_id: str
    incident_id: str
    summary: str
    root_cause: str
    confidence: float
    severity: Severity
    risk_level: RiskLevel
    evidence: list[EvidenceItem] = Field(default_factory=list)
    similar_cases: list[SimilarCase] = Field(default_factory=list)
    runbook_candidates: list[RunbookCandidate] = Field(default_factory=list)
    safety_notes: list[str] = Field(default_factory=list)
    next_steps: list[str] = Field(default_factory=list)
    checkpoint_required: bool = False
    checkpoint_id: str | None = None
    checkpoint_status: str | None = None
    agent_messages: list[AgentMessage] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)


class HealthResponse(BaseModel):
    status: str = "ok"


class DiagnosisContractResponse(BaseModel):
    contract_version: str = "agent-diagnosis.v1"
    input_model: str = "DiagnosisRequest"
    output_model: str = "DiagnosisResponse"
    graph_version: str = "phase7.2-multi-agent-collaboration"
```

---

# 5. Settings 修改

## 5.1 `settings.py`

路径：

```txt id="6v3usf"
apps/aiops-agent/src/aiops_agent/settings.py
```

```python id="c3wh54"
from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class AgentSettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="AIOPS_AGENT_", extra="ignore")

    evidence_api_base_url: str = "http://localhost:8080"
    knowledge_api_base_url: str = "http://localhost:8080"
    checkpoint_api_base_url: str = "http://localhost:8080"
    request_timeout_seconds: float = 5.0
    graph_version: str = "phase7.2-multi-agent-collaboration"
    max_evidence_items: int = 8
    max_similar_cases: int = 5
    checkpoint_ttl_seconds: int = 86400
    max_agent_messages: int = 20


settings = AgentSettings()
```

---

# 6. Graph State 修改

## 6.1 `graph/state.py`

路径：

```txt id="ccp1sh"
apps/aiops-agent/src/aiops_agent/workflow/graph/state.py
```

```python id="t7m9xw"
from __future__ import annotations

from typing import Any, TypedDict

from aiops_agent.workflow.contracts import AgentMessage, EvidenceItem, RunbookCandidate, SimilarCase


class DiagnosisGraphState(TypedDict, total=False):
    tenant_id: str
    incident_id: str
    title: str
    severity: str
    description: str | None
    alert_summary: str | None
    tags: list[str]
    enable_case_retrieval: bool
    enable_runbook_recommendation: bool
    enable_human_checkpoint: bool
    enable_multi_agent_collaboration: bool

    evidence: list[EvidenceItem]
    similar_cases: list[SimilarCase]
    root_cause: str
    confidence: float
    runbook_candidates: list[RunbookCandidate]
    safety_notes: list[str]
    next_steps: list[str]
    risk_level: str
    final_summary: str
    checkpoint_required: bool
    checkpoint_id: str | None
    checkpoint_status: str | None
    resume_token: str | None
    agent_messages: list[AgentMessage]
    metadata: dict[str, Any]
```

---

# 7. Collaboration 基础代码

## 7.1 `collaboration/messages.py`

路径：

```txt id="5khgra"
apps/aiops-agent/src/aiops_agent/workflow/collaboration/messages.py
```

```python id="gka7ja"
from __future__ import annotations

import uuid
from typing import Any

from aiops_agent.workflow.contracts import AgentMessage, AgentRole
from aiops_agent.settings import settings


def new_agent_message(
    role: AgentRole,
    title: str,
    content: str,
    confidence: float = 0.0,
    metadata: dict[str, Any] | None = None,
) -> AgentMessage:
    return AgentMessage(
        message_id="agm_" + uuid.uuid4().hex,
        role=role,
        title=title,
        content=content,
        confidence=max(0.0, min(confidence, 1.0)),
        metadata=metadata or {},
    )


def append_agent_message(
    messages: list[AgentMessage],
    message: AgentMessage,
) -> list[AgentMessage]:
    next_messages = [*messages, message]
    return next_messages[-settings.max_agent_messages :]
```

---

## 7.2 `collaboration/evidence_agent.py`

路径：

```txt id="mgmw5d"
apps/aiops-agent/src/aiops_agent/workflow/collaboration/evidence_agent.py
```

```python id="kbl6ny"
from __future__ import annotations

from aiops_agent.workflow.contracts import AgentMessage, EvidenceItem, SimilarCase
from aiops_agent.workflow.collaboration.messages import new_agent_message


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
```

---

## 7.3 `collaboration/rca_agent.py`

路径：

```txt id="6digjc"
apps/aiops-agent/src/aiops_agent/workflow/collaboration/rca_agent.py
```

```python id="o1hfxu"
from __future__ import annotations

from aiops_agent.workflow.contracts import AgentMessage, EvidenceItem, SimilarCase
from aiops_agent.workflow.collaboration.messages import new_agent_message


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
```

---

## 7.4 `collaboration/runbook_agent.py`

路径：

```txt id="nq74cg"
apps/aiops-agent/src/aiops_agent/workflow/collaboration/runbook_agent.py
```

```python id="xffszb"
from __future__ import annotations

from aiops_agent.workflow.contracts import AgentMessage, RunbookCandidate
from aiops_agent.workflow.collaboration.messages import new_agent_message


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
```

---

## 7.5 `collaboration/safety_agent.py`

路径：

```txt id="e04fy5"
apps/aiops-agent/src/aiops_agent/workflow/collaboration/safety_agent.py
```

```python id="qf6orr"
from __future__ import annotations

from aiops_agent.workflow.contracts import AgentMessage, RunbookCandidate
from aiops_agent.workflow.collaboration.messages import new_agent_message


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
        if severity == "high" and confidence >= 0.65:
            return "high"
        if severity in {"high", "medium"}:
            return "medium"
        return "low"
```

---

## 7.6 `collaboration/reviewer_agent.py`

路径：

```txt id="f9b7ka"
apps/aiops-agent/src/aiops_agent/workflow/collaboration/reviewer_agent.py
```

```python id="06b85d"
from __future__ import annotations

from aiops_agent.workflow.contracts import AgentMessage, RunbookCandidate
from aiops_agent.workflow.collaboration.messages import new_agent_message


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
            next_steps.append("Review advisory runbook candidates before creating automation plan.")

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
```

---

# 8. Multi-Agent Graph Node

## 8.1 `graph/multi_agent_graph.py`

路径：

```txt id="7wxt79"
apps/aiops-agent/src/aiops_agent/workflow/graph/multi_agent_graph.py
```

```python id="ic5fvg"
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
```

---

# 9. Orchestrator 修改

## 9.1 `graph/orchestrator.py`

路径：

```txt id="h7m0az"
apps/aiops-agent/src/aiops_agent/workflow/graph/orchestrator.py
```

完整替换为：

```python id="tq0nl7"
from __future__ import annotations

from langgraph.graph import END, StateGraph

from aiops_agent.workflow.contracts import DiagnosisRequest, DiagnosisResponse, DiagnosisResumeRequest
from aiops_agent.workflow.graph.case_retrieval_graph import retrieve_cases_node
from aiops_agent.workflow.graph.context import GraphContext
from aiops_agent.workflow.graph.evidence_graph import fetch_evidence_node
from aiops_agent.workflow.graph.final_report_graph import final_report_node
from aiops_agent.workflow.graph.human_checkpoint_graph import human_checkpoint_node
from aiops_agent.workflow.graph.multi_agent_graph import (
    multi_agent_rca_node,
    multi_agent_recommendation_node,
)
from aiops_agent.workflow.graph.rca_graph import analyze_rca_node
from aiops_agent.workflow.graph.runbook_graph import recommend_runbook_node
from aiops_agent.workflow.graph.safety_graph import safety_review_node
from aiops_agent.workflow.graph.state import DiagnosisGraphState


def build_diagnosis_graph(context: GraphContext):
    graph = StateGraph(DiagnosisGraphState)

    async def evidence_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
        return await fetch_evidence_node(state, context)

    async def case_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
        return await retrieve_cases_node(state, context)

    async def checkpoint_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
        return await human_checkpoint_node(state, context)

    graph.add_node("evidence", evidence_node)
    graph.add_node("case_retrieval", case_node)
    graph.add_node("rca", analyze_rca_node)
    graph.add_node("multi_agent_rca", multi_agent_rca_node)
    graph.add_node("human_checkpoint", checkpoint_node)
    graph.add_node("runbook", recommend_runbook_node)
    graph.add_node("multi_agent_recommendation", multi_agent_recommendation_node)
    graph.add_node("safety", safety_review_node)
    graph.add_node("final_report", final_report_node)

    graph.set_entry_point("evidence")
    graph.add_edge("evidence", "case_retrieval")

    graph.add_conditional_edges(
        "case_retrieval",
        _route_to_rca,
        {
            "multi_agent_rca": "multi_agent_rca",
            "rca": "rca",
        },
    )

    graph.add_edge("rca", "human_checkpoint")
    graph.add_edge("multi_agent_rca", "human_checkpoint")

    graph.add_conditional_edges(
        "human_checkpoint",
        _route_after_checkpoint,
        {
            "multi_agent_recommendation": "multi_agent_recommendation",
            "runbook": "runbook",
            "final_report": "final_report",
        },
    )

    graph.add_edge("runbook", "safety")
    graph.add_edge("safety", "final_report")
    graph.add_edge("multi_agent_recommendation", "final_report")
    graph.add_edge("final_report", END)

    return graph.compile()


def build_resume_graph():
    graph = StateGraph(DiagnosisGraphState)

    graph.add_node("resume_router", _identity_node)
    graph.add_node("runbook", recommend_runbook_node)
    graph.add_node("multi_agent_recommendation", multi_agent_recommendation_node)
    graph.add_node("safety", safety_review_node)
    graph.add_node("final_report", final_report_node)

    graph.set_entry_point("resume_router")
    graph.add_conditional_edges(
        "resume_router",
        _route_after_resume,
        {
            "multi_agent_recommendation": "multi_agent_recommendation",
            "runbook": "runbook",
        },
    )
    graph.add_edge("runbook", "safety")
    graph.add_edge("safety", "final_report")
    graph.add_edge("multi_agent_recommendation", "final_report")
    graph.add_edge("final_report", END)

    return graph.compile()


async def run_diagnosis_graph(
    request: DiagnosisRequest,
    context: GraphContext,
) -> DiagnosisResponse:
    app = build_diagnosis_graph(context)
    initial_state: DiagnosisGraphState = {
        "tenant_id": request.tenant_id,
        "incident_id": request.incident_id,
        "title": request.title,
        "severity": request.severity,
        "description": request.description,
        "alert_summary": request.alert_summary,
        "tags": request.tags,
        "enable_case_retrieval": request.enable_case_retrieval,
        "enable_runbook_recommendation": request.enable_runbook_recommendation,
        "enable_human_checkpoint": request.enable_human_checkpoint,
        "enable_multi_agent_collaboration": request.enable_multi_agent_collaboration,
        "checkpoint_required": False,
        "checkpoint_id": None,
        "checkpoint_status": None,
        "resume_token": None,
        "evidence": [],
        "similar_cases": [],
        "runbook_candidates": [],
        "safety_notes": [],
        "next_steps": [],
        "agent_messages": [],
        "metadata": {},
    }

    final_state = await app.ainvoke(initial_state)
    return _to_response(final_state)


async def resume_diagnosis_graph(
    request: DiagnosisResumeRequest,
    context: GraphContext,
) -> DiagnosisResponse:
    checkpoint = await context.checkpoint_client.get_checkpoint(
        tenant_id=request.tenant_id,
        checkpoint_id=request.checkpoint_id,
        resume_token=request.resume_token,
    )

    state = DiagnosisGraphState(**checkpoint.state_snapshot)
    state["checkpoint_id"] = checkpoint.checkpoint_id
    state["resume_token"] = checkpoint.resume_token
    state["checkpoint_status"] = checkpoint.status
    state["checkpoint_required"] = False
    state.setdefault("agent_messages", [])

    if checkpoint.status == "rejected":
        state["runbook_candidates"] = []
        state["safety_notes"] = state.get("safety_notes", []) + [
            "Human checkpoint rejected. Runbook recommendation is stopped."
        ]
        state["next_steps"] = ["Revise diagnosis or collect additional evidence."]
        final_report_node(state)
        return _to_response(state)

    if checkpoint.status != "approved":
        state["checkpoint_required"] = True
        state["safety_notes"] = state.get("safety_notes", []) + [
            f"Checkpoint is not approved. Current status={checkpoint.status}."
        ]
        final_report_node(state)
        return _to_response(state)

    app = build_resume_graph()
    final_state = await app.ainvoke(state)
    return _to_response(final_state)


def _route_to_rca(state: DiagnosisGraphState) -> str:
    if state.get("enable_multi_agent_collaboration", False):
        return "multi_agent_rca"
    return "rca"


def _route_after_checkpoint(state: DiagnosisGraphState) -> str:
    status = state.get("checkpoint_status")
    if status not in {"approved", "skipped"}:
        return "final_report"

    if state.get("enable_multi_agent_collaboration", False):
        return "multi_agent_recommendation"

    return "runbook"


def _route_after_resume(state: DiagnosisGraphState) -> str:
    if state.get("enable_multi_agent_collaboration", False):
        return "multi_agent_recommendation"
    return "runbook"


def _identity_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
    return state


def _to_response(final_state: DiagnosisGraphState) -> DiagnosisResponse:
    return DiagnosisResponse(
        tenant_id=final_state["tenant_id"],
        incident_id=final_state["incident_id"],
        summary=final_state.get("final_summary", ""),
        root_cause=final_state.get("root_cause", "Root cause is not confirmed"),
        confidence=float(final_state.get("confidence", 0.0)),
        severity=final_state.get("severity", "medium"),
        risk_level=final_state.get("risk_level", "medium"),
        evidence=final_state.get("evidence", []),
        similar_cases=final_state.get("similar_cases", []),
        runbook_candidates=final_state.get("runbook_candidates", []),
        safety_notes=final_state.get("safety_notes", []),
        next_steps=final_state.get("next_steps", []),
        checkpoint_required=bool(final_state.get("checkpoint_required", False)),
        checkpoint_id=final_state.get("checkpoint_id"),
        checkpoint_status=final_state.get("checkpoint_status"),
        agent_messages=final_state.get("agent_messages", []),
        metadata=final_state.get("metadata", {}),
    )
```

---

# 10. Final Report 修改

## 10.1 `graph/final_report_graph.py`

路径：

```txt id="n8ey9q"
apps/aiops-agent/src/aiops_agent/workflow/graph/final_report_graph.py
```

完整替换为：

```python id="k2n7qj"
from __future__ import annotations

from aiops_agent.workflow.graph.state import DiagnosisGraphState
from aiops_agent.settings import settings


def final_report_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
    title = state.get("title", "")
    root_cause = state.get("root_cause", "Root cause is not confirmed")
    confidence = float(state.get("confidence", 0.0))

    collaboration = (
        "multi-agent"
        if state.get("enable_multi_agent_collaboration", False)
        else "single-agent"
    )

    state["final_summary"] = (
        f"Incident '{title}' diagnosis completed by {collaboration} graph. "
        f"Root cause: {root_cause}. Confidence={confidence:.2f}."
    )

    if not state.get("next_steps"):
        state["next_steps"] = build_next_steps(state)

    metadata = state.get("metadata", {})
    metadata["graph_version"] = settings.graph_version
    metadata["graph_modules"] = [
        "evidence_graph",
        "case_retrieval_graph",
        "rca_graph",
        "multi_agent_graph",
        "human_checkpoint_graph",
        "runbook_graph",
        "safety_graph",
        "final_report_graph",
    ]
    metadata["collaboration_mode"] = (
        "multi_agent"
        if state.get("enable_multi_agent_collaboration", False)
        else "single_agent"
    )
    state["metadata"] = metadata
    return state


def build_next_steps(state: DiagnosisGraphState) -> list[str]:
    if state.get("checkpoint_status") == "pending":
        return [
            "Human checkpoint is pending.",
            "Review the RCA checkpoint and approve or reject it.",
        ]

    if state.get("checkpoint_status") == "rejected":
        return [
            "Human checkpoint was rejected.",
            "Revise diagnosis or collect additional evidence.",
        ]

    if state.get("checkpoint_status") == "failed":
        return [
            "Checkpoint creation failed.",
            "Check Java checkpoint API or internal network policy.",
        ]

    steps = [
        "Review evidence and confirm root cause.",
        "Review recommended runbook candidates.",
    ]

    if state.get("enable_multi_agent_collaboration", False):
        steps.insert(0, "Review multi-agent message ledger.")

    if state.get("risk_level") in {"high", "critical"}:
        steps.append("Require human approval before any remediation.")

    if float(state.get("confidence", 0.0)) < 0.6:
        steps.append("Collect additional metrics, logs, and recent change events.")

    return steps
```

---

# 11. Service / Main 小改

## 11.1 `services/diagnosis_service.py`

保持 Phase7.1 版本即可，确认 GraphContext 有 `checkpoint_client`：

```python id="iqpxs7"
from __future__ import annotations

from aiops_agent.workflow.contracts import DiagnosisRequest, DiagnosisResponse, DiagnosisResumeRequest
from aiops_agent.workflow.graph.context import GraphContext
from aiops_agent.workflow.graph.orchestrator import resume_diagnosis_graph, run_diagnosis_graph
from aiops_agent.workflow.tools.checkpoint_client import CheckpointClient
from aiops_agent.workflow.tools.evidence_client import EvidenceClient
from aiops_agent.workflow.tools.knowledge_client import KnowledgeClient


class DiagnosisService:
    def __init__(self, context: GraphContext | None = None):
        self.context = context or GraphContext(
            evidence_client=EvidenceClient(),
            knowledge_client=KnowledgeClient(),
            checkpoint_client=CheckpointClient(),
        )

    async def diagnose(self, request: DiagnosisRequest) -> DiagnosisResponse:
        return await run_diagnosis_graph(request, self.context)

    async def resume(self, request: DiagnosisResumeRequest) -> DiagnosisResponse:
        return await resume_diagnosis_graph(request, self.context)
```

---

# 12. 单元测试

## 12.1 `test_multi_agent_agents.py`

路径：

```txt id="6hldjr"
apps/aiops-agent/tests/test_multi_agent_agents.py
```

```python id="a2b3hi"
from __future__ import annotations

from aiops_agent.workflow.collaboration.evidence_agent import EvidenceAgent
from aiops_agent.workflow.collaboration.rca_agent import RCAAgent
from aiops_agent.workflow.collaboration.runbook_agent import RunbookAgent
from aiops_agent.workflow.collaboration.safety_agent import SafetyAgent
from aiops_agent.workflow.collaboration.reviewer_agent import ReviewerAgent
from aiops_agent.workflow.contracts import EvidenceItem, RunbookCandidate, SimilarCase


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
```

---

## 12.2 `test_multi_agent_graph.py`

路径：

```txt id="fjtc54"
apps/aiops-agent/tests/test_multi_agent_graph.py
```

```python id="4yu2q4"
from __future__ import annotations

from aiops_agent.workflow.contracts import EvidenceItem, SimilarCase
from aiops_agent.workflow.graph.multi_agent_graph import (
    multi_agent_rca_node,
    multi_agent_recommendation_node,
)


def test_multi_agent_rca_node_adds_messages_and_root_cause():
    state = {
        "title": "Order service redis timeout",
        "evidence": [
            EvidenceItem(
                evidence_id="ev_1",
                evidence_type="log",
                title="Redis timeout",
                summary="redis timeout happened",
            )
        ],
        "similar_cases": [
            SimilarCase(
                case_id="case_1",
                title="Redis timeout case",
                summary="summary",
                root_cause="redis timeout",
                score=0.9,
            )
        ],
        "agent_messages": [],
    }

    result = multi_agent_rca_node(state)

    assert result["root_cause"] == "redis timeout"
    assert result["confidence"] > 0.3
    assert len(result["agent_messages"]) == 2
    assert result["agent_messages"][0].role == "evidence_agent"
    assert result["agent_messages"][1].role == "rca_agent"


def test_multi_agent_recommendation_node_adds_runbook_safety_reviewer_messages():
    state = {
        "root_cause": "Possible timeout or dependency latency issue",
        "severity": "high",
        "confidence": 0.8,
        "enable_runbook_recommendation": True,
        "agent_messages": [],
    }

    result = multi_agent_recommendation_node(state)

    assert len(result["runbook_candidates"]) >= 1
    assert result["risk_level"] == "high"
    assert len(result["agent_messages"]) == 3
    assert result["agent_messages"][0].role == "runbook_agent"
    assert result["agent_messages"][1].role == "safety_agent"
    assert result["agent_messages"][2].role == "reviewer_agent"
```

---

## 12.3 修改 `tests/fakes.py`

确认 `GraphContext` 现在有三个 client。最终建议：

```python id="d70sjq"
from __future__ import annotations

from aiops_agent.workflow.contracts import AgentCheckpoint, EvidenceItem, SimilarCase


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
```

---

## 12.4 修改 `test_phase7_orchestrator.py`

新增两个测试。

```python id="fsyq9x"
from __future__ import annotations

import pytest

from aiops_agent.workflow.contracts import DiagnosisRequest, DiagnosisResumeRequest, EvidenceItem, SimilarCase
from aiops_agent.workflow.graph.context import GraphContext
from aiops_agent.workflow.graph.orchestrator import resume_diagnosis_graph, run_diagnosis_graph
from tests.fakes import (
    ApprovedCheckpointClient,
    FakeCheckpointClient,
    FakeEvidenceClient,
    FakeKnowledgeClient,
)


@pytest.mark.asyncio
async def test_orchestrator_runs_multi_agent_collaboration():
    context = GraphContext(
        evidence_client=FakeEvidenceClient(
            [
                EvidenceItem(
                    evidence_id="ev_1",
                    evidence_type="log",
                    title="Redis timeout",
                    summary="redis dependency timeout happened",
                    source="test",
                )
            ]
        ),
        knowledge_client=FakeKnowledgeClient(
            [
                SimilarCase(
                    case_id="case_1",
                    title="Redis timeout case",
                    summary="Redis timeout caused service degradation",
                    root_cause="redis timeout",
                    resolution="increase timeout and check pool",
                    score=0.9,
                )
            ]
        ),
        checkpoint_client=FakeCheckpointClient(),
    )

    response = await run_diagnosis_graph(
        DiagnosisRequest(
            tenant_id="tenant_1",
            incident_id="inc_1",
            title="Order service timeout",
            severity="high",
            tags=["redis"],
            enable_human_checkpoint=False,
            enable_multi_agent_collaboration=True,
        ),
        context,
    )

    assert response.root_cause == "redis timeout"
    assert len(response.agent_messages) == 5
    assert [message.role for message in response.agent_messages] == [
        "evidence_agent",
        "rca_agent",
        "runbook_agent",
        "safety_agent",
        "reviewer_agent",
    ]
    assert response.metadata["collaboration_mode"] == "multi_agent"
    assert len(response.runbook_candidates) >= 1


@pytest.mark.asyncio
async def test_resume_after_checkpoint_approved_uses_multi_agent_recommendation():
    checkpoint_client = ApprovedCheckpointClient()
    checkpoint_client.checkpoint.state_snapshot = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "title": "Order service timeout",
        "severity": "high",
        "description": None,
        "alert_summary": None,
        "tags": ["redis"],
        "enable_case_retrieval": True,
        "enable_runbook_recommendation": True,
        "enable_human_checkpoint": True,
        "enable_multi_agent_collaboration": True,
        "evidence": [],
        "similar_cases": [],
        "root_cause": "redis timeout",
        "confidence": 0.8,
        "risk_level": "high",
        "agent_messages": [],
        "metadata": {},
    }

    context = GraphContext(
        evidence_client=FakeEvidenceClient(),
        knowledge_client=FakeKnowledgeClient(),
        checkpoint_client=checkpoint_client,
    )

    response = await resume_diagnosis_graph(
        DiagnosisResumeRequest(
            tenant_id="tenant_1",
            checkpoint_id="agcp_1",
        ),
        context,
    )

    assert response.checkpoint_status == "approved"
    assert len(response.runbook_candidates) >= 1
    assert len(response.agent_messages) == 3
    assert response.agent_messages[-1].role == "reviewer_agent"
    assert response.metadata["collaboration_mode"] == "multi_agent"
```

---

## 12.5 修改旧测试的 GraphContext 构造

所有旧测试里：

```python id="w36gp8"
GraphContext(
    evidence_client=FakeEvidenceClient(),
    knowledge_client=FakeKnowledgeClient(),
)
```

都改成：

```python id="emcolh"
GraphContext(
    evidence_client=FakeEvidenceClient(),
    knowledge_client=FakeKnowledgeClient(),
    checkpoint_client=FakeCheckpointClient(),
)
```

典型文件：

```txt id="qjilb7"
test_evidence_graph.py
test_case_retrieval_graph.py
test_human_checkpoint_graph.py
test_phase7_orchestrator.py
```

---

# 13. 文档

路径：

```txt id="wx5ltt"
docs/mvp/design/phase7.2-multi-agent-collaboration.md
```

````md id="2sd8qp"
# Phase7.2 Multi-Agent Collaboration

## 目标

在同一个 Python Agent Runtime 内引入多角色协作：

- Evidence Agent
- RCA Agent
- Runbook Agent
- Safety Agent
- Reviewer Agent

## 不做

- 不做多进程 Agent
- 不做多服务 Agent
- 不做真实 LLM 自动辩论
- 不调 Runner
- 不创建 execution
- 不创建 automation_plan
- 不自动修复
- 不自动回滚
- 不做长期 Memory

## Graph

input
-> evidence_graph
-> case_retrieval_graph
-> multi_agent_rca_node
-> EvidenceAgent
-> RCAAgent
-> human_checkpoint_graph
-> multi_agent_recommendation_node
-> RunbookAgent
-> SafetyAgent
-> ReviewerAgent
-> final_report_graph

## Request Flag

```json
{
  "enable_multi_agent_collaboration": true
}
```
````

默认 false，保证兼容原单 Agent graph。

## Output

DiagnosisResponse 新增：

```json
{
  "agent_messages": []
}
```

每条 message 包含：

- message_id
- role
- title
- content
- confidence
- metadata

## 安全边界

多 Agent 仍然只输出建议。

不得创建 automation_plan。
不得创建 execution。
不得调用 runner。
不得直接 webhook / ansible / ssh。
不得 rollback。

## 验收标准

1. enable_multi_agent_collaboration=false 时走原 graph。
2. enable_multi_agent_collaboration=true 时产生 agent_messages。
3. EvidenceAgent 生成 evidence review message。
4. RCAAgent 生成 root cause proposal。
5. RunbookAgent 生成 advisory candidate。
6. SafetyAgent 过滤危险 action。
7. ReviewerAgent 生成 final reviewer summary。
8. HITL approved resume 后仍然支持 multi-agent recommendation。
9. 不新增执行能力。

````

---

# 14. 验证命令

```bash id="x8f31a"
cd apps/aiops-agent
pip install -e ".[test]"
pytest -q
````

---

# 15. 验收标准

```txt id="wtj40w"
1. /v1/diagnose contract 向后兼容。
2. enable_multi_agent_collaboration 默认为 false。
3. false 时走原单 Agent graph。
4. true 时走 multi_agent_rca_node。
5. true 时走 multi_agent_recommendation_node。
6. 返回 agent_messages。
7. agent_messages 包含 evidence_agent。
8. agent_messages 包含 rca_agent。
9. agent_messages 包含 runbook_agent。
10. agent_messages 包含 safety_agent。
11. agent_messages 包含 reviewer_agent。
12. SafetyAgent 能过滤 ssh/shell/ansible/webhook/delete/rollback/restart。
13. SafetyAgent 能过滤危险文本 rm -rf / drop database。
14. HITL pending 时仍然暂停。
15. HITL approved resume 后继续 multi-agent recommendation。
16. 不创建 automation_plan。
17. 不创建 execution_run。
18. 不调用 runner。
```

---

# 16. 建议提交信息

```txt id="r39mhy"
feat(agent): add multi-agent collaboration graph
```

---

# 17. 下一步 Phase7.3

Phase7.2 完成后进入：

```txt id="zazdy7"
Phase7.3 Agent Memory
```

Phase7.3 才做：

```txt id="svy7lq"
short-term memory
incident session memory
case-derived memory
memory write policy
memory retrieval
memory safety boundary
```

不要把 Memory 混进 Phase7.2。
