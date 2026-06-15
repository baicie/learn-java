from fastapi.testclient import TestClient

from aiops_agent.main import app
from aiops_agent.settings import settings


client = TestClient(app)


def test_health():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["ok"] is True
    assert response.json()["agentName"] == settings.agent_name


def test_diagnose_rejects_missing_token():
    response = client.post(
        "/v1/diagnose",
        json={
            "tenantId": "tenant_1",
            "incidentId": "inc_1",
            "incident": {"id": "inc_1"},
            "alerts": [],
        },
    )

    assert response.status_code == 401


def test_diagnose_returns_structured_response():
    response = client.post(
        "/v1/diagnose",
        headers={"X-AegisOps-Internal-Token": settings.internal_token},
        json={
            "tenantId": "tenant_1",
            "incidentId": "inc_1",
            "incident": {
                "id": "inc_1",
                "title": "CPU high",
                "severity": "critical",
                "status": "open",
                "primaryAssetId": "asset_1",
                "alertCount": 1,
            },
            "alerts": [
                {
                    "id": "a1",
                    "title": "CPU high",
                    "severity": "critical",
                    "assetId": "asset_1",
                    "fingerprint": "fp_cpu",
                }
            ],
            "rca": {
                "id": "rca_1",
                "suspectedRootCause": "CPU saturation",
                "confidence": 0.8,
                "summary": "RCA summary",
            },
            "locale": "zh-CN",
            "traceId": "trace_1",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["agentName"] == "aegisops_diagnosis_graph"
    assert body["summary"]
    assert body["rootCause"]
    assert body["nextSteps"]
    assert body["risks"]
