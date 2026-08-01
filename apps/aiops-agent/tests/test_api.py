import logging

from fastapi.testclient import TestClient

from aiops_agent import main as main_module
from aiops_agent.main import app
from aiops_agent.settings import settings

client = TestClient(app)
DIAGNOSIS_HEADERS = {"X-AegisOps-Diagnosis-Grant": "diagnosis-grant"}


def diagnosis_request() -> dict:
    return {
        "contractVersion": "agent-diagnosis.v1",
        "tenantId": "tenant_1",
        "incidentId": "inc_1",
        "diagnosisId": "diag_1",
        "incident": {"id": "inc_1"},
        "alerts": [],
        "locale": "zh-CN",
        "traceId": "trace_1",
    }


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


def test_internal_endpoints_publish_mtls_and_task_grant_security_contract():
    schema = client.get("/openapi.json").json()

    assert schema["components"]["securitySchemes"]["WorkloadMtls"] == {
        "type": "mutualTLS"
    }
    assert schema["components"]["securitySchemes"]["DiagnosisGrant"] == {
        "type": "apiKey",
        "in": "header",
        "name": "X-AegisOps-Diagnosis-Grant",
    }
    assert "HTTPBearer" not in schema["components"]["securitySchemes"]

    for path in ("/v1/diagnose", "/v1/diagnose/resume"):
        operation = schema["paths"][path]["post"]
        assert operation["security"] == [
            {"WorkloadMtls": [], "DiagnosisGrant": []},
        ]
        assert {"401", "403"} <= operation["responses"].keys()

    work_record = schema["paths"]["/v1/work-record/generate"]["post"]
    assert work_record["security"] == [{"WorkloadMtls": []}]
    assert "/v1/auth/probe" not in schema["paths"]


def test_diagnose_rejects_missing_diagnosis_grant(caplog):
    with caplog.at_level(logging.WARNING):
        response = client.post("/v1/diagnose", json=diagnosis_request())

    assert response.status_code == 401
    records = [
        record
        for record in caplog.records
        if getattr(record, "eventType", None) == "diagnosis_grant_missing"
    ]
    assert len(records) == 1
    record = records[0]
    assert record.severity == "critical"
    assert record.httpStatus == 401
    assert record.requestPath == "/v1/diagnose"


def test_diagnose_rejects_blank_diagnosis_grant():
    response = client.post(
        "/v1/diagnose",
        headers={"X-AegisOps-Diagnosis-Grant": " \t "},
        json=diagnosis_request(),
    )

    assert response.status_code == 401


def test_diagnose_rejects_wrong_contract_header():
    response = client.post(
        "/v1/diagnose",
        headers={
            **DIAGNOSIS_HEADERS,
            "X-AegisOps-Contract-Version": "bad",
        },
        json=diagnosis_request(),
    )

    assert response.status_code == 400


def test_diagnose_returns_contract_response_without_bearer_header():
    payload = diagnosis_request()
    payload.update(
        {
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
        }
    )
    response = client.post(
        "/v1/diagnose",
        headers={
            **DIAGNOSIS_HEADERS,
            "X-AegisOps-Contract-Version": settings.contract_version,
        },
        json=payload,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["contractVersion"] == "agent-diagnosis.v1"
    assert body["agentName"] == "aegisops_diagnosis_graph"
    assert body["raw"]["traceId"] == "trace_1"
    assert body["raw"]["safety"]["autoExecutionAllowed"] is False
    assert body["raw"]["workflow"]["graphVersion"] == "phase8.0-saas-tenant-hardening"


def test_diagnose_resume_rejects_missing_diagnosis_grant():
    response = client.post(
        "/v1/diagnose/resume",
        json={"tenant_id": "tenant_1", "checkpoint_id": "agcp_1"},
    )

    assert response.status_code == 401


def test_diagnose_resume_rejects_blank_diagnosis_grant():
    response = client.post(
        "/v1/diagnose/resume",
        headers={"X-AegisOps-Diagnosis-Grant": " \t "},
        json={"tenant_id": "tenant_1", "checkpoint_id": "agcp_1"},
    )

    assert response.status_code == 401


def test_diagnose_resume_binds_grant_to_complete_task_context(monkeypatch):
    captured = {}

    def verify(token, **kwargs):
        captured.update(kwargs)
        return {"sub": "diagnosis:diag_1", "jti": "test"}

    class ResumeService:
        async def resume(self, request):
            return {
                "tenant_id": request.tenant_id,
                "incident_id": request.incident_id,
                "summary": "resumed",
                "root_cause": "unknown",
                "confidence": 0.1,
                "severity": "medium",
                "risk_level": "low",
            }

    monkeypatch.setattr("aiops_agent.main.diagnosis_grant_verifier.verify", verify)
    app.dependency_overrides[main_module.diagnosis_service] = ResumeService
    try:
        response = client.post(
            "/v1/diagnose/resume",
            headers=DIAGNOSIS_HEADERS,
            json={
                "tenant_id": "tenant_1",
                "incident_id": "inc_1",
                "diagnosis_id": "diag_1",
                "trace_id": "trace_1",
                "checkpoint_id": "agcp_1",
            },
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    assert captured == {
        "required_scope": "diagnosis:resume",
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "diagnosis_id": "diag_1",
        "trace_id": "trace_1",
    }
