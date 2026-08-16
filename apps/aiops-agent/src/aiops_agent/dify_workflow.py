"""Dify Workflow adapter for work-record generation."""

from __future__ import annotations

import asyncio
import hashlib
import hmac
import random
import time
from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field
from typing import Any

import httpx

from aiops_agent.observability.metrics import DIFY_REQUEST_COUNT, DIFY_REQUEST_DURATION
from aiops_agent.schemas import WorkRecordGenerateRequest
from aiops_agent.settings import Settings

_RETRYABLE_SERVER_STATUSES = {500, 502, 503, 504}


@dataclass(frozen=True)
class WorkRecordProviderResult:
    markdown: str
    provider: str
    model: str
    warnings: list[str] = field(default_factory=list)
    run_id: str | None = None
    workflow_id: str | None = None
    workflow_version: str | None = None
    elapsed_ms: int | None = None
    total_tokens: int | None = None
    fallback_reason: str | None = None


class DifyWorkflowError(RuntimeError):
    def __init__(self, reason: str, message: str) -> None:
        super().__init__(message)
        self.reason = reason


class DifyWorkflowClient:
    def __init__(
        self,
        settings: Settings,
        *,
        sleep: Callable[[float], Awaitable[None]] = asyncio.sleep,
    ) -> None:
        self._settings = settings
        self._sleep = sleep

    async def run(
        self,
        request: WorkRecordGenerateRequest,
        report_context_json: str,
    ) -> WorkRecordProviderResult:
        started = time.perf_counter()
        status = "success"
        try:
            return await self._run(request, report_context_json)
        except DifyWorkflowError as exc:
            status = self._metric_status(exc.reason)
            raise
        finally:
            DIFY_REQUEST_COUNT.labels(request.generationType, status).inc()
            DIFY_REQUEST_DURATION.labels(request.generationType, status).observe(
                time.perf_counter() - started
            )

    async def _run(
        self,
        request: WorkRecordGenerateRequest,
        report_context_json: str,
    ) -> WorkRecordProviderResult:
        self._validate_configuration()
        payload = self._build_payload(request, report_context_json)
        headers = {
            "Authorization": f"Bearer {self._settings.dify_work_record_api_key}",
            "Content-Type": "application/json",
        }

        for attempt in range(self._settings.dify_max_retries + 1):
            try:
                async with httpx.AsyncClient(
                    timeout=self._settings.dify_timeout_seconds,
                    trust_env=False,
                ) as client:
                    response = await client.post(
                        self._endpoint(),
                        headers=headers,
                        json=payload,
                    )
            except (httpx.ConnectError, httpx.ConnectTimeout) as exc:
                if attempt < self._settings.dify_max_retries:
                    await self._backoff(attempt)
                    continue
                raise DifyWorkflowError(
                    "connection_failed", "Dify connection failed"
                ) from exc
            except httpx.TimeoutException as exc:
                raise DifyWorkflowError(
                    "timeout_unknown", "Dify request timed out with unknown outcome"
                ) from exc
            except httpx.RequestError as exc:
                raise DifyWorkflowError("request_failed", "Dify request failed") from exc

            if response.status_code == 429:
                if attempt < self._settings.dify_max_retries:
                    await self._backoff(attempt)
                    continue
                raise DifyWorkflowError("http_429", "Dify rate limit exceeded")

            if response.status_code in _RETRYABLE_SERVER_STATUSES:
                if self._response_has_run_id(response):
                    raise DifyWorkflowError(
                        "http_5xx_run_created",
                        "Dify failed after creating a workflow run",
                    )
                if attempt < self._settings.dify_max_retries:
                    await self._backoff(attempt)
                    continue
                raise DifyWorkflowError("http_5xx", "Dify server error")

            if response.status_code in {400, 401}:
                raise DifyWorkflowError(
                    f"http_{response.status_code}",
                    "Dify rejected the workflow request",
                )
            if response.is_error:
                raise DifyWorkflowError("http_error", "Dify HTTP request failed")

            return self._parse_response(response)

        raise DifyWorkflowError("request_failed", "Dify request failed")

    def _validate_configuration(self) -> None:
        if not all(
            (
                self._settings.normalized_dify_base_url(),
                self._settings.dify_work_record_api_key.strip(),
                self._settings.dify_user_hmac_secret.strip(),
            )
        ):
            raise DifyWorkflowError(
                "configuration", "Dify work-record configuration is incomplete"
            )

    def _endpoint(self) -> str:
        base_url = self._settings.normalized_dify_base_url()
        workflow_id = self._settings.dify_work_record_workflow_id.strip()
        if workflow_id:
            return f"{base_url}/workflows/{workflow_id}/run"
        return f"{base_url}/workflows/run"

    def _build_payload(
        self,
        request: WorkRecordGenerateRequest,
        report_context_json: str,
    ) -> dict[str, Any]:
        identity = f"{request.tenantId}:{request.actorId or 'system'}"
        opaque_user = hmac.new(
            self._settings.dify_user_hmac_secret.encode(),
            identity.encode(),
            hashlib.sha256,
        ).hexdigest()
        return {
            "inputs": {
                "generation_type": request.generationType,
                "report_context_json": report_context_json,
                "locale": request.locale,
                "prompt_version": request.promptVersion,
                "trace_id": request.traceId,
            },
            "response_mode": "blocking",
            "user": opaque_user,
        }

    async def _backoff(self, attempt: int) -> None:
        delay = 0.25 * (2**attempt) * random.uniform(0.8, 1.2)
        await self._sleep(delay)

    def _parse_response(self, response: httpx.Response) -> WorkRecordProviderResult:
        try:
            payload = response.json()
        except ValueError as exc:
            raise DifyWorkflowError(
                "invalid_response", "Dify response is not valid JSON"
            ) from exc

        if not isinstance(payload, dict) or not isinstance(payload.get("data"), dict):
            raise DifyWorkflowError(
                "invalid_response", "Dify response data must be an object"
            )

        data = payload["data"]
        status = data.get("status")
        if status != "succeeded":
            reason = (
                f"status_{status}"
                if status in {"failed", "paused", "stopped"}
                else "status_unknown"
            )
            raise DifyWorkflowError(reason, "Dify workflow did not succeed")

        outputs = data.get("outputs")
        if not isinstance(outputs, dict):
            raise DifyWorkflowError("invalid_outputs", "Dify outputs must be an object")

        markdown, warnings, workflow_version = self._extract_outputs(outputs)
        if (
            not isinstance(markdown, str)
            or not markdown.strip()
            or len(markdown) > 100000
            or not isinstance(warnings, list)
            or len(warnings) > 20
            or any(
                not isinstance(item, str) or len(item) > 500
                for item in warnings
            )
        ):
            raise DifyWorkflowError("invalid_outputs", "Dify outputs are invalid")

        expected_version = self._settings.dify_work_record_workflow_version.strip()
        if expected_version and workflow_version != expected_version:
            raise DifyWorkflowError(
                "workflow_version_mismatch",
                "Dify workflow version does not match the configured version",
            )
        if workflow_version is not None and not isinstance(workflow_version, str):
            raise DifyWorkflowError("invalid_outputs", "Workflow version must be text")

        elapsed_ms = self._elapsed_ms(data.get("elapsed_time"))
        total_tokens = self._non_negative_int(data.get("total_tokens"), "total_tokens")
        return WorkRecordProviderResult(
            markdown=markdown.strip(),
            provider="dify",
            model="dify-workflow",
            warnings=[item.strip() for item in warnings],
            run_id=self._optional_text(payload.get("workflow_run_id") or data.get("id")),
            workflow_id=self._optional_text(data.get("workflow_id")),
            workflow_version=self._optional_text(workflow_version),
            elapsed_ms=elapsed_ms,
            total_tokens=total_tokens,
        )

    @staticmethod
    def _elapsed_ms(value: object) -> int | None:
        if value is None:
            return None
        if isinstance(value, bool) or not isinstance(value, (int, float)) or value < 0:
            raise DifyWorkflowError("invalid_response", "Elapsed time is invalid")
        return round(value * 1000)

    @staticmethod
    def _non_negative_int(value: object, name: str) -> int | None:
        if value is None:
            return None
        if isinstance(value, bool) or not isinstance(value, int) or value < 0:
            raise DifyWorkflowError("invalid_response", f"{name} is invalid")
        return value

    @staticmethod
    def _optional_text(value: object) -> str | None:
        return value.strip() if isinstance(value, str) and value.strip() else None

    @staticmethod
    def _extract_outputs(outputs: dict[str, Any]) -> tuple[Any, Any, Any]:
        """Read the executed branch from Dify workflows with multiple End nodes."""
        prefixes = ("summary_", "period_", "weekly_", "monthly_", "")
        for prefix in prefixes:
            markdown_key = f"{prefix}markdown"
            markdown = outputs.get(markdown_key)
            if isinstance(markdown, str) and markdown.strip():
                return (
                    markdown,
                    outputs.get(f"{prefix}warnings", []),
                    outputs.get(f"{prefix}workflow_version"),
                )
        for prefix in prefixes:
            markdown_key = f"{prefix}markdown"
            if markdown_key in outputs:
                return (
                    outputs.get(markdown_key),
                    outputs.get(f"{prefix}warnings", []),
                    outputs.get(f"{prefix}workflow_version"),
                )
        return None, [], None

    @staticmethod
    def _response_has_run_id(response: httpx.Response) -> bool:
        try:
            payload = response.json()
        except ValueError:
            return False
        if not isinstance(payload, dict):
            return False
        data = payload.get("data")
        return bool(
            DifyWorkflowClient._optional_text(payload.get("workflow_run_id"))
            or (
                isinstance(data, dict)
                and DifyWorkflowClient._optional_text(data.get("id"))
            )
        )

    @staticmethod
    def _metric_status(reason: str) -> str:
        if reason.startswith("status_"):
            return "workflow_failed"
        if reason in {
            "configuration",
            "connection_failed",
            "http_400",
            "http_401",
            "http_429",
            "http_5xx",
            "http_5xx_run_created",
            "http_error",
            "invalid_outputs",
            "invalid_response",
            "request_failed",
            "timeout_unknown",
            "workflow_version_mismatch",
        }:
            return reason
        return "other"
