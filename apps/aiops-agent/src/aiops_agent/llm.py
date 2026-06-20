from __future__ import annotations

import json
from typing import Any, Protocol

import httpx
from pydantic import BaseModel, Field

from aiops_agent.schemas import DiagnoseResponse
from aiops_agent.settings import Settings


class DiagnosisDraft(BaseModel):
    summary: str = "No summary generated."
    rootCause: str = "No root cause generated."
    impact: str = "Impact is unknown."
    nextSteps: list[str] = Field(default_factory=list)
    runbookSuggestions: list[str] = Field(default_factory=list)
    risks: list[str] = Field(default_factory=list)


class LlmClient(Protocol):
    def complete_json(self, messages: list[dict[str, str]]) -> str: ...


class OpenAiCompatibleLlmClient:
    def __init__(self, settings: Settings):
        self.settings = settings

    def complete_json(self, messages: list[dict[str, str]]) -> str:
        base_url = self.settings.normalized_openai_base_url()
        if not base_url:
            raise RuntimeError(
                "AIOPS_AGENT_OPENAI_BASE_URL is required for openai-compatible generation mode"
            )

        headers: dict[str, str] = {"Content-Type": "application/json"}
        if self.settings.openai_api_key:
            headers["Authorization"] = f"Bearer {self.settings.openai_api_key}"

        payload: dict[str, Any] = {
            "model": self.settings.model,
            "messages": messages,
            "temperature": self.settings.openai_temperature,
            "max_tokens": self.settings.openai_max_tokens,
        }

        if self.settings.openai_response_format_enabled:
            payload["response_format"] = {"type": "json_object"}

        response = httpx.post(
            f"{base_url}/chat/completions",
            headers=headers,
            json=payload,
            timeout=self.settings.openai_timeout_seconds,
        )
        response.raise_for_status()

        data = response.json()
        content = data.get("choices", [{}])[0].get("message", {}).get("content", "")

        if not content:
            raise RuntimeError("LLM returned empty content")

        return str(content)


def parse_diagnosis_json(content: str) -> DiagnosisDraft:
    if not content or not content.strip():
        raise ValueError("empty diagnosis content")

    payload = json.loads(_extract_json(content))

    if not isinstance(payload, dict):
        raise ValueError("diagnosis content must be a JSON object")

    return DiagnosisDraft(
        summary=_text(payload.get("summary"), "No summary generated."),
        rootCause=_text(payload.get("rootCause"), "No root cause generated."),
        impact=_text(payload.get("impact"), "Impact is unknown."),
        nextSteps=_string_list(payload.get("nextSteps")),
        runbookSuggestions=_string_list(payload.get("runbookSuggestions")),
        risks=_string_list(payload.get("risks")),
    )


def diagnosis_response_from_draft(
    draft: DiagnosisDraft,
    settings: Settings,
    raw: dict[str, Any],
    provider: str | None = None,
) -> DiagnoseResponse:
    return DiagnoseResponse(
        contractVersion=settings.contract_version,
        provider=provider or settings.provider,
        model=settings.model,
        agentName=settings.agent_name,
        summary=draft.summary,
        rootCause=draft.rootCause,
        impact=draft.impact,
        nextSteps=draft.nextSteps,
        runbookSuggestions=draft.runbookSuggestions,
        risks=draft.risks,
        raw=raw,
    )


def _extract_json(content: str) -> str:
    value = content.strip()
    if value.startswith("{") and value.endswith("}"):
        return value

    start = value.find("{")
    end = value.rfind("}")

    if start >= 0 and end > start:
        return value[start : end + 1]

    return value


def _string_list(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item) for item in value if str(item).strip()]


def _text(value: Any, fallback: str) -> str:
    if value is None:
        return fallback

    text = str(value).strip()
    return text or fallback
