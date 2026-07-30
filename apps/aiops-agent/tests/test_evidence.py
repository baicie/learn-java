from aiops_agent.evidence import (
    DisabledEvidenceClient,
    HttpEvidenceClient,
    build_evidence_query_payload,
    unavailable_bundle,
)
from aiops_agent.schemas import AlertContext, DiagnoseRequest, IncidentContext
from aiops_agent.settings import Settings


class FakeHttpxResponse:
    def __init__(self, payload: dict):
        self.payload = payload

    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict:
        return self.payload


def request() -> DiagnoseRequest:
    return DiagnoseRequest(
        contractVersion="agent-diagnosis.v1",
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(
            id="inc_1",
            primaryAssetId="asset_1",
            startedAt="2026-06-16T10:00:00+09:00",
            lastSeenAt="2026-06-16T10:10:00+09:00",
        ),
        alerts=[
            AlertContext(
                id="a1",
                title="CPU high",
                fingerprint="fp_cpu",
                entityType="service",
                entityName="checkout-service",
            ),
            AlertContext(
                id="a2",
                title="Host CPU high",
                fingerprint="fp_host",
                entityType="host",
                entityName="host-1",
            ),
        ],
        traceId="trace_1",
    )


def test_disabled_evidence_client_returns_unavailable_bundle():
    bundle = DisabledEvidenceClient().query(request())

    assert bundle.metrics["available"] is False
    assert bundle.logs["available"] is False
    assert bundle.changes["available"] is False


def test_unavailable_bundle_sets_all_sections():
    bundle = unavailable_bundle("failed")

    assert bundle.metrics["reason"] == "failed"
    assert bundle.logs["reason"] == "failed"
    assert bundle.changes["reason"] == "failed"


def test_build_evidence_query_payload():
    payload = build_evidence_query_payload(request())

    assert payload["tenantId"] == "tenant_1"
    assert payload["incidentId"] == "inc_1"
    assert payload["primaryAssetId"] == "asset_1"
    assert payload["alertFingerprints"] == ["fp_cpu", "fp_host"]
    assert payload["alertTitles"] == ["CPU high", "Host CPU high"]
    assert payload["serviceNames"] == ["checkout-service"]


def test_http_evidence_client_posts_internal_request(monkeypatch):
    captured = {}

    def fake_post(url, headers, json, timeout):
        captured["url"] = url
        captured["headers"] = headers
        captured["json"] = json
        captured["timeout"] = timeout
        return FakeHttpxResponse(
            {
                "metrics": {"available": True, "series": []},
                "logs": {"available": True, "patterns": []},
                "changes": {"available": True, "events": []},
            }
        )

    monkeypatch.setattr("aiops_agent.evidence.httpx.post", fake_post)

    settings = Settings(
        evidence_enabled=True,
        evidence_base_url="http://server:8080/internal/agent/evidence/",
        internal_agent_token="java-to-agent-token",
        outbound_static_token="agent-to-java-token",
        evidence_timeout_seconds=3,
    )

    bundle = HttpEvidenceClient(settings).query(request())

    assert captured["url"] == "http://server:8080/internal/agent/evidence/query"
    assert captured["headers"]["X-AIOPS-INTERNAL-TOKEN"] == "agent-to-java-token"
    assert captured["headers"]["X-Tenant-Id"] == "tenant_1"
    assert captured["json"]["traceId"] == "trace_1"
    assert captured["json"]["serviceNames"] == ["checkout-service"]
    assert captured["timeout"] == 3
    assert bundle.metrics["available"] is True


def test_http_evidence_client_falls_back_on_error(monkeypatch):
    def fake_post(url, headers, json, timeout):
        raise RuntimeError("server down")

    monkeypatch.setattr("aiops_agent.evidence.httpx.post", fake_post)

    settings = Settings(
        evidence_enabled=True,
        evidence_base_url="http://server:8080/internal/agent/evidence",
    )

    bundle = HttpEvidenceClient(settings).query(request())

    assert bundle.metrics["available"] is False
    assert "server down" in bundle.metrics["reason"]
