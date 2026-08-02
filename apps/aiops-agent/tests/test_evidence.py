from aiops_agent.evidence import (
    DisabledEvidenceClient,
    HttpEvidenceClient,
    build_evidence_query_payload,
    unavailable_bundle,
)
from aiops_agent.observability.context import diagnosis_grant_var
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

    class FakeHttpxClient:
        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc_value, traceback):
            return None

        def post(self, url, headers, json):
            captured["url"] = url
            captured["headers"] = headers
            captured["json"] = json
            return FakeHttpxResponse(
                {
                    "metrics": {"available": True, "series": []},
                    "logs": {"available": True, "patterns": []},
                    "changes": {"available": True, "events": []},
                }
            )

    def fake_client(*, timeout, trust_env):
        captured["timeout"] = timeout
        captured["trust_env"] = trust_env
        return FakeHttpxClient()

    monkeypatch.setattr("aiops_agent.evidence.httpx.Client", fake_client)

    settings = Settings(
        evidence_enabled=True,
        evidence_base_url="http://server:8080/internal/agent/evidence/",
        evidence_timeout_seconds=3,
    )

    token = diagnosis_grant_var.set("diagnosis-grant")
    try:
        bundle = HttpEvidenceClient(settings).query(request())
    finally:
        diagnosis_grant_var.reset(token)

    assert captured["url"] == "http://server:8080/internal/agent/evidence/query"
    assert "Authorization" not in captured["headers"]
    assert captured["headers"]["X-AegisOps-Diagnosis-Grant"] == "diagnosis-grant"
    assert captured["headers"]["X-Tenant-Id"] == "tenant_1"
    assert captured["json"]["traceId"] == "trace_1"
    assert captured["json"]["serviceNames"] == ["checkout-service"]
    assert captured["timeout"] == 3
    assert captured["trust_env"] is False
    assert bundle.metrics["available"] is True


def test_http_evidence_client_falls_back_on_error(monkeypatch):
    class FailingHttpxClient:
        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc_value, traceback):
            return None

        def post(self, url, headers, json):
            raise RuntimeError("server down")

    monkeypatch.setattr(
        "aiops_agent.evidence.httpx.Client",
        lambda *, timeout, trust_env: FailingHttpxClient(),
    )
    settings = Settings(
        evidence_enabled=True,
        evidence_base_url="http://server:8080/internal/agent/evidence",
    )

    token = diagnosis_grant_var.set("diagnosis-grant")
    try:
        bundle = HttpEvidenceClient(settings).query(request())
    finally:
        diagnosis_grant_var.reset(token)

    assert bundle.metrics["available"] is False
    assert "server down" in bundle.metrics["reason"]


def test_http_evidence_client_rejects_blank_context_grant_before_request(monkeypatch):
    called = False

    class UnexpectedHttpxClient:
        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc_value, traceback):
            return None

        def post(self, url, headers, json):
            nonlocal called
            called = True
            return FakeHttpxResponse({})

    monkeypatch.setattr(
        "aiops_agent.evidence.httpx.Client",
        lambda *, timeout, trust_env: UnexpectedHttpxClient(),
    )
    settings = Settings(
        evidence_enabled=True,
        evidence_base_url="http://server:8080/internal/agent/evidence",
    )
    token = diagnosis_grant_var.set(" \t ")

    try:
        bundle = HttpEvidenceClient(settings).query(request())
    finally:
        diagnosis_grant_var.reset(token)

    assert called is False
    assert bundle.metrics["available"] is False
    assert "ValueError" in bundle.metrics["reason"]
