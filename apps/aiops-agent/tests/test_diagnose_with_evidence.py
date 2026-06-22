from __future__ import annotations

import pytest

from aiops_agent.schemas import (
    DiagnoseRequest,
    EvidenceContext,
    IncidentContext,
)
from aiops_agent.service import DiagnosisService
from aiops_agent.settings import Settings


@pytest.mark.asyncio
async def test_service_deterministic_mode_uses_evidence():
    settings = Settings(generation_mode="deterministic")
    service = DiagnosisService(settings)

    request = DiagnoseRequest(
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1", title="order-service 主机与服务异常"),
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
        ],
        traceId="trace_1",
    )

    response = await service.diagnose(request)

    assert response.provider == "aiops-agent"
    assert response.raw["generationMode"] == "deterministic-evidence"
    assert "evd_cpu" in response.raw["evidenceRefs"]


@pytest.mark.asyncio
async def test_service_mock_mode_also_uses_evidence():
    settings = Settings(generation_mode="mock")
    service = DiagnosisService(settings)

    request = DiagnoseRequest(
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1", title="order-service 主机与服务异常"),
        evidence=[
            EvidenceContext(
                id="evd_1",
                evidenceKey="evd_cpu",
                evidenceType="metric_cpu_high",
                title="CPU 使用率持续高位",
                summary="CPU 最大值 96%",
            ),
        ],
        traceId="trace_1",
    )

    response = await service.diagnose(request)

    assert response.raw["generationMode"] == "deterministic-evidence"
    assert "evd_cpu" in response.raw["evidenceRefs"]
