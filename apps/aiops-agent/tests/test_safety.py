import pytest

from aiops_agent.safety import apply_safety_boundary, assert_safe_response, find_forbidden_keywords
from aiops_agent.schemas import DiagnoseResponse


def response_with_step(step: str) -> DiagnoseResponse:
    return DiagnoseResponse(
        contractVersion="agent-diagnosis.v1",
        provider="aiops-agent",
        model="langgraph-deterministic",
        agentName="aegisops_diagnosis_graph",
        summary="summary",
        rootCause="root",
        impact="impact",
        nextSteps=[step],
        runbookSuggestions=[],
        risks=[],
        raw={},
    )


def test_find_forbidden_keywords_detects_rm_rf():
    response = response_with_step("run rm -rf / automatically")

    result = find_forbidden_keywords(response)

    assert "rm -rf" in result


def test_apply_safety_boundary_adds_mandatory_risks():
    response = response_with_step("check cpu usage")

    safe = apply_safety_boundary(response)

    assert safe.raw["safety"]["autoExecutionAllowed"] is False
    assert safe.raw["safety"]["sanitized"] is False
    assert any("Do not execute remediation automatically" in risk for risk in safe.risks)


def test_apply_safety_boundary_removes_unsafe_next_step():
    response = response_with_step("run rm -rf / automatically")

    safe = apply_safety_boundary(response)

    assert safe.raw["safety"]["autoExecutionAllowed"] is False
    assert safe.raw["safety"]["sanitized"] is True
    assert "rm -rf" in safe.raw["safety"]["blockedKeywords"]
    assert all("rm -rf" not in step.lower() for step in safe.nextSteps)
    assert any("Blocked unsafe remediation suggestion" in step for step in safe.nextSteps)
    assert any("unsafe remediation wording" in risk for risk in safe.risks)


def test_apply_safety_boundary_sanitizes_inline_text_fields():
    response = DiagnoseResponse(
        contractVersion="agent-diagnosis.v1",
        provider="aiops-agent",
        model="langgraph-deterministic",
        agentName="aegisops_diagnosis_graph",
        summary="summary with drop database suggestion",
        rootCause="root",
        impact="impact",
        nextSteps=[],
        runbookSuggestions=[],
        risks=[],
        raw={},
    )

    safe = apply_safety_boundary(response)

    assert "drop database" not in safe.summary.lower()
    assert "[blocked unsafe remediation wording]" in safe.summary
    assert safe.raw["safety"]["sanitized"] is True


def test_assert_safe_response_raises_for_unsafe_keyword():
    response = response_with_step("kubectl delete namespace prod")

    with pytest.raises(RuntimeError):
        assert_safe_response(response)
