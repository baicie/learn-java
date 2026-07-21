import asyncio
import json
from datetime import UTC, date, datetime

import httpx
import respx

from aiops_agent.schemas import WorkRecordGenerateRequest, WorkRecordItem
from aiops_agent.settings import Settings, settings
from aiops_agent.work_record_generation import WorkRecordGenerationService


def test_record_summary_contains_visible_fields() -> None:
    response = asyncio.run(WorkRecordGenerationService(settings).generate(
        WorkRecordGenerateRequest(
            generationType="record_summary",
            tenantId="tenant-1",
            resourceId="record-1",
            records=[
                WorkRecordItem(
                    id="record-1",
                    title="日报",
                    status="done",
                    recordTime=datetime(2026, 7, 14, tzinfo=UTC),
                    fields={"result": "发布完成"},
                )
            ],
            traceId="trace-1",
        )))
    assert "发布完成" in response.markdown
    assert response.provider == "deterministic"


def test_monthly_report_counts_records() -> None:
    response = asyncio.run(WorkRecordGenerationService(settings).generate(
        WorkRecordGenerateRequest(
            generationType="monthly_report",
            tenantId="tenant-1",
            resourceId="2026-07",
            periodStart=date(2026, 7, 1),
            periodEnd=date(2026, 7, 31),
            records=[
                WorkRecordItem(
                    id="record-1",
                    title="日报",
                    status="done",
                    recordTime=datetime(2026, 7, 14, tzinfo=UTC),
                )
            ],
            traceId="trace-2",
        )))
    assert "记录总数：1" in response.markdown
    assert "done：1" in response.markdown


def _dify_settings(**overrides: object) -> Settings:
    values = {
        "work_record_provider": "dify",
        "dify_base_url": "https://dify.example.com/v1",
        "dify_work_record_api_key": "api-secret",
        "dify_work_record_workflow_version": "version-1",
        "dify_user_hmac_secret": "hmac-secret",
        "dify_max_retries": 0,
    }
    values.update(overrides)
    return Settings(**values)


def _monthly_request(**overrides: object) -> WorkRecordGenerateRequest:
    values = {
        "generationType": "monthly_report",
        "tenantId": "tenant-1",
        "resourceId": "2026-07",
        "actorId": "user-1",
        "periodStart": date(2026, 7, 1),
        "periodEnd": date(2026, 7, 31),
        "promptVersion": "work-record-monthly-v2",
        "statistics": {"recordCount": 42},
        "traceId": "trace-1",
    }
    values.update(overrides)
    return WorkRecordGenerateRequest(**values)


def _dify_success() -> dict[str, object]:
    return {
        "workflow_run_id": "run-1",
        "data": {
            "id": "run-1",
            "workflow_id": "workflow-1",
            "status": "succeeded",
            "outputs": {
                "markdown": "# Dify 工作月报",
                "warnings": ["需人工复核"],
                "workflow_version": "version-1",
            },
            "elapsed_time": 1.25,
            "total_tokens": 321,
        },
    }


@respx.mock
def test_dify_provider_routes_request_and_returns_trace_metadata() -> None:
    route = respx.post("https://dify.example.com/v1/workflows/run").mock(
        return_value=httpx.Response(200, json=_dify_success())
    )

    response = asyncio.run(
        WorkRecordGenerationService(_dify_settings()).generate(_monthly_request())
    )

    assert response.provider == "dify"
    assert response.model == "dify-workflow"
    assert response.markdown == "# Dify 工作月报"
    assert response.warnings == ["需人工复核"]
    assert response.providerRunId == "run-1"
    assert response.providerWorkflowId == "workflow-1"
    assert response.providerWorkflowVersion == "version-1"
    assert response.providerDurationMs == 1250
    assert response.providerTotalTokens == 321
    assert response.fallbackReason is None
    assert response.raw == {
        "recordCount": 0,
        "generationType": "monthly_report",
        "inputBytes": response.raw["inputBytes"],
        "providerRunId": "run-1",
        "providerWorkflowId": "workflow-1",
        "providerWorkflowVersion": "version-1",
        "providerDurationMs": 1250,
        "providerTotalTokens": 321,
        "fallbackReason": None,
    }
    sent = json.loads(route.calls.last.request.content)
    context = json.loads(sent["inputs"]["report_context_json"])
    assert context["statistics"] == {"recordCount": 42}
    assert context["periodStart"] == "2026-07-01"
    assert "tenantId" not in context
    assert "actorId" not in context
    assert "traceId" not in context


@respx.mock
def test_dify_failure_returns_explicit_deterministic_fallback() -> None:
    route = respx.post("https://dify.example.com/v1/workflows/run").mock(
        return_value=httpx.Response(401, json={"message": "unauthorized"})
    )

    response = asyncio.run(
        WorkRecordGenerationService(_dify_settings()).generate(_monthly_request())
    )

    assert response.provider == "deterministic"
    assert response.model == "langgraph-deterministic"
    assert "记录总数：0" in response.markdown
    assert response.warnings == ["Dify 生成失败，已使用确定性模板。"]
    assert response.fallbackReason == "http_401"
    assert response.providerRunId is None
    assert response.providerWorkflowId is None
    assert response.providerWorkflowVersion == "version-1"
    assert response.providerDurationMs is None
    assert response.providerTotalTokens is None
    assert response.raw["fallbackReason"] == "http_401"
    assert response.raw["requestedProvider"] == "dify"
    assert response.raw["providerRunId"] is None
    assert response.raw["providerWorkflowId"] is None
    assert response.raw["providerWorkflowVersion"] == "version-1"
    assert response.raw["providerDurationMs"] is None
    assert response.raw["providerTotalTokens"] is None
    assert route.call_count == 1


@respx.mock
def test_dify_input_over_budget_falls_back_without_external_call() -> None:
    route = respx.post("https://dify.example.com/v1/workflows/run").mock(
        return_value=httpx.Response(200, json=_dify_success())
    )
    request = _monthly_request(statistics={"content": "中" * 128})

    response = asyncio.run(
        WorkRecordGenerationService(
            _dify_settings(dify_max_input_bytes=64)
        ).generate(request)
    )

    assert response.provider == "deterministic"
    assert response.fallbackReason == "input_too_large"
    assert response.warnings == ["Dify 输入超过 64 字节，已使用确定性模板。"]
    assert response.raw["inputBytes"] > 64
    assert not route.called
