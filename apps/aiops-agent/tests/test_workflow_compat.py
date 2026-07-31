from __future__ import annotations

from datetime import datetime, timezone

from aiops_agent.schemas import AlertContext, DiagnoseRequest, IncidentContext, RcaContext
from aiops_agent.settings import Settings
from aiops_agent.workflow.compat import to_workflow_request


def test_to_workflow_request_derives_tags_from_incident_and_alerts():
    request = DiagnoseRequest(
        contractVersion="agent-diagnosis.v1",
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(
            id="inc_1",
            title="Order service timeout",
            severity="critical",
            status="open",
            source="zabbix",
            primaryAssetId="svc_order",
            aggregationKey="zabbix:svc_order:timeout",
            startedAt=datetime(2026, 7, 31, 4, 0, tzinfo=timezone.utc),
            lastSeenAt=datetime(2026, 7, 31, 4, 5, tzinfo=timezone.utc),
        ),
        alerts=[
            AlertContext(
                id="alert_1",
                source="zabbix",
                severity="high",
                assetId="svc_order",
                entityType="service",
                entityName="order-service",
                fingerprint="redis-timeout",
                title="Redis timeout",
            )
        ],
        rca=RcaContext(
            id="rca_1",
            suspectedRootCause="redis timeout",
            modelVersion="mock-rca",
            summary="RCA summary",
        ),
        locale="zh-CN",
        traceId="trace_1",
    )

    workflow_request = to_workflow_request(request, Settings())

    assert workflow_request.trace_id == "trace_1"
    assert workflow_request.primary_asset_id == "svc_order"
    assert workflow_request.started_at == datetime(2026, 7, 31, 4, 0, tzinfo=timezone.utc)
    assert workflow_request.last_seen_at == datetime(2026, 7, 31, 4, 5, tzinfo=timezone.utc)
    assert "zabbix" in workflow_request.tags
    assert "svc-order" in workflow_request.tags
    assert "service" in workflow_request.tags
    assert "order-service" in workflow_request.tags
    assert "redis-timeout" in workflow_request.tags
    assert "has-rca" in workflow_request.tags


def test_to_workflow_request_deduplicates_and_normalizes_tags():
    request = DiagnoseRequest(
        contractVersion="agent-diagnosis.v1",
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(
            id="inc_1",
            source="Zabbix",
            primaryAssetId="svc.order",
            severity="warning",
        ),
        alerts=[
            AlertContext(
                id="alert_1",
                source="zabbix",
                assetId="svc.order",
                severity="warning",
            )
        ],
        locale="zh-CN",
        traceId="trace_1",
    )

    workflow_request = to_workflow_request(request, Settings())

    assert workflow_request.tags.count("zabbix") == 1
    assert "svc-order" in workflow_request.tags
    assert "warning" in workflow_request.tags
