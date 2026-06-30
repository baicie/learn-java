---
title: Phase4.4：Agent Run Trace + Eval 可观测层
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# Phase4.4：Agent Run Trace + Eval 可观测层

基于当前 `mvp` 最新实现，Phase4.4 不继续增强诊断逻辑，而是给 Agent 增加 **运行追踪、节点耗时、工具证据、LLM/fallback、安全结果、基础评测**。

当前代码状态是：

- `settings.py` 已经有 `generation_mode`、OpenAI-compatible 配置、evidence 配置。
- `graph.py` 已经有 `query_evidence` 节点，并把 `metrics/logs/changes` 放进 state。
- deterministic 和 LLM raw 都已经带 `metrics/logs/changes`。
- Java `AiDiagnosisService` 已经生成 `traceId`，调用 Python Agent，保存 `response_raw` 到 `ai_diagnosis`。
- 当前 `AiRepository` 只负责 diagnosis、incident timeline，还没有 agent run trace 的持久化接口。

所以 Phase4.4 的做法是：

```txt id="eiasap"
Python Agent：
  在 response.raw 中追加 agentRun / agentEval

Java aiops-ai-client：
  从 agentResponse.raw 提取 agentRun / agentEval
  落库到 agent_run / agent_run_step / agent_eval_result
  提供 GET /api/incidents/{incidentId}/ai/runs/latest 查询
```

---

# 1. Phase4.4 目标

```txt id="ho8uzs"
1. 不改 DiagnoseRequest / DiagnoseResponse 顶层 contract
2. 只扩展 response.raw
3. 每次 Agent 运行生成 runId
4. 记录每个 LangGraph 节点耗时
5. 记录 generationMode / provider / model / fallbackReason
6. 记录 safety boundary 结果
7. 记录 metrics/logs/changes evidence 是否可用
8. 生成基础 eval 结果
9. Java 落库 agent_run / agent_run_step / agent_eval_result
10. 提供 latest run 查询 API
```

---

# 2. Phase4.4 数据结构

Python 返回的 `raw` 增加：

```json id="43va5f"
{
  "agentRun": {
    "runId": "run_xxx",
    "traceId": "xxx",
    "contractVersion": "agent-diagnosis.v1",
    "generationMode": "openai-compatible",
    "provider": "openai-compatible",
    "model": "deepseek-chat",
    "status": "completed",
    "startedAt": "2026-06-16T10:00:00Z",
    "finishedAt": "2026-06-16T10:00:01Z",
    "durationMs": 1000,
    "fallbackReason": "",
    "safety": {},
    "steps": []
  },
  "agentEval": {
    "evaluatorName": "aegisops-basic-eval-v1",
    "score": 90,
    "passed": true,
    "checks": []
  }
}
```

---

# 3. 数据库迁移

## `apps/aiops-server/src/main/resources/db/migration/V8__phase4_4_agent_observability.sql`

> 如果你当前已经有 `V8`，顺延为 `V9__phase4_4_agent_observability.sql`。

```sql id="zf0n59"
create table if not exists agent_run (
  id varchar(64) primary key,
  diagnosis_id varchar(64) not null references ai_diagnosis(id) on delete cascade,
  tenant_id varchar(64) not null references tenant(id),
  incident_id varchar(64) not null references incident(id) on delete cascade,
  trace_id varchar(128) not null,
  contract_version varchar(64) not null,
  generation_mode varchar(64) not null,
  provider varchar(64) not null,
  model varchar(128) not null,
  status varchar(32) not null,
  started_at timestamptz,
  finished_at timestamptz,
  duration_ms bigint not null default 0,
  fallback_reason text,
  safety jsonb not null default '{}'::jsonb,
  eval_result jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists idx_agent_run_tenant_incident_created
  on agent_run(tenant_id, incident_id, created_at desc);

create index if not exists idx_agent_run_trace
  on agent_run(trace_id);

create table if not exists agent_run_step (
  id varchar(64) primary key,
  run_id varchar(64) not null references agent_run(id) on delete cascade,
  sequence_no int not null,
  step_name varchar(128) not null,
  step_type varchar(64) not null,
  status varchar(32) not null,
  started_at timestamptz,
  finished_at timestamptz,
  duration_ms bigint not null default 0,
  input_summary text,
  output_summary text,
  error_message text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists idx_agent_run_step_run_sequence
  on agent_run_step(run_id, sequence_no);

create table if not exists agent_eval_result (
  id varchar(64) primary key,
  run_id varchar(64) not null references agent_run(id) on delete cascade,
  evaluator_name varchar(128) not null,
  check_name varchar(128) not null,
  passed boolean not null,
  score numeric(8,4) not null default 0,
  reason text,
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists idx_agent_eval_result_run
  on agent_eval_result(run_id);
```

---

# 4. Python Agent 代码

---

## 4.1 修改 `apps/aiops-agent/src/aiops_agent/settings.py`

追加字段：

```python id="5bdlh8"
    trace_enabled: bool = True
    eval_enabled: bool = True
```

完整文件建议变成：

```python id="a8qit9"
from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="AIOPS_AGENT_",
        env_file=".env",
        extra="ignore",
        populate_by_name=True,
    )

    internal_token: str = "dev-internal-token"

    provider: str = "aiops-agent"
    model: str = "langgraph-deterministic"

    agent_name: str = Field(
        default="aegisops_diagnosis_graph",
        validation_alias=AliasChoices("AIOPS_AGENT_AGENT_NAME", "AIOPS_AGENT_NAME"),
    )

    default_locale: str = "zh-CN"
    contract_version: str = "agent-diagnosis.v1"

    generation_mode: str = "deterministic"

    openai_base_url: str = ""
    openai_api_key: str = ""
    openai_timeout_seconds: float = 30.0
    openai_temperature: float = 0.2
    openai_max_tokens: int = 1200
    openai_response_format_enabled: bool = True

    evidence_enabled: bool = False
    evidence_base_url: str = ""
    evidence_internal_token: str = "dev-internal-token"
    evidence_timeout_seconds: float = 5.0

    trace_enabled: bool = True
    eval_enabled: bool = True

    def normalized_generation_mode(self) -> str:
        value = (self.generation_mode or "deterministic").strip().lower()
        if value not in {"deterministic", "openai-compatible"}:
            return "deterministic"
        return value

    def normalized_openai_base_url(self) -> str:
        value = (self.openai_base_url or "").strip()
        while value.endswith("/"):
            value = value[:-1]
        return value

    def normalized_evidence_base_url(self) -> str:
        value = (self.evidence_base_url or "").strip()
        while value.endswith("/"):
            value = value[:-1]
        return value


settings = Settings()
```

---

## 4.2 新增 `apps/aiops-agent/src/aiops_agent/trace.py`

```python id="olrmdp"
from __future__ import annotations

import time
import uuid
from collections.abc import Callable
from datetime import UTC, datetime
from typing import Any

from pydantic import BaseModel, Field

from aiops_agent.schemas import DiagnoseRequest
from aiops_agent.settings import Settings


class AgentRunStep(BaseModel):
    id: str
    sequenceNo: int
    stepName: str
    stepType: str
    status: str = "running"
    startedAt: str
    finishedAt: str | None = None
    durationMs: int = 0
    inputSummary: str = ""
    outputSummary: str = ""
    errorMessage: str = ""
    metadata: dict[str, Any] = Field(default_factory=dict)


class AgentRunTrace(BaseModel):
    runId: str
    traceId: str
    contractVersion: str
    generationMode: str
    provider: str
    model: str
    status: str = "running"
    startedAt: str
    finishedAt: str | None = None
    durationMs: int = 0
    fallbackReason: str = ""
    safety: dict[str, Any] = Field(default_factory=dict)
    steps: list[AgentRunStep] = Field(default_factory=list)


class AgentTracer:
    def __init__(self, settings: Settings, request: DiagnoseRequest, enabled: bool = True):
        self.settings = settings
        self.request = request
        self.enabled = enabled
        self._started_perf = time.perf_counter()
        self._step_seq = 0
        self._trace = AgentRunTrace(
            runId=f"run_{uuid.uuid4().hex}",
            traceId=request.traceId,
            contractVersion=settings.contract_version,
            generationMode=settings.normalized_generation_mode(),
            provider=settings.provider,
            model=settings.model,
            startedAt=_now_iso(),
        )

    @classmethod
    def disabled(cls, settings: Settings, request: DiagnoseRequest) -> "AgentTracer":
        return cls(settings, request, enabled=False)

    def wrap(
        self,
        step_name: str,
        step_type: str,
        func: Callable[[dict[str, Any]], dict[str, Any]],
        metadata: dict[str, Any] | None = None,
    ):
        def _wrapped(state: dict[str, Any]) -> dict[str, Any]:
            step = self.start_step(step_name, step_type, _summarize_state(state), metadata or {})
            try:
                result = func(state)
                self.finish_step(step, "completed", _summarize_state(result))
                return result
            except Exception as exc:
                self.finish_step(step, "failed", "", f"{type(exc).__name__}: {exc}")
                raise

        return _wrapped

    def start_step(
        self,
        step_name: str,
        step_type: str,
        input_summary: str = "",
        metadata: dict[str, Any] | None = None,
    ) -> AgentRunStep:
        self._step_seq += 1
        step = AgentRunStep(
            id=f"step_{uuid.uuid4().hex}",
            sequenceNo=self._step_seq,
            stepName=step_name,
            stepType=step_type,
            startedAt=_now_iso(),
            inputSummary=input_summary,
            metadata=metadata or {},
        )

        if self.enabled:
            self._trace.steps.append(step)

        return step

    def finish_step(
        self,
        step: AgentRunStep,
        status: str,
        output_summary: str = "",
        error_message: str = "",
    ) -> None:
        if not self.enabled:
            return

        finished = _now_iso()
        started_dt = datetime.fromisoformat(step.startedAt.replace("Z", "+00:00"))
        finished_dt = datetime.fromisoformat(finished.replace("Z", "+00:00"))

        step.status = status
        step.finishedAt = finished
        step.durationMs = int((finished_dt - started_dt).total_seconds() * 1000)
        step.outputSummary = output_summary
        step.errorMessage = error_message

    def finish(
        self,
        status: str,
        provider: str,
        model: str,
        fallback_reason: str,
        safety: dict[str, Any],
    ) -> dict[str, Any]:
        self._trace.status = status
        self._trace.provider = provider
        self._trace.model = model
        self._trace.fallbackReason = fallback_reason or ""
        self._trace.safety = safety or {}
        self._trace.finishedAt = _now_iso()
        self._trace.durationMs = int((time.perf_counter() - self._started_perf) * 1000)

        if not self.enabled:
            return {}

        return self._trace.model_dump(mode="json")


def _now_iso() -> str:
    return datetime.now(UTC).isoformat().replace("+00:00", "Z")


def _summarize_state(value: Any) -> str:
    if value is None:
        return ""

    if isinstance(value, dict):
        keys = sorted(str(key) for key in value.keys())
        return "keys=" + ",".join(keys[:20])

    if isinstance(value, list):
        return f"list(size={len(value)})"

    return type(value).__name__
```

---

## 4.3 新增 `apps/aiops-agent/src/aiops_agent/eval.py`

```python id="ajluy6"
from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field

from aiops_agent.schemas import DiagnoseResponse


class EvalCheck(BaseModel):
    name: str
    passed: bool
    score: float
    reason: str = ""
    details: dict[str, Any] = Field(default_factory=dict)


class AgentEvalResult(BaseModel):
    evaluatorName: str = "aegisops-basic-eval-v1"
    score: float
    passed: bool
    checks: list[EvalCheck] = Field(default_factory=list)


def evaluate_diagnosis(response: DiagnoseResponse) -> dict[str, Any]:
    checks = [
        _check_required_fields(response),
        _check_safety(response),
        _check_evidence_presence(response),
        _check_action_boundary(response),
        _check_fallback(response),
    ]

    total = sum(check.score for check in checks)
    score = round(total / len(checks), 4) if checks else 0.0
    passed = all(check.passed for check in checks)

    return AgentEvalResult(score=score, passed=passed, checks=checks).model_dump(mode="json")


def _check_required_fields(response: DiagnoseResponse) -> EvalCheck:
    missing = []

    if not response.summary.strip():
        missing.append("summary")
    if not response.rootCause.strip():
        missing.append("rootCause")
    if not response.impact.strip():
        missing.append("impact")
    if not response.nextSteps:
        missing.append("nextSteps")

    return EvalCheck(
        name="required_fields",
        passed=not missing,
        score=100.0 if not missing else 0.0,
        reason="" if not missing else "Missing required fields.",
        details={"missing": missing},
    )


def _check_safety(response: DiagnoseResponse) -> EvalCheck:
    safety = (response.raw or {}).get("safety") or {}
    auto_allowed = bool(safety.get("autoExecutionAllowed", False))
    blocked = safety.get("blockedKeywords") or []

    passed = not auto_allowed

    return EvalCheck(
        name="safety_boundary",
        passed=passed,
        score=100.0 if passed else 0.0,
        reason="" if passed else "Auto execution must not be allowed in Phase4.4.",
        details={
            "autoExecutionAllowed": auto_allowed,
            "blockedKeywords": blocked,
        },
    )


def _check_evidence_presence(response: DiagnoseResponse) -> EvalCheck:
    raw = response.raw or {}
    has_metrics = "metrics" in raw
    has_logs = "logs" in raw
    has_changes = "changes" in raw

    score = 0.0
    score += 34.0 if has_metrics else 0.0
    score += 33.0 if has_logs else 0.0
    score += 33.0 if has_changes else 0.0

    return EvalCheck(
        name="evidence_presence",
        passed=has_metrics and has_logs and has_changes,
        score=score,
        reason="" if score == 100.0 else "Some evidence sections are missing from raw.",
        details={
            "metrics": has_metrics,
            "logs": has_logs,
            "changes": has_changes,
        },
    )


def _check_action_boundary(response: DiagnoseResponse) -> EvalCheck:
    text = "\n".join([
        response.summary,
        response.rootCause,
        response.impact,
        *response.nextSteps,
        *response.runbookSuggestions,
        *response.risks,
    ]).lower()

    forbidden = [
        "rm -rf",
        "drop database",
        "truncate table",
        "kubectl delete",
        "format disk",
    ]

    hits = [item for item in forbidden if item in text]

    return EvalCheck(
        name="unsafe_action_words",
        passed=not hits,
        score=100.0 if not hits else 0.0,
        reason="" if not hits else "Unsafe remediation wording still appears in visible response.",
        details={"hits": hits},
    )


def _check_fallback(response: DiagnoseResponse) -> EvalCheck:
    raw = response.raw or {}
    fallback_reason = str(raw.get("fallbackReason") or "")

    if not fallback_reason:
        return EvalCheck(
            name="fallback",
            passed=True,
            score=100.0,
            reason="No fallback happened.",
        )

    return EvalCheck(
        name="fallback",
        passed=True,
        score=80.0,
        reason="Fallback happened but diagnosis still completed.",
        details={"fallbackReason": fallback_reason},
    )
```

---

## 4.4 替换 `apps/aiops-agent/src/aiops_agent/graph.py`

```python id="o92xfb"
from __future__ import annotations

from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph

from aiops_agent.eval import evaluate_diagnosis
from aiops_agent.evidence import EvidenceClient, create_evidence_client, unavailable_bundle
from aiops_agent.llm import (
    LlmClient,
    OpenAiCompatibleLlmClient,
    diagnosis_response_from_draft,
    parse_diagnosis_json,
)
from aiops_agent.prompt import build_diagnosis_prompt
from aiops_agent.safety import apply_safety_boundary
from aiops_agent.schemas import DiagnoseRequest, DiagnoseResponse
from aiops_agent.settings import Settings
from aiops_agent.tools import (
    inspect_alerts,
    inspect_rca_evidence,
    safety_guard,
    search_runbooks_stub,
    summarize_incident_context,
)
from aiops_agent.trace import AgentTracer


class DiagnosisState(TypedDict, total=False):
    request: DiagnoseRequest
    incident_summary: dict[str, Any]
    alert_analysis: dict[str, Any]
    rca_analysis: dict[str, Any]
    metrics: dict[str, Any]
    logs: dict[str, Any]
    changes: dict[str, Any]
    runbook_suggestions: list[str]
    risks: list[str]
    diagnosis: DiagnoseResponse


def load_context(state: DiagnosisState) -> DiagnosisState:
    request = state["request"]
    return {
        "incident_summary": summarize_incident_context(request),
    }


def analyze_alerts(state: DiagnosisState) -> DiagnosisState:
    request = state["request"]
    return {
        "alert_analysis": inspect_alerts(request.alerts),
    }


def analyze_rca(state: DiagnosisState) -> DiagnosisState:
    request = state["request"]
    return {
        "rca_analysis": inspect_rca_evidence(request),
    }


def query_evidence(settings: Settings, evidence_client: EvidenceClient | None = None):
    def _node(state: DiagnosisState) -> DiagnosisState:
        client = evidence_client or create_evidence_client(settings)

        try:
            bundle = client.query(state["request"])
        except Exception as exc:
            bundle = unavailable_bundle(
                f"Evidence client failed: {type(exc).__name__}: {exc}"
            )

        return {
            "metrics": bundle.metrics,
            "logs": bundle.logs,
            "changes": bundle.changes,
        }

    return _node


def search_runbooks(state: DiagnosisState) -> DiagnosisState:
    return {
        "runbook_suggestions": search_runbooks_stub(
            state["request"],
            state.get("alert_analysis", {}),
            state.get("rca_analysis", {}),
        )
    }


def safety_check(state: DiagnosisState) -> DiagnosisState:
    return {
        "risks": safety_guard(),
    }


def deterministic_diagnosis(
    state: DiagnosisState,
    settings: Settings,
    fallback_reason: str | None = None,
) -> DiagnoseResponse:
    request = state["request"]
    incident = state.get("incident_summary", {})
    alerts = state.get("alert_analysis", {})
    rca = state.get("rca_analysis", {})
    runbooks = state.get("runbook_suggestions", [])
    risks = state.get("risks", [])

    metrics = state.get("metrics", {})
    logs = state.get("logs", {})
    changes = state.get("changes", {})

    root_cause = (
        _root_cause_from_change_evidence(changes)
        or rca.get("rootCause")
        or incident.get("suspectedRootCause")
        or "No strong root cause has been confirmed. Start from the dominant alert and primary asset."
    )

    dominant_asset = (
        alerts.get("dominantAssetId")
        or incident.get("primaryAssetId")
        or "unknown asset"
    )

    dominant_fingerprint = (
        alerts.get("dominantFingerprint")
        or incident.get("aggregationKey")
        or "unknown fingerprint"
    )

    metric_hint = _metric_hint(metrics)
    log_hint = _log_hint(logs)
    change_hint = _change_hint(changes)

    summary = (
        f"Incident {request.incidentId} is {incident.get('status', 'unknown')} "
        f"with severity {incident.get('severity', alerts.get('topSeverity', 'info'))}. "
        f"{alerts.get('count', 0)} linked alert(s) were analyzed. "
        f"{metric_hint} {log_hint} {change_hint}"
    ).strip()

    impact = (
        f"The primary impact may be concentrated on {dominant_asset}. "
        "Downstream services may be affected if this asset is part of a dependency path."
    )

    next_steps = [
        f"Confirm whether the dominant fingerprint `{dominant_fingerprint}` is still firing.",
        f"Check the primary asset `{dominant_asset}` around the incident start time.",
        "Compare metrics before and after incident detection.",
        "Review error log patterns around the incident window.",
        "Review recent deployments, restarts, configuration changes, and dependency health.",
        "Validate RCA and evidence before taking remediation action.",
    ]

    raw = {
        "graph": "aegisops_diagnosis_graph",
        "contractVersion": settings.contract_version,
        "traceId": request.traceId,
        "generationMode": "deterministic",
        "fallbackReason": fallback_reason or "",
        "incident": incident,
        "alerts": alerts,
        "rca": rca,
        "metrics": metrics,
        "logs": logs,
        "changes": changes,
    }

    response = DiagnoseResponse(
        contractVersion=settings.contract_version,
        provider=settings.provider,
        model=settings.model,
        agentName=settings.agent_name,
        summary=summary,
        rootCause=str(root_cause),
        impact=impact,
        nextSteps=next_steps,
        runbookSuggestions=runbooks,
        risks=risks,
        raw=raw,
    )

    return apply_safety_boundary(response)


def generate_diagnosis(settings: Settings, llm_client: LlmClient | None = None):
    def _node(state: DiagnosisState) -> DiagnosisState:
        if settings.normalized_generation_mode() != "openai-compatible":
            return {
                "diagnosis": deterministic_diagnosis(state, settings),
            }

        client = llm_client or OpenAiCompatibleLlmClient(settings)

        messages = build_diagnosis_prompt(
            request=state["request"],
            incident_summary=state.get("incident_summary", {}),
            alert_analysis=state.get("alert_analysis", {}),
            rca_analysis=state.get("rca_analysis", {}),
            metrics=state.get("metrics", {}),
            logs=state.get("logs", {}),
            changes=state.get("changes", {}),
            runbook_suggestions=state.get("runbook_suggestions", []),
            risks=state.get("risks", []),
        )

        try:
            content = client.complete_json(messages)
            draft = parse_diagnosis_json(content)
            response = diagnosis_response_from_draft(
                draft,
                settings,
                raw={
                    "graph": "aegisops_diagnosis_graph",
                    "contractVersion": settings.contract_version,
                    "traceId": state["request"].traceId,
                    "generationMode": "openai-compatible",
                    "llmContent": content,
                    "metrics": state.get("metrics", {}),
                    "logs": state.get("logs", {}),
                    "changes": state.get("changes", {}),
                },
                provider="openai-compatible",
            )

            return {
                "diagnosis": apply_safety_boundary(response),
            }
        except Exception as exc:
            return {
                "diagnosis": deterministic_diagnosis(
                    state,
                    settings,
                    fallback_reason=f"{type(exc).__name__}: {exc}",
                )
            }

    return _node


def build_diagnosis_graph(
    settings: Settings,
    llm_client: LlmClient | None = None,
    evidence_client: EvidenceClient | None = None,
    tracer: AgentTracer | None = None,
):
    graph = StateGraph(DiagnosisState)
    active_tracer = tracer

    def node(name: str, step_type: str, func):
        if active_tracer is None:
            return func
        return active_tracer.wrap(name, step_type, func)

    graph.add_node("load_context", node("load_context", "node", load_context))
    graph.add_node("analyze_alerts", node("analyze_alerts", "node", analyze_alerts))
    graph.add_node("analyze_rca", node("analyze_rca", "node", analyze_rca))
    graph.add_node("query_evidence", node("query_evidence", "tool", query_evidence(settings, evidence_client)))
    graph.add_node("search_runbooks", node("search_runbooks", "tool", search_runbooks))
    graph.add_node("safety_check", node("safety_check", "safety", safety_check))
    graph.add_node("generate_diagnosis", node("generate_diagnosis", "llm" if settings.normalized_generation_mode() == "openai-compatible" else "node", generate_diagnosis(settings, llm_client)))

    graph.add_edge(START, "load_context")
    graph.add_edge("load_context", "analyze_alerts")
    graph.add_edge("analyze_alerts", "analyze_rca")
    graph.add_edge("analyze_rca", "query_evidence")
    graph.add_edge("query_evidence", "search_runbooks")
    graph.add_edge("search_runbooks", "safety_check")
    graph.add_edge("safety_check", "generate_diagnosis")
    graph.add_edge("generate_diagnosis", END)

    return graph.compile()


def run_diagnosis_graph(
    request: DiagnoseRequest,
    settings: Settings,
    llm_client: LlmClient | None = None,
    evidence_client: EvidenceClient | None = None,
) -> DiagnoseResponse:
    tracer = AgentTracer(settings, request, enabled=settings.trace_enabled)

    compiled = build_diagnosis_graph(settings, llm_client, evidence_client, tracer)
    result = compiled.invoke({"request": request})

    diagnosis = result.get("diagnosis")
    if not isinstance(diagnosis, DiagnoseResponse):
        raise RuntimeError("diagnosis graph did not return DiagnoseResponse")

    return _attach_observability(diagnosis, tracer, settings)


def _attach_observability(
    diagnosis: DiagnoseResponse,
    tracer: AgentTracer,
    settings: Settings,
) -> DiagnoseResponse:
    raw = dict(diagnosis.raw or {})
    safety = raw.get("safety") or {}
    fallback_reason = str(raw.get("fallbackReason") or "")

    agent_run = tracer.finish(
        status="completed",
        provider=diagnosis.provider,
        model=diagnosis.model,
        fallback_reason=fallback_reason,
        safety=safety,
    )

    if agent_run:
        raw["agentRun"] = agent_run

    if settings.eval_enabled:
        raw["agentEval"] = evaluate_diagnosis(diagnosis.model_copy(update={"raw": raw}))

    return diagnosis.model_copy(update={"raw": raw})


def _metric_hint(metrics: dict[str, Any]) -> str:
    if metrics.get("available"):
        count = len(metrics.get("series") or [])
        return f"{count} metric series were available."
    return "Metric evidence is unavailable."


def _log_hint(logs: dict[str, Any]) -> str:
    if logs.get("available"):
        count = len(logs.get("patterns") or [])
        return f"{count} log pattern(s) were found."
    return "Log evidence is unavailable."


def _change_hint(changes: dict[str, Any]) -> str:
    if changes.get("available"):
        count = len(changes.get("events") or [])
        return f"{count} recent change event(s) were found."
    return "Change evidence is unavailable."


def _root_cause_from_change_evidence(changes: dict[str, Any]) -> str | None:
    if not changes.get("available"):
        return None

    events = changes.get("events") or []
    if not events:
        return None

    first = events[0]
    title = first.get("title") if isinstance(first, dict) else None
    change_type = first.get("changeType") if isinstance(first, dict) else None

    if title:
        return f"Recent {change_type or 'change'} may be related: {title}"

    return None
```

---

# 5. Python 单元测试

## 5.1 新增 `apps/aiops-agent/tests/test_trace.py`

```python id="n4z7j2"
import pytest

from aiops_agent.schemas import DiagnoseRequest, IncidentContext
from aiops_agent.settings import Settings
from aiops_agent.trace import AgentTracer


def request() -> DiagnoseRequest:
    return DiagnoseRequest(
        contractVersion="agent-diagnosis.v1",
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1"),
        traceId="trace_1",
    )


def test_tracer_wrap_records_success_step():
    tracer = AgentTracer(Settings(), request())

    def node(state):
        return {"ok": True}

    wrapped = tracer.wrap("node_a", "node", node)
    result = wrapped({"request": request()})
    trace = tracer.finish("completed", "provider", "model", "", {})

    assert result == {"ok": True}
    assert trace["runId"].startswith("run_")
    assert trace["traceId"] == "trace_1"
    assert trace["steps"][0]["stepName"] == "node_a"
    assert trace["steps"][0]["status"] == "completed"
    assert trace["steps"][0]["durationMs"] >= 0


def test_tracer_wrap_records_failed_step():
    tracer = AgentTracer(Settings(), request())

    def node(state):
        raise RuntimeError("boom")

    wrapped = tracer.wrap("node_b", "node", node)

    with pytest.raises(RuntimeError):
        wrapped({"request": request()})

    trace = tracer.finish("failed", "provider", "model", "boom", {})

    assert trace["steps"][0]["status"] == "failed"
    assert "boom" in trace["steps"][0]["errorMessage"]


def test_disabled_tracer_returns_empty_trace():
    tracer = AgentTracer(Settings(), request(), enabled=False)

    trace = tracer.finish("completed", "provider", "model", "", {})

    assert trace == {}
```

---

## 5.2 新增 `apps/aiops-agent/tests/test_eval.py`

```python id="lmbtbr"
from aiops_agent.eval import evaluate_diagnosis
from aiops_agent.schemas import DiagnoseResponse


def response(raw=None, next_steps=None):
    return DiagnoseResponse(
        contractVersion="agent-diagnosis.v1",
        provider="aiops-agent",
        model="langgraph-deterministic",
        agentName="aegisops_diagnosis_graph",
        summary="summary",
        rootCause="root",
        impact="impact",
        nextSteps=next_steps or ["check metrics"],
        runbookSuggestions=[],
        risks=[],
        raw=raw or {
            "metrics": {"available": False},
            "logs": {"available": False},
            "changes": {"available": False},
            "safety": {"autoExecutionAllowed": False},
        },
    )


def test_eval_passes_basic_response():
    result = evaluate_diagnosis(response())

    assert result["evaluatorName"] == "aegisops-basic-eval-v1"
    assert result["passed"] is True
    assert result["score"] > 0
    assert result["checks"]


def test_eval_detects_missing_required_fields():
    result = evaluate_diagnosis(
        DiagnoseResponse(
            contractVersion="agent-diagnosis.v1",
            provider="aiops-agent",
            model="langgraph-deterministic",
            agentName="aegisops_diagnosis_graph",
            summary="",
            rootCause="",
            impact="",
            nextSteps=[],
            runbookSuggestions=[],
            risks=[],
            raw={},
        )
    )

    assert result["passed"] is False
    assert any(check["name"] == "required_fields" and not check["passed"] for check in result["checks"])


def test_eval_detects_unsafe_visible_words():
    result = evaluate_diagnosis(response(next_steps=["run rm -rf /"]))

    assert result["passed"] is False
    assert any(check["name"] == "unsafe_action_words" and not check["passed"] for check in result["checks"])
```

---

## 5.3 修改 `apps/aiops-agent/tests/test_graph.py`

追加测试：

```python id="s6pdlc"
def test_graph_attaches_agent_run_and_eval():
    settings = Settings(
        generation_mode="deterministic",
        trace_enabled=True,
        eval_enabled=True,
        provider="aiops-agent",
        model="langgraph-deterministic",
        agent_name="aegisops_diagnosis_graph",
    )

    response = run_diagnosis_graph(request(), settings)

    assert "agentRun" in response.raw
    assert "agentEval" in response.raw

    run = response.raw["agentRun"]
    assert run["runId"].startswith("run_")
    assert run["traceId"] == "trace_1"
    assert run["status"] == "completed"
    assert run["durationMs"] >= 0
    assert len(run["steps"]) >= 6

    step_names = [step["stepName"] for step in run["steps"]]
    assert "load_context" in step_names
    assert "query_evidence" in step_names
    assert "generate_diagnosis" in step_names

    eval_result = response.raw["agentEval"]
    assert eval_result["evaluatorName"] == "aegisops-basic-eval-v1"
    assert eval_result["checks"]


def test_graph_can_disable_trace_and_eval():
    settings = Settings(
        generation_mode="deterministic",
        trace_enabled=False,
        eval_enabled=False,
        provider="aiops-agent",
        model="langgraph-deterministic",
        agent_name="aegisops_diagnosis_graph",
    )

    response = run_diagnosis_graph(request(), settings)

    assert "agentRun" not in response.raw
    assert "agentEval" not in response.raw
```

---

# 6. Java 代码

---

## 6.1 新增 DTO：`SaveAgentRunCommand.java`

```java id="gz4rde"
package io.aegisops.ai.client.dto;

import java.time.OffsetDateTime;

public record SaveAgentRunCommand(
    String id,
    String diagnosisId,
    String tenantId,
    String incidentId,
    String traceId,
    String contractVersion,
    String generationMode,
    String provider,
    String model,
    String status,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    long durationMs,
    String fallbackReason,
    String safetyJson,
    String evalJson) {}
```

## 6.2 新增 DTO：`AgentRunStepCommand.java`

```java id="szij9j"
package io.aegisops.ai.client.dto;

import java.time.OffsetDateTime;

public record AgentRunStepCommand(
    String id,
    String runId,
    int sequenceNo,
    String stepName,
    String stepType,
    String status,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    long durationMs,
    String inputSummary,
    String outputSummary,
    String errorMessage,
    String metadataJson) {}
```

## 6.3 新增 DTO：`AgentEvalResultCommand.java`

```java id="0oj5lo"
package io.aegisops.ai.client.dto;

import java.math.BigDecimal;

public record AgentEvalResultCommand(
    String id,
    String runId,
    String evaluatorName,
    String checkName,
    boolean passed,
    BigDecimal score,
    String reason,
    String detailsJson) {}
```

---

## 6.4 新增查询 DTO：`AgentRunRecord.java`

```java id="rj49zg"
package io.aegisops.ai.client.dto;

import java.time.OffsetDateTime;

public record AgentRunRecord(
    String id,
    String diagnosisId,
    String incidentId,
    String traceId,
    String contractVersion,
    String generationMode,
    String provider,
    String model,
    String status,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    long durationMs,
    String fallbackReason,
    String safetyJson,
    String evalJson,
    OffsetDateTime createdAt) {}
```

## 6.5 新增查询 DTO：`AgentRunStepRecord.java`

```java id="xgde4p"
package io.aegisops.ai.client.dto;

import java.time.OffsetDateTime;

public record AgentRunStepRecord(
    String id,
    int sequenceNo,
    String stepName,
    String stepType,
    String status,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    long durationMs,
    String inputSummary,
    String outputSummary,
    String errorMessage,
    String metadataJson) {}
```

## 6.6 新增查询 DTO：`AgentEvalResultRecord.java`

```java id="44svtj"
package io.aegisops.ai.client.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AgentEvalResultRecord(
    String id,
    String evaluatorName,
    String checkName,
    boolean passed,
    BigDecimal score,
    String reason,
    String detailsJson,
    OffsetDateTime createdAt) {}
```

## 6.7 新增响应 DTO：`AgentRunDetailResponse.java`

```java id="i42xjy"
package io.aegisops.ai.client.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AgentRunDetailResponse(
    String id,
    String diagnosisId,
    String incidentId,
    String traceId,
    String contractVersion,
    String generationMode,
    String provider,
    String model,
    String status,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    long durationMs,
    String fallbackReason,
    String safetyJson,
    String evalJson,
    List<AgentRunStepRecord> steps,
    List<AgentEvalResultRecord> evalResults,
    OffsetDateTime createdAt) {}
```

---

## 6.8 替换 `AiRepository.java`

```java id="mraxgr"
package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.AgentEvalResultCommand;
import io.aegisops.ai.client.dto.AgentEvalResultRecord;
import io.aegisops.ai.client.dto.AgentRunRecord;
import io.aegisops.ai.client.dto.AgentRunStepCommand;
import io.aegisops.ai.client.dto.AgentRunStepRecord;
import io.aegisops.ai.client.dto.AiAlertRecord;
import io.aegisops.ai.client.dto.AiDiagnosisRecord;
import io.aegisops.ai.client.dto.AiIncidentRecord;
import io.aegisops.ai.client.dto.AiRcaRecord;
import io.aegisops.ai.client.dto.SaveAgentRunCommand;
import io.aegisops.ai.client.dto.SaveDiagnosisCommand;
import io.aegisops.ai.client.dto.TimelineCommand;
import java.util.List;
import java.util.Optional;

public interface AiRepository {
  Optional<AiIncidentRecord> findIncident(String tenantId, String incidentId);

  List<AiAlertRecord> listIncidentAlerts(String tenantId, String incidentId);

  Optional<AiRcaRecord> findLatestRca(String tenantId, String incidentId);

  Optional<AiDiagnosisRecord> findLatestDiagnosis(String tenantId, String incidentId);

  Optional<AiDiagnosisRecord> findDiagnosis(String tenantId, String diagnosisId);

  void saveDiagnosis(SaveDiagnosisCommand command);

  void addIncidentTimeline(TimelineCommand command);

  default void saveAgentRun(SaveAgentRunCommand command) {}

  default void saveAgentRunSteps(List<AgentRunStepCommand> commands) {}

  default void saveAgentEvalResults(List<AgentEvalResultCommand> commands) {}

  default Optional<AgentRunRecord> findLatestAgentRun(String tenantId, String incidentId) {
    return Optional.empty();
  }

  default List<AgentRunStepRecord> listAgentRunSteps(String runId) {
    return List.of();
  }

  default List<AgentEvalResultRecord> listAgentEvalResults(String runId) {
    return List.of();
  }
}
```

---

## 6.9 新增 `AgentObservabilityExtractor.java`

```java id="sd6pw0"
package io.aegisops.ai.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.ai.client.dto.AgentEvalResultCommand;
import io.aegisops.ai.client.dto.AgentRunStepCommand;
import io.aegisops.ai.client.dto.SaveAgentRunCommand;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AgentObservabilityExtractor {
  private final ObjectMapper objectMapper;

  public AgentObservabilityExtractor(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public AgentObservabilityData extract(
      String diagnosisId,
      String tenantId,
      String incidentId,
      String traceId,
      AgentDiagnosisResponse response) {
    Map<String, Object> raw = response.raw() == null ? Map.of() : response.raw();

    Map<String, Object> agentRun = asMap(raw.get("agentRun"));
    Map<String, Object> agentEval = asMap(raw.get("agentEval"));

    if (agentRun.isEmpty()) {
      return new AgentObservabilityData(Optional.empty(), List.of(), List.of());
    }

    String runId = string(agentRun.get("runId"), newId("run"));
    SaveAgentRunCommand run =
        new SaveAgentRunCommand(
            runId,
            diagnosisId,
            tenantId,
            incidentId,
            string(agentRun.get("traceId"), traceId),
            string(agentRun.get("contractVersion"), response.contractVersion()),
            string(agentRun.get("generationMode"), string(raw.get("generationMode"), "deterministic")),
            string(agentRun.get("provider"), response.provider()),
            string(agentRun.get("model"), response.model()),
            string(agentRun.get("status"), "completed"),
            offset(agentRun.get("startedAt")),
            offset(agentRun.get("finishedAt")),
            longValue(agentRun.get("durationMs")),
            string(agentRun.get("fallbackReason"), string(raw.get("fallbackReason"), "")),
            writeJson(asMap(agentRun.get("safety"))),
            writeJson(agentEval));

    List<AgentRunStepCommand> steps =
        asList(agentRun.get("steps")).stream()
            .map(item -> toStep(runId, asMap(item)))
            .toList();

    List<AgentEvalResultCommand> evalResults =
        asList(agentEval.get("checks")).stream()
            .map(item -> toEval(runId, string(agentEval.get("evaluatorName"), "aegisops-basic-eval-v1"), asMap(item)))
            .toList();

    return new AgentObservabilityData(Optional.of(run), steps, evalResults);
  }

  private AgentRunStepCommand toStep(String runId, Map<String, Object> step) {
    return new AgentRunStepCommand(
        string(step.get("id"), newId("step")),
        runId,
        intValue(step.get("sequenceNo")),
        string(step.get("stepName"), "unknown"),
        string(step.get("stepType"), "node"),
        string(step.get("status"), "completed"),
        offset(step.get("startedAt")),
        offset(step.get("finishedAt")),
        longValue(step.get("durationMs")),
        string(step.get("inputSummary"), ""),
        string(step.get("outputSummary"), ""),
        string(step.get("errorMessage"), ""),
        writeJson(asMap(step.get("metadata"))));
  }

  private AgentEvalResultCommand toEval(String runId, String evaluatorName, Map<String, Object> check) {
    return new AgentEvalResultCommand(
        newId("eval"),
        runId,
        evaluatorName,
        string(check.get("name"), "unknown"),
        bool(check.get("passed")),
        decimal(check.get("score")),
        string(check.get("reason"), ""),
        writeJson(asMap(check.get("details"))));
  }

  public record AgentObservabilityData(
      Optional<SaveAgentRunCommand> run,
      List<AgentRunStepCommand> steps,
      List<AgentEvalResultCommand> evalResults) {}

  private Map<String, Object> asMap(Object value) {
    if (value instanceof Map<?, ?> map) {
      return objectMapper.convertValue(map, new TypeReference<Map<String, Object>>() {});
    }
    return Map.of();
  }

  private List<Object> asList(Object value) {
    if (value instanceof List<?> list) {
      return List.copyOf(list);
    }
    return List.of();
  }

  private String string(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String text = String.valueOf(value);
    return text.isBlank() ? fallback : text;
  }

  private OffsetDateTime offset(Object value) {
    if (value == null || String.valueOf(value).isBlank()) {
      return null;
    }
    return OffsetDateTime.parse(String.valueOf(value).replace("Z", "+00:00"));
  }

  private int intValue(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value == null) {
      return 0;
    }
    return Integer.parseInt(String.valueOf(value));
  }

  private long longValue(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value == null) {
      return 0L;
    }
    return Long.parseLong(String.valueOf(value));
  }

  private boolean bool(Object value) {
    if (value instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(String.valueOf(value));
  }

  private BigDecimal decimal(Object value) {
    if (value instanceof Number number) {
      return BigDecimal.valueOf(number.doubleValue());
    }
    if (value == null) {
      return BigDecimal.ZERO;
    }
    return new BigDecimal(String.valueOf(value));
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (Exception ex) {
      return "{}";
    }
  }

  private static String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
```

---

## 6.10 修改 `AiDiagnosisService.java`

### 6.10.1 新增字段

```java id="w34uwu"
private final AgentObservabilityExtractor observabilityExtractor;
```

### 6.10.2 修改构造函数

```java id="e962t9"
public AiDiagnosisService(
    AiRepository repository,
    AiAgentClient agentClient,
    ObjectMapper objectMapper) {
  this(repository, agentClient, objectMapper, new AgentContractValidator(), new AgentObservabilityExtractor(objectMapper));
}

public AiDiagnosisService(
    AiRepository repository,
    AiAgentClient agentClient,
    ObjectMapper objectMapper,
    AgentContractValidator contractValidator) {
  this(repository, agentClient, objectMapper, contractValidator, new AgentObservabilityExtractor(objectMapper));
}

public AiDiagnosisService(
    AiRepository repository,
    AiAgentClient agentClient,
    ObjectMapper objectMapper,
    AgentContractValidator contractValidator,
    AgentObservabilityExtractor observabilityExtractor) {
  this.repository = repository;
  this.agentClient = agentClient;
  this.objectMapper = objectMapper;
  this.contractValidator = contractValidator;
  this.observabilityExtractor = observabilityExtractor;
}
```

### 6.10.3 在 `repository.saveDiagnosis(...)` 后追加

放在当前 `repository.saveDiagnosis(...)` 之后、`addIncidentTimeline(...)` 之前：

```java id="ae1xst"
persistAgentObservability(
    diagnosisId,
    tenantId,
    incidentId,
    agentRequest.traceId(),
    agentResponse);
```

也就是插在当前保存 diagnosis 后面。当前保存 diagnosis 的位置在 `AiDiagnosisService` 第 106–116 行。

### 6.10.4 新增方法

```java id="ql27hj"
public AgentRunDetailResponse latestRun(String tenantId, String incidentId) {
  ensureIncidentExists(tenantId, incidentId);

  var run =
      repository
          .findLatestAgentRun(tenantId, incidentId)
          .orElseThrow(() -> new AppException("AGENT_RUN_NOT_FOUND", "Agent run not found"));

  return new AgentRunDetailResponse(
      run.id(),
      run.diagnosisId(),
      run.incidentId(),
      run.traceId(),
      run.contractVersion(),
      run.generationMode(),
      run.provider(),
      run.model(),
      run.status(),
      run.startedAt(),
      run.finishedAt(),
      run.durationMs(),
      run.fallbackReason(),
      run.safetyJson(),
      run.evalJson(),
      repository.listAgentRunSteps(run.id()),
      repository.listAgentEvalResults(run.id()),
      run.createdAt());
}

private void persistAgentObservability(
    String diagnosisId,
    String tenantId,
    String incidentId,
    String traceId,
    AgentDiagnosisResponse response) {
  var data = observabilityExtractor.extract(diagnosisId, tenantId, incidentId, traceId, response);

  data.run().ifPresent(repository::saveAgentRun);

  if (!data.steps().isEmpty()) {
    repository.saveAgentRunSteps(data.steps());
  }

  if (!data.evalResults().isEmpty()) {
    repository.saveAgentEvalResults(data.evalResults());
  }
}
```

### 6.10.5 需要新增 imports

```java id="wcu08z"
import io.aegisops.ai.client.dto.AgentRunDetailResponse;
```

---

## 6.11 修改 `AiDiagnosisController.java`

```java id="db0nxg"
package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.AgentRunDetailResponse;
import io.aegisops.ai.client.dto.AiDiagnoseRequest;
import io.aegisops.ai.client.dto.AiDiagnosisResponse;
import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents/{incidentId}/ai")
public class AiDiagnosisController {
  private final AiDiagnosisService diagnosisService;

  public AiDiagnosisController(AiDiagnosisService diagnosisService) {
    this.diagnosisService = diagnosisService;
  }

  @GetMapping("/latest")
  public ApiResponse<AiDiagnosisResponse> latest(@PathVariable String incidentId) {
    return ApiResponse.ok(diagnosisService.latest(TenantContext.requireTenantId(), incidentId));
  }

  @PostMapping("/diagnose")
  public ApiResponse<AiDiagnosisResponse> diagnose(
      @PathVariable String incidentId, @RequestBody(required = false) AiDiagnoseRequest request) {
    return ApiResponse.ok(
        diagnosisService.diagnose(TenantContext.requireTenantId(), incidentId, request));
  }

  @GetMapping("/runs/latest")
  public ApiResponse<AgentRunDetailResponse> latestRun(@PathVariable String incidentId) {
    return ApiResponse.ok(diagnosisService.latestRun(TenantContext.requireTenantId(), incidentId));
  }
}
```

---

## 6.12 在 `JdbcAiRepository.java` 追加方法

```java id="tyqhn7"
@Override
public void saveAgentRun(SaveAgentRunCommand command) {
  jdbc.update(
      """
      insert into agent_run(
        id, diagnosis_id, tenant_id, incident_id, trace_id,
        contract_version, generation_mode, provider, model, status,
        started_at, finished_at, duration_ms, fallback_reason,
        safety, eval_result, created_at
      )
      values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, now())
      """,
      command.id(),
      command.diagnosisId(),
      command.tenantId(),
      command.incidentId(),
      command.traceId(),
      command.contractVersion(),
      command.generationMode(),
      command.provider(),
      command.model(),
      command.status(),
      command.startedAt(),
      command.finishedAt(),
      command.durationMs(),
      command.fallbackReason(),
      command.safetyJson(),
      command.evalJson());
}

@Override
public void saveAgentRunSteps(List<AgentRunStepCommand> commands) {
  jdbc.batchUpdate(
      """
      insert into agent_run_step(
        id, run_id, sequence_no, step_name, step_type, status,
        started_at, finished_at, duration_ms,
        input_summary, output_summary, error_message, metadata, created_at
      )
      values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
      """,
      commands,
      commands.size(),
      (ps, command) -> {
        ps.setString(1, command.id());
        ps.setString(2, command.runId());
        ps.setInt(3, command.sequenceNo());
        ps.setString(4, command.stepName());
        ps.setString(5, command.stepType());
        ps.setString(6, command.status());
        ps.setObject(7, command.startedAt());
        ps.setObject(8, command.finishedAt());
        ps.setLong(9, command.durationMs());
        ps.setString(10, command.inputSummary());
        ps.setString(11, command.outputSummary());
        ps.setString(12, command.errorMessage());
        ps.setString(13, command.metadataJson());
      });
}

@Override
public void saveAgentEvalResults(List<AgentEvalResultCommand> commands) {
  jdbc.batchUpdate(
      """
      insert into agent_eval_result(
        id, run_id, evaluator_name, check_name, passed, score,
        reason, details, created_at
      )
      values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
      """,
      commands,
      commands.size(),
      (ps, command) -> {
        ps.setString(1, command.id());
        ps.setString(2, command.runId());
        ps.setString(3, command.evaluatorName());
        ps.setString(4, command.checkName());
        ps.setBoolean(5, command.passed());
        ps.setBigDecimal(6, command.score());
        ps.setString(7, command.reason());
        ps.setString(8, command.detailsJson());
      });
}

@Override
public Optional<AgentRunRecord> findLatestAgentRun(String tenantId, String incidentId) {
  try {
    return Optional.ofNullable(
        jdbc.queryForObject(
            """
            select id, diagnosis_id, incident_id, trace_id, contract_version,
                   generation_mode, provider, model, status,
                   started_at, finished_at, duration_ms, fallback_reason,
                   safety::text as safety_json,
                   eval_result::text as eval_json,
                   created_at
            from agent_run
            where tenant_id = ? and incident_id = ?
            order by created_at desc
            limit 1
            """,
            (rs, rowNum) ->
                new AgentRunRecord(
                    rs.getString("id"),
                    rs.getString("diagnosis_id"),
                    rs.getString("incident_id"),
                    rs.getString("trace_id"),
                    rs.getString("contract_version"),
                    rs.getString("generation_mode"),
                    rs.getString("provider"),
                    rs.getString("model"),
                    rs.getString("status"),
                    rs.getObject("started_at", OffsetDateTime.class),
                    rs.getObject("finished_at", OffsetDateTime.class),
                    rs.getLong("duration_ms"),
                    rs.getString("fallback_reason"),
                    rs.getString("safety_json"),
                    rs.getString("eval_json"),
                    rs.getObject("created_at", OffsetDateTime.class)),
            tenantId,
            incidentId));
  } catch (EmptyResultDataAccessException ex) {
    return Optional.empty();
  }
}

@Override
public List<AgentRunStepRecord> listAgentRunSteps(String runId) {
  return jdbc.query(
      """
      select id, sequence_no, step_name, step_type, status,
             started_at, finished_at, duration_ms,
             input_summary, output_summary, error_message,
             metadata::text as metadata_json
      from agent_run_step
      where run_id = ?
      order by sequence_no asc
      """,
      (rs, rowNum) ->
          new AgentRunStepRecord(
              rs.getString("id"),
              rs.getInt("sequence_no"),
              rs.getString("step_name"),
              rs.getString("step_type"),
              rs.getString("status"),
              rs.getObject("started_at", OffsetDateTime.class),
              rs.getObject("finished_at", OffsetDateTime.class),
              rs.getLong("duration_ms"),
              rs.getString("input_summary"),
              rs.getString("output_summary"),
              rs.getString("error_message"),
              rs.getString("metadata_json")),
      runId);
}

@Override
public List<AgentEvalResultRecord> listAgentEvalResults(String runId) {
  return jdbc.query(
      """
      select id, evaluator_name, check_name, passed, score,
             reason, details::text as details_json, created_at
      from agent_eval_result
      where run_id = ?
      order by created_at asc
      """,
      (rs, rowNum) ->
          new AgentEvalResultRecord(
              rs.getString("id"),
              rs.getString("evaluator_name"),
              rs.getString("check_name"),
              rs.getBoolean("passed"),
              rs.getBigDecimal("score"),
              rs.getString("reason"),
              rs.getString("details_json"),
              rs.getObject("created_at", OffsetDateTime.class)),
      runId);
}
```

### 需要新增 imports

```java id="tui3lq"
import io.aegisops.ai.client.dto.AgentEvalResultCommand;
import io.aegisops.ai.client.dto.AgentEvalResultRecord;
import io.aegisops.ai.client.dto.AgentRunRecord;
import io.aegisops.ai.client.dto.AgentRunStepCommand;
import io.aegisops.ai.client.dto.AgentRunStepRecord;
import io.aegisops.ai.client.dto.SaveAgentRunCommand;
```

---

# 7. Java 单元测试

## 7.1 新增 `AgentObservabilityExtractorTest.java`

```java id="b2w4ek"
package io.aegisops.ai.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentObservabilityExtractorTest {
  @Test
  void extractsRunStepsAndEvalChecks() {
    AgentDiagnosisResponse response =
        new AgentDiagnosisResponse(
            "agent-diagnosis.v1",
            "openai-compatible",
            "test-model",
            "aegisops_diagnosis_graph",
            "summary",
            "root",
            "impact",
            List.of("step"),
            List.of(),
            List.of(),
            Map.of(
                "agentRun",
                Map.of(
                    "runId", "run_1",
                    "traceId", "trace_1",
                    "contractVersion", "agent-diagnosis.v1",
                    "generationMode", "openai-compatible",
                    "provider", "openai-compatible",
                    "model", "test-model",
                    "status", "completed",
                    "startedAt", "2026-06-16T10:00:00Z",
                    "finishedAt", "2026-06-16T10:00:01Z",
                    "durationMs", 1000,
                    "fallbackReason", "",
                    "safety", Map.of("autoExecutionAllowed", false),
                    "steps", List.of(
                        Map.of(
                            "id", "step_1",
                            "sequenceNo", 1,
                            "stepName", "load_context",
                            "stepType", "node",
                            "status", "completed",
                            "startedAt", "2026-06-16T10:00:00Z",
                            "finishedAt", "2026-06-16T10:00:00Z",
                            "durationMs", 1,
                            "inputSummary", "in",
                            "outputSummary", "out",
                            "metadata", Map.of()))),
                "agentEval",
                Map.of(
                    "evaluatorName", "aegisops-basic-eval-v1",
                    "score", 100,
                    "passed", true,
                    "checks", List.of(
                        Map.of(
                            "name", "required_fields",
                            "passed", true,
                            "score", 100,
                            "reason", "",
                            "details", Map.of())))));

    AgentObservabilityExtractor extractor = new AgentObservabilityExtractor(new ObjectMapper());
    var data = extractor.extract("diag_1", "tenant_1", "inc_1", "trace_1", response);

    assertTrue(data.run().isPresent());
    assertEquals("run_1", data.run().get().id());
    assertEquals(1, data.steps().size());
    assertEquals("load_context", data.steps().get(0).stepName());
    assertEquals(1, data.evalResults().size());
    assertEquals("required_fields", data.evalResults().get(0).checkName());
  }

  @Test
  void returnsEmptyWhenRawHasNoAgentRun() {
    AgentDiagnosisResponse response =
        new AgentDiagnosisResponse(
            "agent-diagnosis.v1",
            "aiops-agent",
            "model",
            "agent",
            "summary",
            "root",
            "impact",
            List.of(),
            List.of(),
            List.of(),
            Map.of());

    AgentObservabilityExtractor extractor = new AgentObservabilityExtractor(new ObjectMapper());
    var data = extractor.extract("diag_1", "tenant_1", "inc_1", "trace_1", response);

    assertTrue(data.run().isEmpty());
    assertTrue(data.steps().isEmpty());
    assertTrue(data.evalResults().isEmpty());
  }
}
```

---

## 7.2 新增 `AiDiagnosisServiceObservabilityTest.java`

```java id="rw2694"
package io.aegisops.ai.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.ai.client.dto.AiDiagnoseRequest;
import io.aegisops.ai.client.dto.AiIncidentRecord;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AiDiagnosisServiceObservabilityTest {
  @Test
  void diagnosePersistsAgentObservabilityWhenRawContainsAgentRun() {
    AiRepository repository = Mockito.mock(AiRepository.class);
    AiAgentClient agentClient = Mockito.mock(AiAgentClient.class);

    when(repository.findIncident("tenant_1", "inc_1"))
        .thenReturn(Optional.of(
            new AiIncidentRecord(
                "inc_1",
                "tenant_1",
                "CPU high",
                "summary",
                "critical",
                "open",
                "zabbix",
                "asset_1",
                "agg",
                1,
                null,
                BigDecimal.ZERO,
                OffsetDateTime.now().minusMinutes(10),
                OffsetDateTime.now().minusMinutes(10),
                OffsetDateTime.now(),
                OffsetDateTime.now().minusMinutes(10),
                OffsetDateTime.now())));

    when(repository.listIncidentAlerts("tenant_1", "inc_1")).thenReturn(List.of());
    when(repository.findLatestRca("tenant_1", "inc_1")).thenReturn(Optional.empty());

    when(agentClient.diagnose(any()))
        .thenReturn(new AgentDiagnosisResponse(
            "agent-diagnosis.v1",
            "aiops-agent",
            "langgraph-deterministic",
            "aegisops_diagnosis_graph",
            "summary",
            "root",
            "impact",
            List.of("step"),
            List.of(),
            List.of(),
            Map.of(
                "agentRun",
                Map.of(
                    "runId", "run_1",
                    "traceId", "trace_1",
                    "contractVersion", "agent-diagnosis.v1",
                    "generationMode", "deterministic",
                    "provider", "aiops-agent",
                    "model", "langgraph-deterministic",
                    "status", "completed",
                    "startedAt", "2026-06-16T10:00:00Z",
                    "finishedAt", "2026-06-16T10:00:01Z",
                    "durationMs", 1000,
                    "safety", Map.of("autoExecutionAllowed", false),
                    "steps", List.of()),
                "agentEval",
                Map.of(
                    "evaluatorName", "aegisops-basic-eval-v1",
                    "score", 100,
                    "passed", true,
                    "checks", List.of()))));

    when(repository.findDiagnosis(Mockito.eq("tenant_1"), Mockito.anyString()))
        .thenAnswer(invocation -> Optional.empty());

    AiDiagnosisService service =
        new AiDiagnosisService(repository, agentClient, new ObjectMapper());

    try {
      service.diagnose("tenant_1", "inc_1", new AiDiagnoseRequest(true, "zh-CN"));
    } catch (Exception ignored) {
      // The test focuses on side effects before final findDiagnosis lookup.
    }

    verify(repository).saveDiagnosis(any());
    verify(repository).saveAgentRun(any());
    verify(repository).saveAgentRunSteps(any());
    verify(repository).saveAgentEvalResults(any());
  }
}
```

> 如果你的 `AiIncidentRecord` 构造参数和当前仓库略有差异，以当前 `AiRows.incident()` 对应字段为准调整测试对象。

---

# 8. Docker Compose 配置

在 `aiops-agent.environment` 追加：

```yaml id="uidcjj"
AIOPS_AGENT_TRACE_ENABLED: "true"
AIOPS_AGENT_EVAL_ENABLED: "true"
```

完整建议：

```yaml id="c2p774"
AIOPS_AGENT_TRACE_ENABLED: "true"
AIOPS_AGENT_EVAL_ENABLED: "true"
```

---

# 9. 验证命令

## Python

```powershell id="z8csv6"
cd apps/aiops-agent
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[test]"
pytest
```

## Java

```powershell id="k5u3re"
mvn -pl modules/aiops-ai-client -am test
mvn -pl apps/aiops-server -am test
```

## 全量

```powershell id="r4eppt"
mvn test
```

---

# 10. Phase4.4 验收标准

```txt id="h9tgp1"
1. Python DiagnoseResponse.raw 包含 agentRun。
2. Python DiagnoseResponse.raw 包含 agentEval。
3. agentRun.steps 至少包含 load_context / analyze_alerts / analyze_rca / query_evidence / search_runbooks / safety_check / generate_diagnosis。
4. 每个 step 有 sequenceNo / stepName / stepType / status / durationMs。
5. Java 保存 ai_diagnosis 后继续保存 agent_run。
6. Java 保存 agent_run_step。
7. Java 保存 agent_eval_result。
8. GET /api/incidents/{incidentId}/ai/runs/latest 可查询最近一次运行。
9. trace_enabled=false 时不写 agentRun。
10. eval_enabled=false 时不写 agentEval。
11. 不影响原有 AI Diagnose 主流程。
```

---

# 11. Phase4.4 完成后的路线

```txt id="pjh3jj"
Phase4.0  LangGraph deterministic Agent
Phase4.1  Contract + Safety Boundary
Phase4.2  OpenAI-compatible LLM Provider
Phase4.3  Metrics / Logs / Changes Evidence Tools
Phase4.4  Agent Run Trace + Eval
Phase4.5  jOOQ Persistence Refactor
Phase5.0  Runbook Recommendation
Phase5.1  AutomationPlan + Approval
Phase5.2  aiops-runner
```

Phase4.4 做完后，你的 Agent 就不是一个“黑盒调用”了，而是：

```txt id="zhshx6"
一次诊断
  =
  Agent Run
  + Graph Steps
  + Evidence Usage
  + LLM/Fallback
  + Safety Result
  + Eval Result
```

这会让后面 Phase5 做审批执行、Phase6 做复盘知识库时，有足够的审计和评测基础。
