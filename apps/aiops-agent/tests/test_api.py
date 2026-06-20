from fastapi.testclient import TestClient

from aiops_agent.main import app
from aiops_agent.settings import settings

client = TestClient(app)


def test_health_includes_contract_version():
    response = client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["ok"] is True
    assert body["contractVersion"] == settings.contract_version
    assert body["generationMode"] == settings.normalized_generation_mode()


def test_contract_endpoint_returns_schemas():
    response = client.get("/v1/contracts/diagnosis")

    assert response.status_code == 200
    body = response.json()
    assert body["contractVersion"] == settings.contract_version
    assert body["requestSchema"]["title"] == "AegisOps Agent Diagnosis Request"
    assert body["responseSchema"]["title"] == "AegisOps Agent Diagnosis Response"


def test_diagnose_rejects_missing_token():
    response = client.post(
        "/v1/diagnose",
        json={
            "contractVersion": "agent-diagnosis.v1",
            "tenantId": "tenant_1",
            "incidentId": "inc_1",
            "incident": {"id": "inc_1"},
            "alerts": [],
            "locale": "zh-CN",
            "traceId": "trace_1",
        },
    )

    assert response.status_code == 401


def test_diagnose_rejects_wrong_contract_header():
    response = client.post(
        "/v1/diagnose",
        headers={
            "X-AegisOps-Internal-Token": settings.internal_token,
            "X-AegisOps-Contract-Version": "bad",
        },
        json={
            "contractVersion": "agent-diagnosis.v1",
            "tenantId": "tenant_1",
            "incidentId": "inc_1",
            "incident": {"id": "inc_1"},
            "alerts": [],
            "locale": "zh-CN",
            "traceId": "trace_1",
        },
    )

    assert response.status_code == 400


def test_diagnose_returns_contract_response():
    response = client.post(
        "/v1/diagnose",
        headers={
            "X-AegisOps-Internal-Token": settings.internal_token,
            "X-AegisOps-Contract-Version": settings.contract_version,
        },
        json={
            "contractVersion": "agent-diagnosis.v1",
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
    assert body["contractVersion"] == "agent-diagnosis.v1"
    assert body["agentName"] == "aegisops_diagnosis_graph"
    assert body["raw"]["traceId"] == "trace_1"
    assert body["raw"]["safety"]["autoExecutionAllowed"] is False
    assert body["raw"]["workflow"]["graphVersion"] == "phase7.3-agent-memory"


def test_diagnose_resume_rejects_missing_token():
    response = client.post(
        "/v1/diagnose/resume",
        json={
            "tenant_id": "tenant_1",
            "checkpoint_id": "agcp_1",
        },
    )

    assert response.status_code == 401
