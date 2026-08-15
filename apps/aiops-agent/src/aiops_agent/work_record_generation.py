from __future__ import annotations

import json
from collections import Counter

from aiops_agent.dify_workflow import DifyWorkflowClient, DifyWorkflowError
from aiops_agent.observability.metrics import DIFY_FALLBACK_COUNT, DIFY_TOKEN_COUNT
from aiops_agent.schemas import WorkRecordGenerateRequest, WorkRecordGenerateResponse
from aiops_agent.settings import Settings


class WorkRecordGenerationService:
    def __init__(
        self,
        settings: Settings,
        dify_client: DifyWorkflowClient | None = None,
    ) -> None:
        self._settings = settings
        self._dify_client = dify_client or DifyWorkflowClient(settings)

    async def generate(
        self, request: WorkRecordGenerateRequest
    ) -> WorkRecordGenerateResponse:
        if request.generationType not in {
            "record_summary",
            "weekly_report",
            "monthly_report",
        }:
            raise ValueError("unsupported work-record generation type")
        if self._settings.normalized_work_record_provider() == "dify":
            return await self._generate_with_dify(request)
        return self._deterministic_response(request)

    async def _generate_with_dify(
        self,
        request: WorkRecordGenerateRequest,
    ) -> WorkRecordGenerateResponse:
        context_json = self._report_context_json(request)
        input_bytes = len(context_json.encode("utf-8"))
        if input_bytes > self._settings.dify_max_input_bytes:
            return self._fallback_response(
                request,
                reason="input_too_large",
                warning=(
                    f"Dify 输入超过 {self._settings.dify_max_input_bytes} 字节，"
                    "已使用确定性模板。"
                ),
                input_bytes=input_bytes,
            )

        try:
            result = await self._dify_client.run(request, context_json)
        except DifyWorkflowError as exc:
            return self._fallback_response(
                request,
                reason=exc.reason,
                warning="Dify 生成失败，已使用确定性模板。",
                input_bytes=input_bytes,
            )

        if result.total_tokens is not None:
            DIFY_TOKEN_COUNT.labels(request.generationType).inc(result.total_tokens)
        raw = self._base_raw(request, input_bytes)
        raw.update(
            {
                "providerRunId": result.run_id,
                "providerWorkflowId": result.workflow_id,
                "providerWorkflowVersion": result.workflow_version,
                "providerDurationMs": result.elapsed_ms,
                "providerTotalTokens": result.total_tokens,
                "fallbackReason": None,
            }
        )
        return WorkRecordGenerateResponse(
            provider=result.provider,
            model=result.model,
            promptVersion=request.promptVersion,
            markdown=result.markdown,
            warnings=result.warnings,
            providerRunId=result.run_id,
            providerWorkflowId=result.workflow_id,
            providerWorkflowVersion=result.workflow_version,
            providerDurationMs=result.elapsed_ms,
            providerTotalTokens=result.total_tokens,
            fallbackReason=None,
            raw=raw,
        )

    def _deterministic_response(
        self,
        request: WorkRecordGenerateRequest,
        *,
        warnings: list[str] | None = None,
        fallback_reason: str | None = None,
        provider_workflow_id: str | None = None,
        provider_workflow_version: str | None = None,
        raw: dict[str, object] | None = None,
    ) -> WorkRecordGenerateResponse:
        if request.generationType == "record_summary":
            markdown = self._record_summary(request)
        else:
            markdown = self._period_report(request)
        return WorkRecordGenerateResponse(
            provider="deterministic",
            model=self._settings.model,
            promptVersion=request.promptVersion,
            markdown=markdown,
            warnings=warnings or [],
            providerWorkflowId=provider_workflow_id,
            providerWorkflowVersion=provider_workflow_version,
            fallbackReason=fallback_reason,
            raw=raw
            or {
                "recordCount": len(request.records),
                "generationType": request.generationType,
            },
        )

    def _fallback_response(
        self,
        request: WorkRecordGenerateRequest,
        *,
        reason: str,
        warning: str,
        input_bytes: int,
    ) -> WorkRecordGenerateResponse:
        DIFY_FALLBACK_COUNT.labels(request.generationType, reason).inc()
        raw = self._base_raw(request, input_bytes)
        raw.update(
            {
                "requestedProvider": "dify",
                "providerRunId": None,
                "providerWorkflowId": (
                    self._settings.dify_work_record_workflow_id or None
                ),
                "providerWorkflowVersion": self._configured_workflow_version(),
                "providerDurationMs": None,
                "providerTotalTokens": None,
                "fallbackReason": reason,
            }
        )
        return self._deterministic_response(
            request,
            warnings=[warning],
            fallback_reason=reason,
            provider_workflow_id=(
                self._settings.dify_work_record_workflow_id or None
            ),
            provider_workflow_version=self._configured_workflow_version(),
            raw=raw,
        )

    def _configured_workflow_version(self) -> str | None:
        return self._settings.dify_work_record_workflow_version or None

    @staticmethod
    def _report_context_json(request: WorkRecordGenerateRequest) -> str:
        context = request.model_dump(
            mode="json",
            include={
                "resourceId",
                "periodStart",
                "periodEnd",
                "records",
                "statistics",
            },
        )
        return json.dumps(
            context,
            ensure_ascii=False,
            separators=(",", ":"),
            sort_keys=True,
        )

    @staticmethod
    def _base_raw(
        request: WorkRecordGenerateRequest,
        input_bytes: int,
    ) -> dict[str, object]:
        return {
            "recordCount": len(request.records),
            "generationType": request.generationType,
            "inputBytes": input_bytes,
        }

    def _record_summary(self, request: WorkRecordGenerateRequest) -> str:
        if len(request.records) != 1:
            raise ValueError("record_summary requires exactly one record")
        record = request.records[0]
        fields = [
            f"- **{key}**：{self._render(value)}"
            for key, value in record.fields.items()
        ]
        relations = [
            f"- {item.get('type', 'relation')}：{item.get('title') or item.get('id', '-')}"
            for item in record.relations
        ]
        return "\n".join(
            [
                f"# {record.title}",
                "",
                "## 工作概述",
                f"记录状态：{record.status}；记录时间：{record.recordTime.isoformat()}。",
                "",
                "## 关键内容",
                *(fields or ["- 暂无可总结字段。"]),
                "",
                "## 关联对象",
                *(relations or ["- 暂无关联告警、巡检或事件。"]),
                "",
                "## 后续建议",
                "- 请由记录负责人复核总结内容后再发布。",
            ]
        )

    def _period_report(self, request: WorkRecordGenerateRequest) -> str:
        sampled_statuses = Counter(record.status for record in request.records)
        status_counts = request.statistics.get("statusCounts")
        statuses = status_counts if isinstance(status_counts, dict) else sampled_statuses
        sampled_owners = Counter(record.ownerName or "未分配" for record in request.records)
        owner_counts = request.statistics.get("ownerCounts")
        owners = self._owner_counts(owner_counts, sampled_owners)
        record_count = request.statistics.get("recordCount", len(request.records))
        title = "工作周报" if request.generationType == "weekly_report" else "工作月报"
        statistics = [
            f"- **{key}**：{self._render(value)}"
            for key, value in request.statistics.items()
        ]
        return "\n".join(
            [
                f"# {title}",
                "",
                f"统计周期：{request.periodStart} 至 {request.periodEnd}",
                f"记录总数：{record_count}",
                "",
                "## 状态分布",
                *[f"- {key}：{value}" for key, value in sorted(statuses.items())],
                "",
                "## 工作量分布",
                *[f"- {label}：{count}" for label, count in owners],
                "",
                "## 系统统计",
                *(statistics or ["- 暂无系统统计。"]),
                "",
                "## 风险与改进",
                "- 请结合未完成记录、关联事件与 SLA 超时情况人工复核。",
            ]
        )

    @staticmethod
    def _owner_counts(
        value: object, fallback: Counter[str]
    ) -> list[tuple[str, int]]:
        if isinstance(value, list):
            structured: list[tuple[str, int, str]] = []
            for item in value:
                if not isinstance(item, dict):
                    continue
                display_name = str(item.get("displayName") or "未分配")
                owner_id = str(item.get("ownerId") or "")
                try:
                    count = int(item.get("count", 0))
                except (TypeError, ValueError):
                    continue
                label = f"{display_name} ({owner_id})" if owner_id else display_name
                structured.append((label, count, owner_id))
            return [
                (label, count)
                for label, count, _ in sorted(
                    structured, key=lambda item: (-item[1], item[2], item[0])
                )[:10]
            ]
        owners = value if isinstance(value, dict) else fallback
        return [
            (str(key), int(count))
            for key, count in sorted(
                owners.items(), key=lambda item: (-int(item[1]), str(item[0]))
            )[:10]
        ]

    @staticmethod
    def _render(value: object) -> str:
        if isinstance(value, (dict, list)):
            return json.dumps(value, ensure_ascii=False)
        return str(value)
