from aiops_agent.graph import run_diagnosis_graph
from aiops_agent.schemas import AlertContext, DiagnoseRequest, IncidentContext, RcaContext
from aiops_agent.settings import Settings


def test_diagnosis_graph_returns_structured_result():
    settings = Settings(
        internal_token="token",
        provider="aiops-agent",
        model="langgraph-deterministic",
        agent_name="aegisops_diagnosis_graph",
    )

    request = DiagnoseRequest(
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
    )

    response = run_diagnosis_graph(request, settings)

    assert response.provider == "aiops-agent"
    assert response.model == "langgraph-deterministic"
    assert response.agentName == "aegisops_diagnosis_graph"
    assert "CPU saturation" in response.rootCause
    assert response.nextSteps
    assert response.runbookSuggestions
    assert response.risks
    assert response.raw["graph"] == "aegisops_diagnosis_graph"
