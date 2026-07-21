from aiops_agent.schemas import (
    DiagnoseRequest,
    IncidentContext,
    WorkRecordGenerateRequest,
    WorkRecordGenerateResponse,
)


def test_diagnose_request_defaults():
    request = DiagnoseRequest(
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1"),
        traceId="trace_1",
    )

    assert request.locale == "zh-CN"
    assert request.alerts == []
    assert request.rca is None
    assert request.contractVersion == "agent-diagnosis.v1"


def test_work_record_request_accepts_optional_actor_id():
    request = WorkRecordGenerateRequest(
        generationType="monthly_report",
        tenantId="tenant_1",
        resourceId="2026-07",
        actorId="user_1",
        traceId="trace_1",
    )

    assert request.actorId == "user_1"


def test_work_record_response_accepts_provider_metadata():
    response = WorkRecordGenerateResponse(
        provider="dify",
        model="dify-workflow",
        promptVersion="work-record-monthly-v2",
        markdown="# 月报",
        providerRunId="run-1",
        providerWorkflowId="workflow-1",
        providerWorkflowVersion="version-1",
        providerDurationMs=1250,
        providerTotalTokens=321,
        fallbackReason=None,
    )

    assert response.providerRunId == "run-1"
    assert response.providerWorkflowId == "workflow-1"
    assert response.providerWorkflowVersion == "version-1"
    assert response.providerDurationMs == 1250
    assert response.providerTotalTokens == 321
    assert response.fallbackReason is None
