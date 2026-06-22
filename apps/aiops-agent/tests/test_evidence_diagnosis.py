from __future__ import annotations

from datetime import datetime

from aiops_agent.evidence_diagnosis import deterministic_diagnose
from aiops_agent.schemas import (
    AlertContext,
    DiagnoseRequest,
    EvidenceContext,
    IncidentContext,
    RcaContext,
)


def test_deterministic_diagnosis_uses_cpu_api_health_evidence():
    request = DiagnoseRequest(
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(
            id="inc_1",
            title="order-service 主机与服务异常",
            severity="critical",
            status="open",
        ),
        alerts=[
            AlertContext(id="a1", title="CPU High", severity="high", entityName="order-service"),
            AlertContext(id="a2", title="API Slow", severity="medium", entityName="order-service"),
            AlertContext(
                id="a3",
                title="Health Check Failed",
                severity="critical",
                entityName="order-service",
            ),
        ],
        evidence=[
            EvidenceContext(
                id="evd_1",
                evidenceKey="evd_cpu",
                evidenceType="metric_cpu_high",
                title="CPU 使用率持续高位",
                summary="CPU 最大值 96%",
            ),
            EvidenceContext(
                id="evd_2",
                evidenceKey="evd_api",
                evidenceType="metric_api_slow",
                title="接口响应时间明显升高",
                summary="接口响应时间最大值 2.50s",
            ),
            EvidenceContext(
                id="evd_3",
                evidenceKey="evd_health",
                evidenceType="metric_health_check_failed",
                title="健康检查失败",
                summary="/health 连续失败",
            ),
        ],
        rca=RcaContext(
            id="rca_1",
            suspectedRootCause="主机 CPU 持续高位导致服务响应变慢，并进一步引发健康检查失败",
            confidence=0.88,
            summary="RCA matched 3 rules",
            matchedRules=["CPU_API_HEALTH_COMBINED"],
            evidenceRefs=["evd_cpu", "evd_api", "evd_health"],
        ),
        traceId="trace_1",
    )

    response = deterministic_diagnose(request)

    assert "CPU" in response.summary
    assert "健康检查失败" in response.rootCause
    assert "evd_cpu" in response.raw["evidenceRefs"]
    assert "CPU_API_HEALTH_COMBINED" in response.raw["matchedRules"]
    assert response.nextSteps


def test_deterministic_diagnosis_builds_timeline_from_evidence():
    request = DiagnoseRequest(
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1", title="Test service"),
        evidence=[
            EvidenceContext(
                id="evd_1",
                evidenceKey="evd_cpu",
                evidenceType="metric_cpu_high",
                title="CPU 使用率持续高位",
                summary="CPU 最大值 96%",
                timeRangeStart=datetime(2026, 6, 21, 5, 0, 0),
            ),
        ],
        traceId="trace_1",
    )

    response = deterministic_diagnose(request)

    timeline = response.raw["timeline"]
    assert len(timeline) == 1
    assert timeline[0]["evidenceRef"] == "evd_cpu"
    assert timeline[0]["type"] == "metric_cpu_high"


def test_deterministic_diagnosis_infers_rules_from_evidence_when_rca_has_no_rules():
    request = DiagnoseRequest(
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1", title="Test service"),
        evidence=[
            EvidenceContext(
                id="evd_1",
                evidenceKey="evd_cpu",
                evidenceType="metric_cpu_high",
                title="CPU 高",
                summary="CPU 96%",
            ),
            EvidenceContext(
                id="evd_2",
                evidenceKey="evd_api",
                evidenceType="metric_api_slow",
                title="API 慢",
                summary="API 2.5s",
            ),
        ],
        rca=RcaContext(
            id="rca_1",
            suspectedRootCause="CPU 饱和",
            confidence=0.80,
            matchedRules=[],
            evidenceRefs=[],
        ),
        traceId="trace_1",
    )

    response = deterministic_diagnose(request)

    assert "HOST_CPU_HIGH_WITH_SERVICE_SLOW" in response.raw["matchedRules"]


def test_deterministic_diagnosis_adds_combined_rule_when_all_three_present():
    request = DiagnoseRequest(
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1", title="Test service"),
        evidence=[
            EvidenceContext(
                id="evd_1",
                evidenceKey="evd_cpu",
                evidenceType="metric_cpu_high",
                title="CPU 高",
                summary="CPU 96%",
            ),
            EvidenceContext(
                id="evd_2",
                evidenceKey="evd_api",
                evidenceType="metric_api_slow",
                title="API 慢",
                summary="API 2.5s",
            ),
            EvidenceContext(
                id="evd_3",
                evidenceKey="evd_health",
                evidenceType="metric_health_check_failed",
                title="Health 失败",
                summary="/health 失败",
            ),
        ],
        traceId="trace_1",
    )

    response = deterministic_diagnose(request)

    rules = response.raw["matchedRules"]
    assert "CPU_API_HEALTH_COMBINED" in rules
    assert rules[0] == "CPU_API_HEALTH_COMBINED"
