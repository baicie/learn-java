import asyncio
from datetime import UTC, date, datetime

from aiops_agent.schemas import WorkRecordGenerateRequest, WorkRecordItem
from aiops_agent.settings import settings
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
