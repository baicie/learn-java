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
