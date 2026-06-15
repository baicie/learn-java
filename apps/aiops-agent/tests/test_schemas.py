from aiops_agent.schemas import DiagnoseRequest, IncidentContext


def test_diagnose_request_defaults():
    request = DiagnoseRequest(
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1"),
    )

    assert request.locale == "zh-CN"
    assert request.alerts == []
    assert request.rca is None
