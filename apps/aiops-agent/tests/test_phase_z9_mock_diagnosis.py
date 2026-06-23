from __future__ import annotations

import pytest

from aiops_agent.schemas import DiagnoseRequest, EvidenceContext, IncidentContext, RcaContext
from aiops_agent.service import DiagnosisService
from aiops_agent.settings import Settings


@pytest.mark.asyncio
async def test_phase_z9_mock_ai_diagnosis_uses_evidence_and_rca():
    service = DiagnosisService(Settings(generation_mode="deterministic"))

    request = DiagnoseRequest(
        tenantId="tenant_z9",
        incidentId="inc_z9",
        incident=IncidentContext(
            id="inc_z9",
            title="order-service 主机与服务异常",
            severity="critical",
            status="open",
        ),
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
            id="rca_z9",
            suspectedRootCause="主机 CPU 持续高位导致服务响应变慢，并进一步引发健康检查失败",
            confidence=0.88,
            summary="RCA matched CPU_API_HEALTH_COMBINED",
            matchedRules=["CPU_API_HEALTH_COMBINED"],
            evidenceRefs=["evd_cpu", "evd_api", "evd_health"],
        ),
        traceId="trace_z9",
    )

    response = await service.diagnose(request)

    assert "CPU" in response.summary
    assert "健康检查失败" in response.rootCause
    assert "evd_cpu" in response.raw.get("evidenceRefs", [])
    assert "CPU_API_HEALTH_COMBINED" in response.raw.get("matchedRules", [])
    assert response.nextSteps
