from aiops_agent.prompt import build_diagnosis_prompt
from aiops_agent.schemas import DiagnoseRequest, IncidentContext


def test_build_diagnosis_prompt_returns_system_and_user_messages():
    request = DiagnoseRequest(
        contractVersion="agent-diagnosis.v1",
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1", title="CPU high"),
        alerts=[],
        locale="zh-CN",
        traceId="trace_1",
    )

    messages = build_diagnosis_prompt(
        request=request,
        incident_summary={"title": "CPU high"},
        alert_analysis={"count": 1},
        rca_analysis={"hasRca": False},
        metrics={"available": False},
        logs={"available": False},
        runbook_suggestions=["Generic incident triage checklist"],
        risks=["Do not execute remediation automatically."],
    )

    assert len(messages) == 2
    assert messages[0]["role"] == "system"
    assert messages[1]["role"] == "user"
    assert "Return strict JSON only" in messages[0]["content"]
    assert "outputSchema" in messages[1]["content"]
    assert "trace_1" in messages[1]["content"]
