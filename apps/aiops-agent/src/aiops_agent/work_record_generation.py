from __future__ import annotations

import json
from collections import Counter

from aiops_agent.schemas import WorkRecordGenerateRequest, WorkRecordGenerateResponse
from aiops_agent.settings import Settings


class WorkRecordGenerationService:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def generate(
        self, request: WorkRecordGenerateRequest
    ) -> WorkRecordGenerateResponse:
        if request.generationType not in {"record_summary", "monthly_report"}:
            raise ValueError("unsupported work-record generation type")
        markdown = (
            self._record_summary(request)
            if request.generationType == "record_summary"
            else self._monthly_report(request)
        )
        return WorkRecordGenerateResponse(
            provider=self._settings.provider,
            model=self._settings.model,
            promptVersion=request.promptVersion,
            markdown=markdown,
            raw={
                "recordCount": len(request.records),
                "generationType": request.generationType,
            },
        )

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

    def _monthly_report(self, request: WorkRecordGenerateRequest) -> str:
        statuses = Counter(record.status for record in request.records)
        owners = Counter(record.ownerName or "未分配" for record in request.records)
        return "\n".join(
            [
                "# 工作月报",
                "",
                f"统计周期：{request.periodStart} 至 {request.periodEnd}",
                f"记录总数：{len(request.records)}",
                "",
                "## 状态分布",
                *[f"- {key}：{value}" for key, value in sorted(statuses.items())],
                "",
                "## 工作量分布",
                *[f"- {key}：{value}" for key, value in owners.most_common(10)],
                "",
                "## 系统统计",
                "```json",
                json.dumps(request.statistics, ensure_ascii=False, indent=2),
                "```",
                "",
                "## 风险与改进",
                "- 请结合未完成记录、关联事件与 SLA 超时情况人工复核。",
            ]
        )

    @staticmethod
    def _render(value: object) -> str:
        if isinstance(value, (dict, list)):
            return json.dumps(value, ensure_ascii=False)
        return str(value)
