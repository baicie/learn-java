import hashlib
import hmac
import json
from importlib.util import find_spec

import httpx
import pytest
import respx

import aiops_agent.dify_workflow as dify
from aiops_agent.schemas import WorkRecordGenerateRequest
from aiops_agent.settings import Settings


def test_dify_workflow_module_is_available() -> None:
    assert find_spec("aiops_agent.dify_workflow") is not None


async def _no_sleep(_: float) -> None:
    return None


def _settings(**overrides: object) -> Settings:
    values = {
        "work_record_provider": "dify",
        "dify_base_url": "https://dify.example.com/v1",
        "dify_work_record_api_key": "api-secret",
        "dify_work_record_workflow_version": "version-1",
        "dify_user_hmac_secret": "hmac-secret",
        "dify_max_retries": 2,
    }
    values.update(overrides)
    return Settings(**values)


def _request(actor_id: str | None = "user-1") -> WorkRecordGenerateRequest:
    return WorkRecordGenerateRequest(
        generationType="monthly_report",
        tenantId="tenant-1",
        resourceId="2026-07",
        actorId=actor_id,
        promptVersion="work-record-monthly-v2",
        traceId="trace-1",
    )


def _success_response(**overrides: object) -> dict[str, object]:
    data: dict[str, object] = {
        "id": "run-1",
        "workflow_id": "workflow-1",
        "status": "succeeded",
        "outputs": {
            "markdown": "# 工作月报",
            "warnings": ["需人工复核"],
            "workflow_version": "version-1",
        },
        "elapsed_time": 1.25,
        "total_tokens": 321,
    }
    data.update(overrides)
    return {"workflow_run_id": "run-1", "data": data}


@respx.mock
@pytest.mark.asyncio
async def test_run_maps_blocking_response_and_uses_hmac_user() -> None:
    route = respx.post("https://dify.example.com/v1/workflows/run").mock(
        return_value=httpx.Response(200, json=_success_response())
    )

    result = await dify.DifyWorkflowClient(_settings(), sleep=_no_sleep).run(
        _request(), '{"facts":{"recordCount":42}}'
    )

    assert result.markdown == "# 工作月报"
    assert result.provider == "dify"
    assert result.model == "dify-workflow"
    assert result.warnings == ["需人工复核"]
    assert result.run_id == "run-1"
    assert result.workflow_id == "workflow-1"
    assert result.workflow_version == "version-1"
    assert result.elapsed_ms == 1250
    assert result.total_tokens == 321
    sent = json.loads(route.calls.last.request.content)
    expected_user = hmac.new(
        b"hmac-secret", b"tenant-1:user-1", hashlib.sha256
    ).hexdigest()
    assert sent == {
        "inputs": {
            "generation_type": "monthly_report",
            "report_context_json": '{"facts":{"recordCount":42}}',
            "locale": "zh-CN",
            "prompt_version": "work-record-monthly-v2",
            "trace_id": "trace-1",
        },
        "response_mode": "blocking",
        "user": expected_user,
    }
    assert route.calls.last.request.headers["Authorization"] == "Bearer api-secret"


@respx.mock
@pytest.mark.asyncio
async def test_run_uses_fixed_published_workflow_endpoint() -> None:
    route = respx.post(
        "https://dify.example.com/v1/workflows/published-42/run"
    ).mock(return_value=httpx.Response(200, json=_success_response()))

    await dify.DifyWorkflowClient(
        _settings(dify_work_record_workflow_id="published-42"), sleep=_no_sleep
    ).run(_request(), "{}")

    assert route.called


@respx.mock
@pytest.mark.asyncio
async def test_run_uses_system_identity_when_actor_is_absent() -> None:
    route = respx.post("https://dify.example.com/v1/workflows/run").mock(
        return_value=httpx.Response(200, json=_success_response())
    )

    await dify.DifyWorkflowClient(_settings(), sleep=_no_sleep).run(
        _request(actor_id=None), "{}"
    )

    sent = json.loads(route.calls.last.request.content)
    assert sent["user"] == hmac.new(
        b"hmac-secret", b"tenant-1:system", hashlib.sha256
    ).hexdigest()


@pytest.mark.parametrize(
    "overrides",
    [
        {"dify_base_url": ""},
        {"dify_work_record_api_key": ""},
        {"dify_user_hmac_secret": ""},
    ],
)
@pytest.mark.asyncio
async def test_run_rejects_missing_required_configuration(
    overrides: dict[str, object],
) -> None:
    with pytest.raises(dify.DifyWorkflowError) as raised:
        await dify.DifyWorkflowClient(
            _settings(**overrides), sleep=_no_sleep
        ).run(_request(), "{}")

    assert raised.value.reason == "configuration"


@pytest.mark.parametrize("status_code", [400, 401])
@respx.mock
@pytest.mark.asyncio
async def test_run_does_not_retry_configuration_errors(status_code: int) -> None:
    route = respx.post("https://dify.example.com/v1/workflows/run").mock(
        return_value=httpx.Response(status_code, json={"message": "rejected"})
    )

    with pytest.raises(dify.DifyWorkflowError) as raised:
        await dify.DifyWorkflowClient(_settings(), sleep=_no_sleep).run(_request(), "{}")

    assert raised.value.reason == f"http_{status_code}"
    assert route.call_count == 1


@pytest.mark.parametrize("status_code", [429, 500, 502, 503, 504])
@respx.mock
@pytest.mark.asyncio
async def test_run_retries_explicit_transient_http_errors(status_code: int) -> None:
    route = respx.post("https://dify.example.com/v1/workflows/run").mock(
        side_effect=[
            httpx.Response(status_code, json={"message": "temporary"}),
            httpx.Response(200, json=_success_response()),
        ]
    )

    result = await dify.DifyWorkflowClient(_settings(), sleep=_no_sleep).run(
        _request(), "{}"
    )

    assert result.run_id == "run-1"
    assert route.call_count == 2


@respx.mock
@pytest.mark.asyncio
async def test_run_does_not_retry_5xx_when_response_has_run_id() -> None:
    route = respx.post("https://dify.example.com/v1/workflows/run").mock(
        return_value=httpx.Response(503, json={"workflow_run_id": "unknown-run"})
    )

    with pytest.raises(dify.DifyWorkflowError) as raised:
        await dify.DifyWorkflowClient(_settings(), sleep=_no_sleep).run(_request(), "{}")

    assert raised.value.reason == "http_5xx_run_created"
    assert route.call_count == 1


@respx.mock
@pytest.mark.asyncio
async def test_run_retries_connection_failure() -> None:
    route = respx.post("https://dify.example.com/v1/workflows/run").mock(
        side_effect=[
            httpx.ConnectError("connection refused"),
            httpx.Response(200, json=_success_response()),
        ]
    )

    result = await dify.DifyWorkflowClient(_settings(), sleep=_no_sleep).run(
        _request(), "{}"
    )

    assert result.run_id == "run-1"
    assert route.call_count == 2


@respx.mock
@pytest.mark.asyncio
async def test_run_does_not_retry_read_timeout_with_unknown_outcome() -> None:
    route = respx.post("https://dify.example.com/v1/workflows/run").mock(
        side_effect=httpx.ReadTimeout("unknown outcome")
    )

    with pytest.raises(dify.DifyWorkflowError) as raised:
        await dify.DifyWorkflowClient(_settings(), sleep=_no_sleep).run(_request(), "{}")

    assert raised.value.reason == "timeout_unknown"
    assert route.call_count == 1


@pytest.mark.parametrize(
    ("payload", "reason"),
    [
        ({"data": {"status": "paused", "outputs": {}}}, "status_paused"),
        (
            _success_response(outputs={"markdown": "", "workflow_version": "version-1"}),
            "invalid_outputs",
        ),
        (
            _success_response(
                outputs={
                    "markdown": "# 月报",
                    "warnings": "not-a-list",
                    "workflow_version": "version-1",
                }
            ),
            "invalid_outputs",
        ),
        (
            _success_response(
                outputs={
                    "markdown": "# 月报",
                    "warnings": [],
                    "workflow_version": "wrong-version",
                }
            ),
            "workflow_version_mismatch",
        ),
    ],
)
@respx.mock
@pytest.mark.asyncio
async def test_run_rejects_non_success_or_invalid_outputs(
    payload: dict[str, object], reason: str
) -> None:
    route = respx.post("https://dify.example.com/v1/workflows/run").mock(
        return_value=httpx.Response(200, json=payload)
    )

    with pytest.raises(dify.DifyWorkflowError) as raised:
        await dify.DifyWorkflowClient(_settings(), sleep=_no_sleep).run(_request(), "{}")

    assert raised.value.reason == reason
    assert route.call_count == 1


@respx.mock
@pytest.mark.asyncio
async def test_run_rejects_invalid_json_without_retry() -> None:
    route = respx.post("https://dify.example.com/v1/workflows/run").mock(
        return_value=httpx.Response(200, text="not-json")
    )

    with pytest.raises(dify.DifyWorkflowError) as raised:
        await dify.DifyWorkflowClient(_settings(), sleep=_no_sleep).run(_request(), "{}")

    assert raised.value.reason == "invalid_response"
    assert route.call_count == 1
