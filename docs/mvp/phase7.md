# Phase7.0：Agent Graph Modularization

> Phase7.0 目标：把 Phase4.x 已经可运行的单体诊断 Agent 拆成清晰的 Graph 子模块。
> 这一阶段 **不新增执行能力**，不调 Runner，不做多 Agent，不做长期 Memory，只做 Agent 内部 Graph 模块化，为 Phase7.1 HITL、Phase7.2 Multi-Agent、Phase7.3 Agent Memory 铺路。

---

# 1. Phase7.0 定位

当前已完成：

```txt
Phase4.x  Python LangGraph Diagnosis Agent
Phase5.x  Runbook / Approval / Execution / Rollback / Report
Phase6.x  Postmortem / Case Library / KB Retrieval / Eval
```

Phase7.0 要把原来的：

```txt
单体 diagnose_graph
```

拆成：

```txt
diagnosis_orchestrator_graph
  ├── evidence_graph
  ├── case_retrieval_graph
  ├── rca_graph
  ├── runbook_graph
  ├── safety_graph
  └── final_report_graph
```

---

# 2. 不做什么

```txt
1. 不做多 Agent 协作
2. 不做 Human-in-the-loop checkpoint
3. 不做 Agent Memory
4. 不做自动执行
5. 不调 Runner
6. 不创建 AutomationPlan
7. 不审批
8. 不回滚
```

这些放到后续：

```txt
Phase7.1  Human-in-the-loop Checkpoint
Phase7.2  Multi-Agent Collaboration
Phase7.3  Agent Memory
```

---

# 3. 核心设计

## 3.1 Graph 拆分目标

原来 Agent 可能是：

```txt
diagnose(input)
  -> fetch evidence
  -> analyze
  -> recommend
  -> output
```

Phase7.0 后变成：

```txt
input
  -> evidence_graph
  -> case_retrieval_graph
  -> rca_graph
  -> runbook_graph
  -> safety_graph
  -> final_report_graph
  -> output
```

每个子图只负责一件事：

| 子图                 | 职责                                         | 是否允许执行 |
| -------------------- | -------------------------------------------- | ------------ |
| evidence_graph       | 拉取 metrics/logs/changes/rca/ai 上下文      | 否           |
| case_retrieval_graph | 调用 Phase6.2 KB 检索相似 case               | 否           |
| rca_graph            | 生成 root cause / confidence / evidence refs | 否           |
| runbook_graph        | 推荐 runbook/action draft 文本               | 否           |
| safety_graph         | 风险评估、禁止动作过滤                       | 否           |
| final_report_graph   | 组装最终 DiagnosisResponse                   | 否           |

---

# 4. 目录结构

新增/调整 Python Agent 目录：

```txt
apps/aiops-agent/
  app/
    main.py
    agent/
      __init__.py
      contracts.py
      settings.py
      errors.py
      graph/
        __init__.py
        state.py
        context.py
        orchestrator.py
        evidence_graph.py
        case_retrieval_graph.py
        rca_graph.py
        runbook_graph.py
        safety_graph.py
        final_report_graph.py
      tools/
        __init__.py
        evidence_client.py
        knowledge_client.py
      services/
        __init__.py
        diagnosis_service.py
  tests/
    test_phase7_orchestrator.py
    test_evidence_graph.py
    test_case_retrieval_graph.py
    test_rca_graph.py
    test_runbook_graph.py
    test_safety_graph.py
    test_final_report_graph.py
```

---

# 5. 依赖

如果之前已有 LangGraph / FastAPI，可以只补测试依赖。

`apps/aiops-agent/pyproject.toml`

```toml
[project]
name = "aiops-agent"
version = "0.1.0"
requires-python = ">=3.11"
dependencies = [
  "fastapi>=0.111.0",
  "uvicorn>=0.30.0",
  "pydantic>=2.7.0",
  "pydantic-settings>=2.2.1",
  "httpx>=0.27.0",
  "langgraph>=0.2.0"
]

[project.optional-dependencies]
test = [
  "pytest>=8.2.0",
  "pytest-asyncio>=0.23.0",
  "respx>=0.21.0"
]

[tool.pytest.ini_options]
asyncio_mode = "auto"
testpaths = ["tests"]
```

---

# 6. 完整代码

## 6.1 `contracts.py`

路径：

```txt
apps/aiops-agent/src/aiops_agent/workflow/contracts.py
```

```python
from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


Severity = Literal["info", "low", "medium", "high", "critical"]
RiskLevel = Literal["low", "medium", "high", "critical"]


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
    metadata: dict[str, Any] = Field(default_factory=dict)


class HealthResponse(BaseModel):
    status: str = "ok"


class DiagnosisContractResponse(BaseModel):
    contract_version: str = "agent-diagnosis.v1"
    input_model: str = "DiagnosisRequest"
    output_model: str = "DiagnosisResponse"
    graph_version: str = "phase7.0-modular-graph"
```

---

## 6.2 `settings.py`

路径：

```txt
apps/aiops-agent/src/aiops_agent/settings.py
```

```python
from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class AgentSettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="AIOPS_AGENT_", extra="ignore")

    evidence_api_base_url: str = "http://localhost:8080"
    knowledge_api_base_url: str = "http://localhost:8080"
    request_timeout_seconds: float = 5.0
    graph_version: str = "phase7.0-modular-graph"
    max_evidence_items: int = 8
    max_similar_cases: int = 5


settings = AgentSettings()
```

---

## 6.3 `errors.py`

路径：

```txt
apps/aiops-agent/src/aiops_agent/workflow/errors.py
```

```python
from __future__ import annotations


class AgentError(Exception):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


class ToolError(AgentError):
    pass
```

---

## 6.4 `graph/state.py`

路径：

```txt
apps/aiops-agent/src/aiops_agent/workflow/graph/state.py
```

```python
from __future__ import annotations

from typing import Any, TypedDict

from aiops_agent.workflow.contracts import EvidenceItem, RunbookCandidate, SimilarCase


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

    evidence: list[EvidenceItem]
    similar_cases: list[SimilarCase]
    root_cause: str
    confidence: float
    runbook_candidates: list[RunbookCandidate]
    safety_notes: list[str]
    next_steps: list[str]
    risk_level: str
    final_summary: str
    metadata: dict[str, Any]
```

---

## 6.5 `graph/context.py`

路径：

```txt
apps/aiops-agent/src/aiops_agent/workflow/graph/context.py
```

```python
from __future__ import annotations

from dataclasses import dataclass

from aiops_agent.workflow.tools.evidence_client import EvidenceClient
from aiops_agent.workflow.tools.knowledge_client import KnowledgeClient


@dataclass(slots=True)
class GraphContext:
    evidence_client: EvidenceClient
    knowledge_client: KnowledgeClient
```

---

# 7. Tools

## 7.1 `tools/evidence_client.py`

路径：

```txt
apps/aiops-agent/src/aiops_agent/workflow/tools/evidence_client.py
```

```python
from __future__ import annotations

import httpx

from aiops_agent.workflow.contracts import EvidenceItem
from aiops_agent.workflow.errors import ToolError
from aiops_agent.settings import settings


class EvidenceClient:
    def __init__(self, base_url: str | None = None, timeout: float | None = None):
        self.base_url = (base_url or settings.evidence_api_base_url).rstrip("/")
        self.timeout = timeout or settings.request_timeout_seconds

    async def fetch_evidence(self, tenant_id: str, incident_id: str) -> list[EvidenceItem]:
        url = f"{self.base_url}/internal/agent/incidents/{incident_id}/evidence"

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.get(url, headers={"X-Tenant-Id": tenant_id})
                if response.status_code == 404:
                    return []
                response.raise_for_status()
                payload = response.json()
        except httpx.HTTPError as exc:
            raise ToolError("EVIDENCE_TOOL_FAILED", f"Failed to fetch evidence: {exc}") from exc

        data = payload.get("data", payload)
        items = data.get("items", data if isinstance(data, list) else [])
        return [EvidenceItem.model_validate(item) for item in items]
```

---

## 7.2 `tools/knowledge_client.py`

路径：

```txt
apps/aiops-agent/src/aiops_agent/workflow/tools/knowledge_client.py
```

```python
from __future__ import annotations

import httpx

from aiops_agent.workflow.contracts import SimilarCase
from aiops_agent.workflow.errors import ToolError
from aiops_agent.settings import settings


class KnowledgeClient:
    def __init__(self, base_url: str | None = None, timeout: float | None = None):
        self.base_url = (base_url or settings.knowledge_api_base_url).rstrip("/")
        self.timeout = timeout or settings.request_timeout_seconds

    async def search_cases(
        self,
        tenant_id: str,
        query: str,
        tags: list[str],
        top_k: int,
    ) -> list[SimilarCase]:
        url = f"{self.base_url}/internal/agent/tools/search-cases"
        body = {
            "query": query,
            "tags": tags,
            "topK": top_k,
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.post(url, json=body, headers={"X-Tenant-Id": tenant_id})
                if response.status_code == 404:
                    return []
                response.raise_for_status()
                payload = response.json()
        except httpx.HTTPError as exc:
            raise ToolError("KNOWLEDGE_TOOL_FAILED", f"Failed to search cases: {exc}") from exc

        data = payload.get("data", payload)
        results = data.get("results", [])
        return [
            SimilarCase(
                case_id=item.get("sourceId", item.get("caseId", "")),
                title=item.get("title", ""),
                summary=item.get("content", ""),
                root_cause=item.get("rootCause"),
                resolution=item.get("resolution"),
                score=float(item.get("score", 0.0)),
                tags=item.get("tags", []),
            )
            for item in results
        ]
```

---

# 8. 子图实现

## 8.1 `graph/evidence_graph.py`

路径：

```txt
apps/aiops-agent/src/aiops_agent/workflow/graph/evidence_graph.py
```

```python
from __future__ import annotations

from aiops_agent.workflow.contracts import EvidenceItem
from aiops_agent.workflow.errors import ToolError
from aiops_agent.workflow.graph.context import GraphContext
from aiops_agent.workflow.graph.state import DiagnosisGraphState
from aiops_agent.settings import settings


async def fetch_evidence_node(
    state: DiagnosisGraphState,
    context: GraphContext,
) -> DiagnosisGraphState:
    tenant_id = state["tenant_id"]
    incident_id = state["incident_id"]

    try:
        evidence = await context.evidence_client.fetch_evidence(tenant_id, incident_id)
    except ToolError as exc:
        evidence = [
            EvidenceItem(
                evidence_id="evidence_tool_error",
                evidence_type="tool_error",
                title="Evidence fetch failed",
                summary=exc.message,
                source="agent",
                metadata={"code": exc.code},
            )
        ]

    state["evidence"] = evidence[: settings.max_evidence_items]
    return state


def build_evidence_query(state: DiagnosisGraphState) -> str:
    parts = [
        state.get("title", ""),
        state.get("alert_summary") or "",
        state.get("description") or "",
        " ".join(state.get("tags", [])),
    ]
    return "\n".join(part for part in parts if part)
```

---

## 8.2 `graph/case_retrieval_graph.py`

路径：

```txt
apps/aiops-agent/src/aiops_agent/workflow/graph/case_retrieval_graph.py
```

```python
from __future__ import annotations

from aiops_agent.workflow.errors import ToolError
from aiops_agent.workflow.graph.context import GraphContext
from aiops_agent.workflow.graph.evidence_graph import build_evidence_query
from aiops_agent.workflow.graph.state import DiagnosisGraphState
from aiops_agent.settings import settings


async def retrieve_cases_node(
    state: DiagnosisGraphState,
    context: GraphContext,
) -> DiagnosisGraphState:
    if not state.get("enable_case_retrieval", True):
        state["similar_cases"] = []
        return state

    query = build_evidence_query(state)

    try:
        cases = await context.knowledge_client.search_cases(
            tenant_id=state["tenant_id"],
            query=query,
            tags=state.get("tags", []),
            top_k=settings.max_similar_cases,
        )
    except ToolError:
        cases = []

    state["similar_cases"] = cases[: settings.max_similar_cases]
    return state
```

---

## 8.3 `graph/rca_graph.py`

路径：

```txt
apps/aiops-agent/src/aiops_agent/workflow/graph/rca_graph.py
```

```python
from __future__ import annotations

from aiops_agent.workflow.graph.state import DiagnosisGraphState


def analyze_rca_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
    evidence = state.get("evidence", [])
    cases = state.get("similar_cases", [])

    root_cause = infer_root_cause(state)
    confidence = infer_confidence(evidence_count=len(evidence), case_count=len(cases))

    state["root_cause"] = root_cause
    state["confidence"] = confidence
    return state


def infer_root_cause(state: DiagnosisGraphState) -> str:
    evidence = state.get("evidence", [])
    cases = state.get("similar_cases", [])

    if cases and cases[0].root_cause:
        return cases[0].root_cause

    for item in evidence:
        summary = item.summary.lower()
        if "timeout" in summary:
            return "Possible timeout or dependency latency issue"
        if "error rate" in summary or "5xx" in summary:
            return "Possible service error rate increase"
        if "cpu" in summary:
            return "Possible resource saturation"

    title = state.get("title", "").lower()
    if "timeout" in title:
        return "Possible timeout or dependency latency issue"

    return "Root cause is not confirmed"


def infer_confidence(evidence_count: int, case_count: int) -> float:
    score = 0.35
    score += min(evidence_count, 5) * 0.08
    score += min(case_count, 3) * 0.08
    return round(min(score, 0.9), 2)
```

---

## 8.4 `graph/runbook_graph.py`

路径：

```txt
apps/aiops-agent/src/aiops_agent/workflow/graph/runbook_graph.py
```

```python
from __future__ import annotations

from aiops_agent.workflow.contracts import RunbookCandidate
from aiops_agent.workflow.graph.state import DiagnosisGraphState


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
```

---

## 8.5 `graph/safety_graph.py`

路径：

```txt
apps/aiops-agent/src/aiops_agent/workflow/graph/safety_graph.py
```

```python
from __future__ import annotations

from aiops_agent.workflow.contracts import RunbookCandidate
from aiops_agent.workflow.graph.state import DiagnosisGraphState


FORBIDDEN_ACTION_TYPES = {
    "shell",
    "ssh",
    "ansible",
    "webhook",
    "delete",
    "rollback",
    "restart",
}


def safety_review_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
    candidates = state.get("runbook_candidates", [])
    safe_candidates: list[RunbookCandidate] = []
    notes: list[str] = []

    for candidate in candidates:
        if candidate.action_type.lower() in FORBIDDEN_ACTION_TYPES:
            notes.append(
                f"Blocked executable action candidate: {candidate.title} ({candidate.action_type})"
            )
            continue
        safe_candidates.append(candidate)

    risk_level = infer_risk_level(state)

    notes.append(
        "Agent recommendation is advisory only. Execution must go through approval and runner."
    )

    state["runbook_candidates"] = safe_candidates
    state["risk_level"] = risk_level
    state["safety_notes"] = notes
    return state


def infer_risk_level(state: DiagnosisGraphState) -> str:
    severity = state.get("severity", "medium")
    confidence = state.get("confidence", 0.0)

    if severity == "critical":
        return "critical"
    if severity == "high" and confidence >= 0.65:
        return "high"
    if severity in {"high", "medium"}:
        return "medium"
    return "low"
```

---

## 8.6 `graph/final_report_graph.py`

路径：

```txt
apps/aiops-agent/src/aiops_agent/workflow/graph/final_report_graph.py
```

```python
from __future__ import annotations

from aiops_agent.workflow.graph.state import DiagnosisGraphState
from aiops_agent.settings import settings


def final_report_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
    title = state.get("title", "")
    root_cause = state.get("root_cause", "Root cause is not confirmed")
    confidence = state.get("confidence", 0.0)

    state["final_summary"] = (
        f"Incident '{title}' diagnosis completed. "
        f"Root cause: {root_cause}. Confidence={confidence:.2f}."
    )

    state["next_steps"] = build_next_steps(state)

    metadata = state.get("metadata", {})
    metadata["graph_version"] = settings.graph_version
    metadata["graph_modules"] = [
        "evidence_graph",
        "case_retrieval_graph",
        "rca_graph",
        "runbook_graph",
        "safety_graph",
        "final_report_graph",
    ]
    state["metadata"] = metadata
    return state


def build_next_steps(state: DiagnosisGraphState) -> list[str]:
    steps = [
        "Review evidence and confirm root cause.",
        "Review recommended runbook candidates.",
    ]

    if state.get("risk_level") in {"high", "critical"}:
        steps.append("Require human approval before any remediation.")

    if state.get("confidence", 0.0) < 0.6:
        steps.append("Collect additional metrics, logs, and recent change events.")

    return steps
```

---

# 9. Orchestrator

## 9.1 `graph/orchestrator.py`

路径：

```txt
apps/aiops-agent/src/aiops_agent/workflow/graph/orchestrator.py
```

```python
from __future__ import annotations

from langgraph.graph import END, StateGraph

from aiops_agent.workflow.contracts import DiagnosisRequest, DiagnosisResponse
from aiops_agent.workflow.graph.case_retrieval_graph import retrieve_cases_node
from aiops_agent.workflow.graph.context import GraphContext
from aiops_agent.workflow.graph.evidence_graph import fetch_evidence_node
from aiops_agent.workflow.graph.final_report_graph import final_report_node
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

    graph.add_node("evidence", evidence_node)
    graph.add_node("case_retrieval", case_node)
    graph.add_node("rca", analyze_rca_node)
    graph.add_node("runbook", recommend_runbook_node)
    graph.add_node("safety", safety_review_node)
    graph.add_node("final_report", final_report_node)

    graph.set_entry_point("evidence")
    graph.add_edge("evidence", "case_retrieval")
    graph.add_edge("case_retrieval", "rca")
    graph.add_edge("rca", "runbook")
    graph.add_edge("runbook", "safety")
    graph.add_edge("safety", "final_report")
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
        "evidence": [],
        "similar_cases": [],
        "runbook_candidates": [],
        "safety_notes": [],
        "next_steps": [],
        "metadata": {},
    }

    final_state = await app.ainvoke(initial_state)

    return DiagnosisResponse(
        tenant_id=request.tenant_id,
        incident_id=request.incident_id,
        summary=final_state.get("final_summary", ""),
        root_cause=final_state.get("root_cause", "Root cause is not confirmed"),
        confidence=float(final_state.get("confidence", 0.0)),
        severity=request.severity,
        risk_level=final_state.get("risk_level", "medium"),
        evidence=final_state.get("evidence", []),
        similar_cases=final_state.get("similar_cases", []),
        runbook_candidates=final_state.get("runbook_candidates", []),
        safety_notes=final_state.get("safety_notes", []),
        next_steps=final_state.get("next_steps", []),
        metadata=final_state.get("metadata", {}),
    )
```

---

# 10. Service

## 10.1 `services/diagnosis_service.py`

路径：

```txt
apps/aiops-agent/src/aiops_agent/service.py
```

```python
from __future__ import annotations

from aiops_agent.workflow.contracts import DiagnosisRequest, DiagnosisResponse
from aiops_agent.workflow.graph.context import GraphContext
from aiops_agent.workflow.graph.orchestrator import run_diagnosis_graph
from aiops_agent.workflow.tools.evidence_client import EvidenceClient
from aiops_agent.workflow.tools.knowledge_client import KnowledgeClient


class DiagnosisService:
    def __init__(self, context: GraphContext | None = None):
        self.context = context or GraphContext(
            evidence_client=EvidenceClient(),
            knowledge_client=KnowledgeClient(),
        )

    async def diagnose(self, request: DiagnosisRequest) -> DiagnosisResponse:
        return await run_diagnosis_graph(request, self.context)
```

---

# 11. FastAPI

## 11.1 `main.py`

路径：

```txt
apps/aiops-agent/src/aiops_agent/main.py
```

```python
from __future__ import annotations

from fastapi import FastAPI

from aiops_agent.workflow.contracts import (
    DiagnosisContractResponse,
    DiagnosisRequest,
    DiagnosisResponse,
    HealthResponse,
)
from aiops_agent.service import DiagnosisService

app = FastAPI(title="AegisOps Agent", version="0.7.0")

diagnosis_service = DiagnosisService()


@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    return HealthResponse()


@app.get("/v1/contracts/diagnosis", response_model=DiagnosisContractResponse)
async def diagnosis_contract() -> DiagnosisContractResponse:
    return DiagnosisContractResponse()


@app.post("/v1/diagnose", response_model=DiagnosisResponse)
async def diagnose(request: DiagnosisRequest) -> DiagnosisResponse:
    return await diagnosis_service.diagnose(request)
```

---

# 12. 单元测试

## 12.1 `tests/fakes.py`

路径：

```txt
apps/aiops-agent/tests/fakes.py
```

```python
from __future__ import annotations

from aiops_agent.workflow.contracts import EvidenceItem, SimilarCase


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
```

---

## 12.2 `test_evidence_graph.py`

路径：

```txt
apps/aiops-agent/tests/test_evidence_graph.py
```

```python
from __future__ import annotations

import pytest

from aiops_agent.workflow.contracts import EvidenceItem
from aiops_agent.workflow.graph.context import GraphContext
from aiops_agent.workflow.graph.evidence_graph import fetch_evidence_node
from tests.fakes import FakeEvidenceClient, FakeKnowledgeClient


@pytest.mark.asyncio
async def test_fetch_evidence_node_sets_evidence():
    evidence = [
        EvidenceItem(
            evidence_id="ev_1",
            evidence_type="metric",
            title="Error rate",
            summary="5xx error rate increased",
            source="test",
        )
    ]
    context = GraphContext(
        evidence_client=FakeEvidenceClient(evidence),
        knowledge_client=FakeKnowledgeClient(),
    )

    state = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "title": "Order service error",
    }

    result = await fetch_evidence_node(state, context)

    assert len(result["evidence"]) == 1
    assert result["evidence"][0].evidence_id == "ev_1"
```

---

## 12.3 `test_case_retrieval_graph.py`

路径：

```txt
apps/aiops-agent/tests/test_case_retrieval_graph.py
```

```python
from __future__ import annotations

import pytest

from aiops_agent.workflow.contracts import SimilarCase
from aiops_agent.workflow.graph.case_retrieval_graph import retrieve_cases_node
from aiops_agent.workflow.graph.context import GraphContext
from tests.fakes import FakeEvidenceClient, FakeKnowledgeClient


@pytest.mark.asyncio
async def test_retrieve_cases_node_calls_knowledge_client():
    knowledge = FakeKnowledgeClient(
        [
            SimilarCase(
                case_id="case_1",
                title="Redis timeout",
                summary="Redis timeout caused order service errors",
                root_cause="redis timeout",
                score=0.9,
            )
        ]
    )
    context = GraphContext(
        evidence_client=FakeEvidenceClient(),
        knowledge_client=knowledge,
    )

    state = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "title": "Order service redis timeout",
        "tags": ["order-service"],
        "enable_case_retrieval": True,
    }

    result = await retrieve_cases_node(state, context)

    assert knowledge.called is True
    assert len(result["similar_cases"]) == 1
    assert result["similar_cases"][0].case_id == "case_1"


@pytest.mark.asyncio
async def test_retrieve_cases_node_can_be_disabled():
    knowledge = FakeKnowledgeClient()
    context = GraphContext(
        evidence_client=FakeEvidenceClient(),
        knowledge_client=knowledge,
    )

    state = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "title": "Order service redis timeout",
        "tags": [],
        "enable_case_retrieval": False,
    }

    result = await retrieve_cases_node(state, context)

    assert knowledge.called is False
    assert result["similar_cases"] == []
```

---

## 12.4 `test_rca_graph.py`

路径：

```txt
apps/aiops-agent/tests/test_rca_graph.py
```

```python
from __future__ import annotations

from aiops_agent.workflow.contracts import EvidenceItem, SimilarCase
from aiops_agent.workflow.graph.rca_graph import analyze_rca_node


def test_rca_prefers_similar_case_root_cause():
    state = {
        "title": "Order service error",
        "evidence": [
            EvidenceItem(
                evidence_id="ev_1",
                evidence_type="metric",
                title="Error rate",
                summary="5xx increased",
            )
        ],
        "similar_cases": [
            SimilarCase(
                case_id="case_1",
                title="Redis timeout",
                summary="Redis timeout",
                root_cause="redis connection timeout",
                score=0.9,
            )
        ],
    }

    result = analyze_rca_node(state)

    assert result["root_cause"] == "redis connection timeout"
    assert result["confidence"] > 0.4


def test_rca_uses_evidence_when_no_case():
    state = {
        "title": "Order service error",
        "evidence": [
            EvidenceItem(
                evidence_id="ev_1",
                evidence_type="log",
                title="Timeout",
                summary="dependency timeout happened",
            )
        ],
        "similar_cases": [],
    }

    result = analyze_rca_node(state)

    assert "timeout" in result["root_cause"].lower()
```

---

## 12.5 `test_runbook_graph.py`

路径：

```txt
apps/aiops-agent/tests/test_runbook_graph.py
```

```python
from __future__ import annotations

from aiops_agent.workflow.graph.runbook_graph import recommend_runbook_node


def test_recommend_runbook_for_timeout_root_cause():
    state = {
        "root_cause": "Possible timeout or dependency latency issue",
        "enable_runbook_recommendation": True,
    }

    result = recommend_runbook_node(state)

    assert len(result["runbook_candidates"]) >= 1
    assert "latency" in result["runbook_candidates"][0].reason.lower()


def test_runbook_recommendation_can_be_disabled():
    state = {
        "root_cause": "timeout",
        "enable_runbook_recommendation": False,
    }

    result = recommend_runbook_node(state)

    assert result["runbook_candidates"] == []
```

---

## 12.6 `test_safety_graph.py`

路径：

```txt
apps/aiops-agent/tests/test_safety_graph.py
```

```python
from __future__ import annotations

from aiops_agent.workflow.contracts import RunbookCandidate
from aiops_agent.workflow.graph.safety_graph import safety_review_node


def test_safety_blocks_executable_action_candidates():
    state = {
        "severity": "high",
        "confidence": 0.8,
        "runbook_candidates": [
            RunbookCandidate(
                title="Restart service",
                action_type="ssh",
                target_type="service",
                risk_level="high",
                reason="unsafe direct action",
            ),
            RunbookCandidate(
                title="Collect logs",
                action_type="manual",
                target_type="service",
                risk_level="low",
                reason="safe manual action",
            ),
        ],
    }

    result = safety_review_node(state)

    assert len(result["runbook_candidates"]) == 1
    assert result["runbook_candidates"][0].action_type == "manual"
    assert result["risk_level"] == "high"
    assert any("Blocked executable action" in note for note in result["safety_notes"])
```

---

## 12.7 `test_final_report_graph.py`

路径：

```txt
apps/aiops-agent/tests/test_final_report_graph.py
```

```python
from __future__ import annotations

from aiops_agent.workflow.graph.final_report_graph import final_report_node


def test_final_report_node_sets_summary_next_steps_and_metadata():
    state = {
        "title": "Order service timeout",
        "root_cause": "redis timeout",
        "confidence": 0.7,
        "risk_level": "high",
        "metadata": {},
    }

    result = final_report_node(state)

    assert "redis timeout" in result["final_summary"]
    assert "Require human approval" in " ".join(result["next_steps"])
    assert result["metadata"]["graph_version"] == "phase7.0-modular-graph"
    assert "evidence_graph" in result["metadata"]["graph_modules"]
```

---

## 12.8 `test_phase7_orchestrator.py`

路径：

```txt
apps/aiops-agent/tests/test_phase7_orchestrator.py
```

```python
from __future__ import annotations

import pytest

from aiops_agent.workflow.contracts import DiagnosisRequest, EvidenceItem, SimilarCase
from aiops_agent.workflow.graph.context import GraphContext
from aiops_agent.workflow.graph.orchestrator import run_diagnosis_graph
from tests.fakes import FakeEvidenceClient, FakeKnowledgeClient


@pytest.mark.asyncio
async def test_orchestrator_runs_all_graph_modules():
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
    )

    response = await run_diagnosis_graph(
        DiagnosisRequest(
            tenant_id="tenant_1",
            incident_id="inc_1",
            title="Order service timeout",
            severity="high",
            description="Order service returned 5xx",
            alert_summary="Redis timeout",
            tags=["order-service", "redis"],
        ),
        context,
    )

    assert response.incident_id == "inc_1"
    assert response.root_cause == "redis timeout"
    assert response.confidence > 0.4
    assert response.risk_level == "high"
    assert len(response.evidence) == 1
    assert len(response.similar_cases) == 1
    assert response.metadata["graph_version"] == "phase7.0-modular-graph"


@pytest.mark.asyncio
async def test_orchestrator_without_case_retrieval():
    context = GraphContext(
        evidence_client=FakeEvidenceClient(
            [
                EvidenceItem(
                    evidence_id="ev_1",
                    evidence_type="log",
                    title="Timeout",
                    summary="dependency timeout happened",
                    source="test",
                )
            ]
        ),
        knowledge_client=FakeKnowledgeClient(),
    )

    response = await run_diagnosis_graph(
        DiagnosisRequest(
            tenant_id="tenant_1",
            incident_id="inc_1",
            title="Order service timeout",
            severity="medium",
            enable_case_retrieval=False,
        ),
        context,
    )

    assert response.similar_cases == []
    assert "timeout" in response.root_cause.lower()
```

---

# 13. 文档

路径：

```txt
docs/mvp/design/phase7.0-agent-graph-modularization.md
```

```md
# Phase7.0 Agent Graph Modularization

## 目标

将原本单体 Diagnosis Agent 拆成可维护、可测试、可扩展的 Graph 子模块。

## 不做

- 不做多 Agent
- 不做人审 checkpoint
- 不做 Agent Memory
- 不调 Runner
- 不做自动执行
- 不做自动回滚

## 子图

- evidence_graph
- case_retrieval_graph
- rca_graph
- runbook_graph
- safety_graph
- final_report_graph

## 主流程

input
-> evidence_graph
-> case_retrieval_graph
-> rca_graph
-> runbook_graph
-> safety_graph
-> final_report_graph
-> DiagnosisResponse

## 安全边界

Agent 输出仍然只是诊断建议。

不得直接生成 execution。
不得直接调用 runner。
不得直接执行 webhook / ansible / ssh。
不得直接 rollback。

## 验收标准

1. /v1/diagnose contract 不破坏。
2. graph_version = phase7.0-modular-graph。
3. evidence_graph 可独立测试。
4. case_retrieval_graph 可独立测试。
5. rca_graph 可独立测试。
6. runbook_graph 可独立测试。
7. safety_graph 可独立测试。
8. final_report_graph 可独立测试。
9. orchestrator 能串联所有子图。
10. 不引入执行能力。
```

---

# 14. 验证命令

进入 Python Agent 目录：

```bash
cd apps/aiops-agent
```

安装依赖：

```bash
pip install -e ".[test]"
```

运行单元测试：

```bash
pytest -q
```

启动服务：

```bash
uvicorn aiops_agent.main:app --reload --port 8000
```

测试接口：

```bash
curl -X POST http://localhost:8000/v1/diagnose \
  -H "Content-Type: application/json" \
  -d '{
    "tenant_id": "tenant_1",
    "incident_id": "inc_1",
    "title": "Order service redis timeout",
    "severity": "high",
    "description": "Order service returned 5xx due to redis timeout",
    "tags": ["order-service", "redis"]
  }'
```

---

# 15. 验收标准

```txt
1. /health 正常。
2. /v1/contracts/diagnosis 正常。
3. /v1/diagnose contract 不破坏。
4. 返回 metadata.graph_version = phase7.0-modular-graph。
5. evidence_graph 独立测试通过。
6. case_retrieval_graph 独立测试通过。
7. rca_graph 独立测试通过。
8. runbook_graph 独立测试通过。
9. safety_graph 独立测试通过。
10. final_report_graph 独立测试通过。
11. orchestrator 测试通过。
12. Agent 不调 runner。
13. Agent 不创建 execution。
14. Agent 不直接执行 webhook / ansible / ssh。
15. Agent 只输出诊断建议和 runbook candidate。
```

---

# 16. 建议提交信息

```txt
feat(agent): modularize diagnosis graph
```

---

# 17. 下一步 Phase7.1

Phase7.0 完成后进入：

```txt
Phase7.1 Human-in-the-loop Checkpoint
```

Phase7.1 才开始做：

```txt
1. graph checkpoint
2. interrupt before recommendation
3. human review state
4. resume graph
5. approval-like but for agent reasoning, not execution
```

不要把 HITL 混进 Phase7.0。
