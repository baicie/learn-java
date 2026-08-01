import logging

from fastapi.testclient import TestClient

from aiops_agent.main import app, service_authenticator
from aiops_agent.settings import settings

client = TestClient(app)


class FakeServiceDecoder:
    async def decode(self, token: str) -> dict[str, object]:
        assert token == "test-service-token"
        return {
            "sub": "svc:aiops-server",
            "aud": ["aiops-agent-api"],
            "scope": "agent:diagnose agent:resume agent:work-record",
            "iat": 1_000,
            "exp": 1_300,
        }


service_authenticator.decoder = FakeServiceDecoder()
SERVICE_HEADERS = {
    "Authorization": "Bearer test-service-token",
    "X-AegisOps-Diagnosis-Grant": "diagnosis-grant",
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


def test_service_auth_probe_requires_valid_scoped_service_identity():
    response = client.post(
        "/v1/auth/probe",
        headers={"Authorization": "Bearer test-service-token"},
    )

    assert response.status_code == 200
    assert response.json() == {
        "ok": True,
        "serviceId": "svc:aiops-server",
    }

    missing = client.post("/v1/auth/probe")
    assert missing.status_code == 401


def test_diagnosis_endpoints_publish_service_auth_security_contract():
    schema = client.get("/openapi.json").json()

    assert schema["components"]["securitySchemes"]["HTTPBearer"] == {
        "type": "http",
        "scheme": "bearer",
        "bearerFormat": "JWT",
    }
    assert schema["components"]["securitySchemes"]["DiagnosisGrant"] == {
        "type": "apiKey",
        "in": "header",
        "name": "X-AegisOps-Diagnosis-Grant",
    }

    for path in ("/v1/diagnose", "/v1/diagnose/resume"):
        operation = schema["paths"][path]["post"]
        assert operation["security"] == [
            {"HTTPBearer": [], "DiagnosisGrant": []},
        ]
        assert {"401", "403", "503"} <= operation["responses"].keys()

    work_record = schema["paths"]["/v1/work-record/generate"]["post"]
    assert work_record["security"] == [{"HTTPBearer": []}]
    assert {"401", "403", "503"} <= work_record["responses"].keys()

    auth_probe = schema["paths"]["/v1/auth/probe"]["post"]
    assert auth_probe["security"] == [{"HTTPBearer": []}]
    assert {"401", "403", "503"} <= auth_probe["responses"].keys()


def test_diagnose_rejects_missing_token(caplog):
    with caplog.at_level(logging.WARNING):
        response = client.post(
            "/v1/diagnose",
            headers={"X-AegisOps-Diagnosis-Grant": "diagnosis-grant"},
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
    records = [
        record
        for record in caplog.records
        if getattr(record, "eventType", None) == "internal_auth_failed"
    ]
    assert len(records) == 1
    assert "diagnosis-grant" not in caplog.text


def test_diagnose_rejects_missing_diagnosis_grant(caplog):
    with caplog.at_level(logging.WARNING):
        response = client.post(
            "/v1/diagnose",
            headers={"Authorization": "Bearer test-service-token"},
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
    assert record.serviceId == "svc:aiops-server"
    assert "test-service-token" not in caplog.text


def test_diagnose_rejects_blank_diagnosis_grant():
    response = client.post(
        "/v1/diagnose",
        headers={
            "Authorization": "Bearer test-service-token",
            "X-AegisOps-Diagnosis-Grant": " \t ",
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

    assert response.status_code == 401


def test_diagnose_rejects_wrong_contract_header():
    response = client.post(
        "/v1/diagnose",
        headers={
            **SERVICE_HEADERS,
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
            **SERVICE_HEADERS,
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
    assert body["raw"]["workflow"]["graphVersion"] == "phase8.0-saas-tenant-hardening"


def test_diagnose_resume_rejects_missing_token():
    response = client.post(
        "/v1/diagnose/resume",
        headers={"X-AegisOps-Diagnosis-Grant": "diagnosis-grant"},
        json={
            "tenant_id": "tenant_1",
            "checkpoint_id": "agcp_1",
        },
    )

    assert response.status_code == 401


def test_diagnose_resume_rejects_missing_diagnosis_grant():
    response = client.post(
        "/v1/diagnose/resume",
        headers={"Authorization": "Bearer test-service-token"},
        json={
            "tenant_id": "tenant_1",
            "checkpoint_id": "agcp_1",
        },
    )

    assert response.status_code == 401


def test_diagnose_resume_rejects_blank_diagnosis_grant():
    response = client.post(
        "/v1/diagnose/resume",
        headers={
            "Authorization": "Bearer test-service-token",
            "X-AegisOps-Diagnosis-Grant": " \t ",
        },
        json={
            "tenant_id": "tenant_1",
            "checkpoint_id": "agcp_1",
        },
    )

    assert response.status_code == 401
