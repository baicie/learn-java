import json

import pytest

from aiops_agent.llm import (
    DiagnosisDraft,
    diagnosis_response_from_draft,
    parse_diagnosis_json,
)
from aiops_agent.settings import Settings


def test_parse_diagnosis_json_strict_json():
    draft = parse_diagnosis_json(json.dumps({
        "summary": "summary",
        "rootCause": "root",
        "impact": "impact",
        "nextSteps": ["step"],
        "runbookSuggestions": ["runbook"],
        "risks": ["risk"],
    }))

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
