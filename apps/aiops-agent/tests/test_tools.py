from aiops_agent.schemas import AlertContext, DiagnoseRequest, IncidentContext, RcaContext
from aiops_agent.tools import inspect_alerts, inspect_rca_evidence, search_runbooks_stub


def test_inspect_alerts_returns_dominant_fingerprint_and_asset():
    alerts = [
        AlertContext(
            id="a1", severity="critical", title="CPU high", assetId="asset_1", fingerprint="fp_cpu"
        ),
        AlertContext(
            id="a2", severity="warning", title="CPU high", assetId="asset_1", fingerprint="fp_cpu"
        ),
        AlertContext(
            id="a3", severity="info", title="Other", assetId="asset_2", fingerprint="fp_other"
        ),
    ]

    result = inspect_alerts(alerts)

    assert result["count"] == 3
    assert result["topSeverity"] == "critical"
    assert result["dominantFingerprint"] == "fp_cpu"
    assert result["dominantAssetId"] == "asset_1"


def test_inspect_rca_evidence_handles_missing_rca():
    request = DiagnoseRequest(
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1"),
        traceId="trace_1",
    )

    result = inspect_rca_evidence(request)

    assert result["hasRca"] is False
    assert result["confidence"] == 0


def test_runbook_stub_recommends_cpu_runbook():
    request = DiagnoseRequest(
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1"),
        alerts=[AlertContext(id="a1", title="CPU high", severity="critical")],
        rca=RcaContext(id="rca_1", suspectedRootCause="CPU saturation"),
        traceId="trace_1",
    )

    alert_analysis = inspect_alerts(request.alerts)
    rca_analysis = inspect_rca_evidence(request)
    result = search_runbooks_stub(request, alert_analysis, rca_analysis)

    assert "Host resource saturation investigation" in result
