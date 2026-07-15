---
title: Phase 20.5 AI 自动总结与 AI 月报
type: phase
status: draft
phase: work-record-20
owner: ai
created: 2026-07-14
updated: 2026-07-14
related: []
---

# Phase 20.5：AI 自动总结与 AI 月报

> 基线：`baicie/ai-ops`，分支 `feat/record-doc-portal`，提交 `43d16315cc4b68cc705177d2c8faba1d8e42644e`。
>
> 本文件由 Phase 20 总方案按主题拆分。Phase 20 开发前必须先完成 Phase 19 P0 修复。

## 数据库迁移

### 4.4 V0033：AI 生成结果

```sql
-- V0033__phase20_ai_generation.sql

create table if not exists work_record.wr_ai_generation (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    generation_type varchar(32) not null,
    resource_type varchar(32) not null,
    resource_id varchar(64) not null,
    period_start date,
    period_end date,
    status varchar(24) not null default 'queued',
    prompt_version varchar(64) not null,
    input_hash varchar(64) not null,
    input_json jsonb not null default '{}'::jsonb,
    output_markdown text,
    provider varchar(128),
    model varchar(128),
    requested_by varchar(64) not null,
    reviewed_by varchar(64),
    reviewed_at timestamptz,
    created_at timestamptz not null default now(),
    finished_at timestamptz,

    constraint ck_wr_ai_generation_type
        check (generation_type in ('record_summary', 'monthly_report')),

    constraint ck_wr_ai_generation_resource
        check (resource_type in ('record', 'tenant_month')),

    constraint ck_wr_ai_generation_status
        check (status in ('queued', 'running', 'success', 'failed', 'accepted', 'rejected')),

    constraint ck_wr_ai_generation_input
        check (jsonb_typeof(input_json) = 'object'),

    constraint ck_wr_ai_generation_hash
        check (input_hash ~ '^[0-9a-f]{64}$')
);

create unique index if not exists
uk_wr_ai_generation_input
on work_record.wr_ai_generation(
    tenant_id,
    generation_type,
    resource_type,
    resource_id,
    input_hash
)
where status in ('queued', 'running', 'success', 'accepted');

create index if not exists
idx_wr_ai_generation_resource
on work_record.wr_ai_generation(
    tenant_id,
    resource_type,
    resource_id,
    created_at desc
);
```

## 12. Phase 20.5：AI 自动总结与 AI 月报

### 12.1 安全边界

AI 生成遵守以下规则：

```text
1. 只把调用人有权读取的字段送给 Agent。
2. mask_mode=full 的字段永不进入 Prompt。
3. 附件正文默认不进入 Prompt，只传文件名和类型。
4. 输出永远是草稿，不自动覆盖工作记录或自动发布月报。
5. 输入快照、promptVersion、provider、model、reviewer 全部留痕。
6. 相同 inputHash 的成功结果直接复用，防止重复计费。
7. Agent 只返回 Markdown 和结构化 warnings，不执行外部操作。
```

### 12.2 Python Agent Schema

在 `apps/aiops-agent/src/aiops_agent/schemas.py` 增加：

```python
from datetime import date
from typing import Literal


class WorkRecordItem(BaseModel):
    id: str
    title: str
    status: str
    recordTime: datetime
    ownerName: str | None = None
    fields: dict[str, Any] = Field(default_factory=dict)
    relations: list[dict[str, Any]] = Field(default_factory=list)


class WorkRecordGenerateRequest(BaseModel):
    contractVersion: str = "work-record-generation.v1"
    generationType: Literal["record_summary", "monthly_report"]
    tenantId: str
    resourceId: str
    periodStart: date | None = None
    periodEnd: date | None = None
    locale: str = "zh-CN"
    promptVersion: str = "work-record-summary-v1"
    records: list[WorkRecordItem] = Field(default_factory=list, max_length=5000)
    statistics: dict[str, Any] = Field(default_factory=dict)
    traceId: str


class WorkRecordGenerateResponse(BaseModel):
    contractVersion: str = "work-record-generation.v1"
    provider: str
    model: str
    promptVersion: str
    markdown: str = Field(min_length=1, max_length=100000)
    warnings: list[str] = Field(default_factory=list)
    raw: dict[str, Any] = Field(default_factory=dict)
```

### 12.3 Python 生成服务

新增 `apps/aiops-agent/src/aiops_agent/work_record_generation.py`：

````python
from __future__ import annotations

import json
from collections import Counter

from aiops_agent.schemas import (
    WorkRecordGenerateRequest,
    WorkRecordGenerateResponse,
)
from aiops_agent.settings import Settings


class WorkRecordGenerationService:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def generate(
        self,
        request: WorkRecordGenerateRequest,
    ) -> WorkRecordGenerateResponse:
        if request.generationType == "record_summary":
            markdown = self._record_summary(request)
        else:
            markdown = self._monthly_report(request)

        return WorkRecordGenerateResponse(
            provider=self._settings.provider,
            model=self._settings.model,
            promptVersion=request.promptVersion,
            markdown=markdown,
            warnings=[],
            raw={
                "recordCount": len(request.records),
                "generationType": request.generationType,
            },
        )

    def _record_summary(self, request: WorkRecordGenerateRequest) -> str:
        if len(request.records) != 1:
            raise ValueError("record_summary requires exactly one record")

        record = request.records[0]
        field_lines = [
            f"- **{key}**：{self._render(value)}"
            for key, value in record.fields.items()
        ]
        relation_lines = [
            f"- {item.get('type', 'relation')}："
            f"{item.get('title') or item.get('id', '-') }"
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
                *(field_lines or ["- 暂无可总结字段。"]),
                "",
                "## 关联对象",
                *(relation_lines or ["- 暂无关联告警、巡检或事件。"]),
                "",
                "## 后续建议",
                "- 请由记录负责人复核总结内容后再发布。",
            ]
        )

    def _monthly_report(self, request: WorkRecordGenerateRequest) -> str:
        status_counter = Counter(record.status for record in request.records)
        owner_counter = Counter(
            record.ownerName or "未分配" for record in request.records
        )
        top_owners = owner_counter.most_common(10)

        return "\n".join(
            [
                "# 工作月报",
                "",
                f"统计周期：{request.periodStart} 至 {request.periodEnd}",
                f"记录总数：{len(request.records)}",
                "",
                "## 状态分布",
                *[
                    f"- {status}：{count}"
                    for status, count in sorted(status_counter.items())
                ],
                "",
                "## 工作量分布",
                *[f"- {owner}：{count}" for owner, count in top_owners],
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

    def _render(self, value: object) -> str:
        if isinstance(value, (dict, list)):
            return json.dumps(value, ensure_ascii=False)
        return str(value)
````

生成模式接入真实 LLM 时，`_record_summary/_monthly_report` 替换为 OpenAI-compatible structured output 调用，但 deterministic fallback 必须保留，Agent 不可用时仍能生成基础报告。

### 12.4 Python API

在 `main.py` 增加：

```python
from aiops_agent.schemas import (
    WorkRecordGenerateRequest,
    WorkRecordGenerateResponse,
)
from aiops_agent.work_record_generation import WorkRecordGenerationService


def work_record_generation_service() -> WorkRecordGenerationService:
    return WorkRecordGenerationService(settings)


@app.post(
    "/v1/work-record/generate",
    response_model=WorkRecordGenerateResponse,
    dependencies=[Depends(verify_internal_token)],
)
async def generate_work_record(
    request: WorkRecordGenerateRequest,
    service: WorkRecordGenerationService = Depends(
        work_record_generation_service
    ),
) -> WorkRecordGenerateResponse:
    if request.contractVersion != "work-record-generation.v1":
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="unsupported work-record generation contract",
        )
    return await service.generate(request)
```

### 12.5 Python 测试

新增 `apps/aiops-agent/tests/test_work_record_generation.py`：

```python
from datetime import UTC, date, datetime

import pytest

from aiops_agent.schemas import (
    WorkRecordGenerateRequest,
    WorkRecordItem,
)
from aiops_agent.settings import settings
from aiops_agent.work_record_generation import WorkRecordGenerationService


@pytest.mark.asyncio
async def test_record_summary_contains_visible_fields() -> None:
    service = WorkRecordGenerationService(settings)
    response = await service.generate(
        WorkRecordGenerateRequest(
            generationType="record_summary",
            tenantId="t1",
            resourceId="r1",
            records=[
                WorkRecordItem(
                    id="r1",
                    title="日报",
                    status="done",
                    recordTime=datetime(2026, 7, 11, tzinfo=UTC),
                    fields={"result": "发布完成"},
                )
            ],
            traceId="trace-1",
        )
    )

    assert "发布完成" in response.markdown
    assert response.promptVersion == "work-record-summary-v1"


@pytest.mark.asyncio
async def test_monthly_report_counts_records() -> None:
    service = WorkRecordGenerationService(settings)
    response = await service.generate(
        WorkRecordGenerateRequest(
            generationType="monthly_report",
            tenantId="t1",
            resourceId="2026-07",
            periodStart=date(2026, 7, 1),
            periodEnd=date(2026, 7, 31),
            records=[
                WorkRecordItem(
                    id="r1",
                    title="a",
                    status="done",
                    recordTime=datetime(2026, 7, 1, tzinfo=UTC),
                ),
                WorkRecordItem(
                    id="r2",
                    title="b",
                    status="draft",
                    recordTime=datetime(2026, 7, 2, tzinfo=UTC),
                ),
            ],
            traceId="trace-2",
        )
    )

    assert "记录总数：2" in response.markdown
    assert "done：1" in response.markdown
```

### 12.6 Java Client 契约

新增 `modules/aiops-ai-client/src/main/java/io/aegisops/ai/client/workrecord/WorkRecordAiClient.java`：

```java
package io.aegisops.ai.client.workrecord;

public interface WorkRecordAiClient {

  WorkRecordGenerationResponse generate(
      WorkRecordGenerationRequest request);
}
```

```java
package io.aegisops.ai.client.workrecord;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record WorkRecordGenerationRequest(
    String contractVersion,
    String generationType,
    String tenantId,
    String resourceId,
    LocalDate periodStart,
    LocalDate periodEnd,
    String locale,
    String promptVersion,
    List<RecordItem> records,
    Map<String, Object> statistics,
    String traceId) {

  public WorkRecordGenerationRequest {
    records = records == null ? List.of() : List.copyOf(records);
    statistics = statistics == null ? Map.of() : Map.copyOf(statistics);
  }

  public record RecordItem(
      String id,
      String title,
      String status,
      OffsetDateTime recordTime,
      String ownerName,
      Map<String, Object> fields,
      List<Map<String, Object>> relations) {

    public RecordItem {
      fields = fields == null ? Map.of() : Map.copyOf(fields);
      relations = relations == null ? List.of() : List.copyOf(relations);
    }
  }
}
```

```java
package io.aegisops.ai.client.workrecord;

import java.util.List;
import java.util.Map;

public record WorkRecordGenerationResponse(
    String contractVersion,
    String provider,
    String model,
    String promptVersion,
    String markdown,
    List<String> warnings,
    Map<String, Object> raw) {

  public WorkRecordGenerationResponse {
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
    raw = raw == null ? Map.of() : Map.copyOf(raw);
  }
}
```

### 12.7 HttpWorkRecordAiClient.java

```java
package io.aegisops.ai.client.workrecord;

import io.aegisops.ai.client.AgentClientProperties;
import io.aegisops.ai.client.AgentContract;
import io.aegisops.common.exception.AppException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpWorkRecordAiClient implements WorkRecordAiClient {

  private final AgentClientProperties properties;
  private final RestClient restClient;

  public HttpWorkRecordAiClient(
      AgentClientProperties properties,
      RestClient.Builder builder) {
    this.properties = properties;
    this.restClient =
        builder
            .baseUrl(properties.normalizedBaseUrl())
            .defaultHeader(
                HttpHeaders.CONTENT_TYPE,
                MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  @Override
  public WorkRecordGenerationResponse generate(
      WorkRecordGenerationRequest request) {
    try {
      WorkRecordGenerationResponse response =
          restClient
              .post()
              .uri("/v1/work-record/generate")
              .header(
                  AgentContract.INTERNAL_TOKEN_HEADER,
                  properties.normalizedInternalToken())
              .header(AgentContract.TRACE_ID_HEADER, request.traceId())
              .body(request)
              .retrieve()
              .body(WorkRecordGenerationResponse.class);
      if (response == null || response.markdown() == null || response.markdown().isBlank()) {
        throw new AppException("AI_WORK_RECORD_EMPTY", "AI returned an empty work-record result");
      }
      return response;
    } catch (AppException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new AppException("AI_WORK_RECORD_CALL_FAILED", "Failed to call work-record AI agent");
    }
  }
}
```

### 12.8 AiGenerationRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.workrecord.extension.domain.AiGeneration;
import java.util.List;
import java.util.Optional;

public interface AiGenerationRepository {

  AiGeneration create(CreateGeneration command);

  Optional<AiGeneration> find(String tenantId, String generationId);

  Optional<AiGeneration> findReusable(
      String tenantId,
      String generationType,
      String resourceType,
      String resourceId,
      String inputHash);

  List<AiGeneration> listByResource(
      String tenantId,
      String resourceType,
      String resourceId);

  boolean markRunning(String tenantId, String generationId);

  boolean complete(
      String tenantId,
      String generationId,
      String markdown,
      String provider,
      String model);

  boolean fail(String tenantId, String generationId);

  boolean review(
      String tenantId,
      String generationId,
      String targetStatus,
      String reviewerId);

  record CreateGeneration(
      String id,
      String tenantId,
      String generationType,
      String resourceType,
      String resourceId,
      java.time.LocalDate periodStart,
      java.time.LocalDate periodEnd,
      String promptVersion,
      String inputHash,
      String inputJson,
      String requestedBy) {}
}
```

### 12.9 AiGenerationService.java

```java
package io.aegisops.workrecord.extension.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.id.Ids;
import io.aegisops.common.outbox.OutboxMessage;
import io.aegisops.common.outbox.OutboxWriter;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.port.AiGenerationRepository;
import io.aegisops.workrecord.extension.domain.AiGeneration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiGenerationService {

  private final AiGenerationRepository repository;
  private final AiInputBuilder inputBuilder;
  private final OutboxWriter outbox;
  private final ObjectMapper objectMapper;

  public AiGenerationService(
      AiGenerationRepository repository,
      AiInputBuilder inputBuilder,
      OutboxWriter outbox,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.inputBuilder = inputBuilder;
    this.outbox = outbox;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public AiGeneration requestRecordSummary(
      String tenantId,
      String recordId,
      UserPrincipal user) {
    requireGenerate(user);
    AiInputBuilder.AiInput input =
        inputBuilder.recordSummary(tenantId, recordId, user);
    return createOrReuse(
        tenantId,
        "record_summary",
        "record",
        recordId,
        null,
        null,
        "work-record-summary-v1",
        input,
        user);
  }

  @Transactional
  public AiGeneration requestMonthlyReport(
      String tenantId,
      LocalDate month,
      UserPrincipal user) {
    requireGenerate(user);
    if (month == null) {
      throw new IllegalArgumentException("month is required");
    }
    LocalDate start = month.withDayOfMonth(1);
    LocalDate end = start.plusMonths(1).minusDays(1);
    AiInputBuilder.AiInput input =
        inputBuilder.monthlyReport(tenantId, start, end, user);
    return createOrReuse(
        tenantId,
        "monthly_report",
        "tenant_month",
        start.toString().substring(0, 7),
        start,
        end,
        "work-record-monthly-v1",
        input,
        user);
  }

  @Transactional
  public AiGeneration review(
      String tenantId,
      String generationId,
      boolean accepted,
      UserPrincipal user) {
    if (!user.hasPermission("work-record:ai:review")) {
      throw new AccessDeniedException("not allowed to review AI result");
    }
    String status = accepted ? "accepted" : "rejected";
    if (!repository.review(tenantId, generationId, status, user.id())) {
      throw new IllegalStateException("AI result cannot be reviewed");
    }
    return repository.find(tenantId, generationId).orElseThrow();
  }

  private AiGeneration createOrReuse(
      String tenantId,
      String generationType,
      String resourceType,
      String resourceId,
      LocalDate periodStart,
      LocalDate periodEnd,
      String promptVersion,
      AiInputBuilder.AiInput input,
      UserPrincipal user) {
    String inputJson = write(input.payload());
    String hash = sha256(inputJson);
    var reusable =
        repository.findReusable(
            tenantId,
            generationType,
            resourceType,
            resourceId,
            hash);
    if (reusable.isPresent()) {
      return reusable.get();
    }

    String id = Ids.newId();
    AiGeneration created =
        repository.create(
            new AiGenerationRepository.CreateGeneration(
                id,
                tenantId,
                generationType,
                resourceType,
                resourceId,
                periodStart,
                periodEnd,
                promptVersion,
                hash,
                inputJson,
                user.id()));

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("tenantId", tenantId);
    payload.put("generationId", id);
    payload.put("requestedBy", user.id());
    outbox.enqueue(
        new OutboxMessage(
            "worker",
            "work-record-ai-generate",
            tenantId,
            "ai-generation:" + id,
            OffsetDateTime.now(),
            5,
            payload));
    return created;
  }

  private void requireGenerate(UserPrincipal user) {
    if (user == null || !user.hasPermission("work-record:ai:generate")) {
      throw new AccessDeniedException("not allowed to generate AI work-record content");
    }
  }

  private String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException("failed to serialize AI input", ex);
    }
  }

  private String sha256(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256")
              .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}
```

### 12.10 AiGenerationProcessor.java

```java
package io.aegisops.workrecord.extension.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.workrecord.WorkRecordAiClient;
import io.aegisops.ai.client.workrecord.WorkRecordGenerationRequest;
import io.aegisops.workrecord.extension.application.port.AiGenerationRepository;
import org.springframework.stereotype.Service;

@Service
public class AiGenerationProcessor {

  private final AiGenerationRepository repository;
  private final WorkRecordAiClient client;
  private final ObjectMapper objectMapper;

  public AiGenerationProcessor(
      AiGenerationRepository repository,
      WorkRecordAiClient client,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.client = client;
    this.objectMapper = objectMapper;
  }

  public void process(String tenantId, String generationId) {
    var generation =
        repository.find(tenantId, generationId)
            .orElseThrow(() -> new IllegalArgumentException("AI generation not found"));
    if (!repository.markRunning(tenantId, generationId)) {
      return;
    }
    try {
      WorkRecordGenerationRequest request =
          objectMapper.readValue(
              generation.inputJson(),
              WorkRecordGenerationRequest.class);
      var response = client.generate(request);
      if (!repository.complete(
          tenantId,
          generationId,
          response.markdown(),
          response.provider(),
          response.model())) {
        throw new IllegalStateException("AI generation state changed");
      }
    } catch (RuntimeException ex) {
      repository.fail(tenantId, generationId);
      throw ex;
    } catch (Exception ex) {
      repository.fail(tenantId, generationId);
      throw new IllegalStateException("invalid AI input", ex);
    }
  }
}
```

### 12.11 AI 关键测试

```java
@Test
void recordSummaryInputMustExcludeFullyMaskedField() {
  FieldPolicyService policies = mock(FieldPolicyService.class);
  when(policies.readDecision(any(), eq("secret"), any()))
      .thenReturn(FieldReadDecision.hidden());

  AiInputBuilder builder = testBuilder(policies);
  AiInputBuilder.AiInput input =
      builder.recordSummary("t1", "r1", TestPrincipals.aiGenerator());

  assertThat(input.payload().toString()).doesNotContain("secret-value");
}

@Test
void sameInputHashReusesSuccessfulGeneration() {
  when(repository.findReusable("t1", "record_summary", "record", "r1", HASH))
      .thenReturn(Optional.of(existing));

  AiGeneration result =
      service.requestRecordSummary("t1", "r1", TestPrincipals.aiGenerator());

  assertThat(result.id()).isEqualTo(existing.id());
  verifyNoInteractions(outbox);
}
```

---

## 补充领域模型与 Repository 契约

### 16.3 AiGeneration.java

```java
package io.aegisops.workrecord.extension.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AiGeneration(
    String id,
    String tenantId,
    String generationType,
    String resourceType,
    String resourceId,
    LocalDate periodStart,
    LocalDate periodEnd,
    String status,
    String promptVersion,
    String inputHash,
    String inputJson,
    String outputMarkdown,
    String provider,
    String model,
    String requestedBy,
    String reviewedBy,
    OffsetDateTime reviewedAt,
    OffsetDateTime createdAt,
    OffsetDateTime finishedAt) {}
```
