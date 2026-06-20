import json

import pytest

from aiops_agent.llm import (
    DiagnosisDraft,
    OpenAiCompatibleLlmClient,
    diagnosis_response_from_draft,
    parse_diagnosis_json,
)
from aiops_agent.settings import Settings


class FakeHttpxResponse:
    def __init__(self, payload: dict):
        self.payload = payload

    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict:
        return self.payload


def test_parse_diagnosis_json_strict_json():
    draft = parse_diagnosis_json(
        json.dumps(
            {
                "summary": "summary",
                "rootCause": "root",
                "impact": "impact",
                "nextSteps": ["step"],
                "runbookSuggestions": ["runbook"],
                "risks": ["risk"],
            }
        )
    )

    assert draft.summary == "summary"
    assert draft.rootCause == "root"
    assert draft.nextSteps == ["step"]


def test_parse_diagnosis_json_extracts_json_from_text():
    draft = parse_diagnosis_json("""
    Some preface:
    {
      "summary": "summary",
      "rootCause": "root",
      "impact": "impact",
      "nextSteps": ["step"],
      "runbookSuggestions": [],
      "risks": []
    }
    """)

    assert draft.summary == "summary"
    assert draft.rootCause == "root"


def test_parse_diagnosis_json_rejects_invalid_json():
    with pytest.raises(Exception):
        parse_diagnosis_json("not json")


def test_parse_diagnosis_json_rejects_non_object_json():
    with pytest.raises(Exception):
        parse_diagnosis_json("[1, 2, 3]")


def test_diagnosis_response_from_draft_preserves_contract_version():
    settings = Settings(
        contract_version="agent-diagnosis.v1",
        provider="aiops-agent",
        model="test-model",
        agent_name="aegisops_diagnosis_graph",
    )

    response = diagnosis_response_from_draft(
        DiagnosisDraft(
            summary="summary",
            rootCause="root",
            impact="impact",
            nextSteps=["step"],
            runbookSuggestions=[],
            risks=[],
        ),
        settings,
        raw={"ok": True},
        provider="openai-compatible",
    )

    assert response.contractVersion == "agent-diagnosis.v1"
    assert response.provider == "openai-compatible"
    assert response.model == "test-model"
    assert response.rootCause == "root"


def test_openai_compatible_client_sends_response_format_by_default(monkeypatch):
    captured = {}

    def fake_post(url, headers, json, timeout):
        captured["url"] = url
        captured["headers"] = headers
        captured["json"] = json
        captured["timeout"] = timeout
        return FakeHttpxResponse(
            {"choices": [{"message": {"content": '{"summary":"s","rootCause":"r","impact":"i"}'}}]}
        )

    monkeypatch.setattr("aiops_agent.llm.httpx.post", fake_post)

    settings = Settings(
        generation_mode="openai-compatible",
        openai_base_url="https://example.com/v1/",
        openai_api_key="test-key",
        model="test-model",
    )

    content = OpenAiCompatibleLlmClient(settings).complete_json(
        [{"role": "user", "content": "hello"}]
    )

    assert captured["url"] == "https://example.com/v1/chat/completions"
    assert captured["headers"]["Authorization"] == "Bearer test-key"
    assert captured["json"]["model"] == "test-model"
    assert captured["json"]["response_format"] == {"type": "json_object"}
    assert content


def test_openai_compatible_client_can_disable_response_format(monkeypatch):
    captured = {}

    def fake_post(url, headers, json, timeout):
        captured["json"] = json
        return FakeHttpxResponse(
            {"choices": [{"message": {"content": '{"summary":"s","rootCause":"r","impact":"i"}'}}]}
        )

    monkeypatch.setattr("aiops_agent.llm.httpx.post", fake_post)

    settings = Settings(
        generation_mode="openai-compatible",
        openai_base_url="https://example.com/v1/",
        model="test-model",
        openai_response_format_enabled=False,
    )

    OpenAiCompatibleLlmClient(settings).complete_json([{"role": "user", "content": "hello"}])

    assert "response_format" not in captured["json"]


def test_openai_compatible_client_requires_base_url():
    settings = Settings(
        generation_mode="openai-compatible",
        openai_base_url="",
        model="test-model",
    )

    with pytest.raises(RuntimeError):
        OpenAiCompatibleLlmClient(settings).complete_json([{"role": "user", "content": "hello"}])
