from aiops_agent.prompt import build_diagnosis_prompt
from aiops_agent.schemas import DiagnoseRequest, IncidentContext


def test_build_diagnosis_prompt_includes_evidence_sections():
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
        metrics={"available": True, "series": []},
        logs={"available": True, "patterns": []},
        changes={"available": True, "events": []},
        runbook_suggestions=["Generic incident triage checklist"],
        risks=["Do not execute remediation automatically."],
    )

    content = messages[1]["content"]

    assert "metrics" in content
    assert "logs" in content
    assert "changes" in content
    assert "Do not invent metrics, logs, or changes" in content
