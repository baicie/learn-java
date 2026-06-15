from aiops_agent.graph import run_diagnosis_graph
from aiops_agent.schemas import AlertContext, DiagnoseRequest, IncidentContext, RcaContext
from aiops_agent.settings import Settings


def test_diagnosis_graph_returns_contract_version_and_safety_raw():
    settings = Settings(
        contract_version="agent-diagnosis.v1",
        provider="aiops-agent",
        model="langgraph-deterministic",
        agent_name="aegisops_diagnosis_graph",
    )

    request = DiagnoseRequest(
        contractVersion="agent-diagnosis.v1",
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(
            id="inc_1",
            title="CPU high",
            severity="critical",
            status="open",
            primaryAssetId="asset_1",
            alertCount=2,
        ),
        alerts=[
            AlertContext(id="a1", title="CPU high", severity="critical", assetId="asset_1", fingerprint="fp_cpu"),
            AlertContext(id="a2", title="CPU high", severity="warning", assetId="asset_1", fingerprint="fp_cpu"),
        ],
        rca=RcaContext(
            id="rca_1",
            suspectedRootCause="CPU saturation",
            confidence=0.8,
            summary="RCA summary",
        ),
        traceId="trace_1",
    )

    response = run_diagnosis_graph(request, settings)

    assert response.contractVersion == "agent-diagnosis.v1"
    assert response.provider == "aiops-agent"
    assert response.agentName == "aegisops_diagnosis_graph"
    assert response.raw["traceId"] == "trace_1"
    assert response.raw["safety"]["autoExecutionAllowed"] is False
    assert response.nextSteps
    assert response.risks
