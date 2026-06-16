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
