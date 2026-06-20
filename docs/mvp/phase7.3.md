# Phase7.3：Agent Memory

> Phase7.3 目标：给 Agent 增加 **受控、可审计、可检索的记忆能力**。
> 它不是让 Agent 自主长期记忆所有内容，而是把“经过策略允许的诊断经验”写入 Java 后端，由 Python Agent 在后续诊断中只读检索。

---

# 1. Phase7.3 定位

前置阶段：

```txt id="769soq"
Phase7.0 Agent Graph Modularization
Phase7.1 Human-in-the-loop Checkpoint
Phase7.2 Multi-Agent Collaboration
```

Phase7.3 新增：

```txt id="2p0hef"
Java:
  agent_memory
  agent_memory_event
  AgentMemoryService
  AgentMemoryRepository
  AgentMemoryController
  InternalAgentMemoryController

Python:
  AgentMemory
  MemoryClient
  memory_retrieval_graph
  memory_write_graph
  Agent graph memory state
```

最终流程：

```txt id="cy13lz"
input
  -> memory_retrieval_graph
  -> evidence_graph
  -> case_retrieval_graph
  -> rca / multi_agent_rca
  -> human_checkpoint_graph
  -> runbook / multi_agent_recommendation
  -> final_report_graph
  -> memory_write_graph
  -> output
```

---

# 2. 不做什么

```txt id="nuvzvd"
1. 不让 Agent 自主记忆任意内容
2. 不记用户隐私
3. 不记密钥/token/password
4. 不记原始日志全文
5. 不记执行凭证
6. 不创建 execution
7. 不调 runner
8. 不自动修复
9. 不自动 rollback
10. 不做跨租户 memory
```

---

# 3. Memory 类型

```txt id="qpxp9z"
incident_summary      事故摘要
root_cause_pattern    根因模式
service_behavior      服务行为特征
runbook_hint          Runbook 建议线索
safety_note           安全注意事项
```

---

# 4. Memory 写入策略

只有满足以下条件才允许写入：

```txt id="z70wwr"
1. enable_agent_memory_write=true
2. diagnosis confidence >= 0.60
3. root_cause 不是 "Root cause is not confirmed"
4. content 不包含 secret/password/token/private key
5. content 长度不超过 4000
6. tenant_id 必须存在
7. memory_type 必须在 allowlist
```

---

# 5. Java Migration

路径：

```txt id="awh62k"
apps/aiops-server/src/main/resources/db/migration/V25__phase7_3_agent_memory.sql
```

```sql id="d5fxld"
-- Phase 7.3: Agent Memory.
-- Controlled, tenant-scoped, auditable memory.
-- This phase does not add execution capability and does not call runner.

create table if not exists agent_memory (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  scope_type varchar(64) not null default 'tenant',
  scope_id varchar(128),
  memory_type varchar(64) not null,
  source_type varchar(64) not null default 'diagnosis',
  source_id varchar(128),
  title varchar(240) not null,
  content text not null,
  tags jsonb not null default '[]'::jsonb,
  confidence numeric(6,4) not null default 0,
  status varchar(32) not null default 'active',
  created_by varchar(64) not null,
  expires_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint ck_agent_memory_scope_type
    check (scope_type in ('tenant', 'service', 'incident', 'asset')),
  constraint ck_agent_memory_type
    check (memory_type in (
      'incident_summary',
      'root_cause_pattern',
      'service_behavior',
      'runbook_hint',
      'safety_note'
    )),
  constraint ck_agent_memory_source_type
    check (source_type in ('diagnosis', 'postmortem', 'incident_case', 'manual')),
  constraint ck_agent_memory_status
    check (status in ('active', 'archived')),
  constraint ck_agent_memory_confidence
    check (confidence >= 0 and confidence <= 1)
);

create index if not exists idx_agent_memory_scope
  on agent_memory(tenant_id, scope_type, scope_id, status, created_at desc);

create index if not exists idx_agent_memory_type
  on agent_memory(tenant_id, memory_type, status, created_at desc);

create index if not exists idx_agent_memory_source
  on agent_memory(tenant_id, source_type, source_id);

create table if not exists agent_memory_event (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  memory_id varchar(64) references agent_memory(id) on delete cascade,
  event_type varchar(64) not null,
  summary text not null,
  actor varchar(64) not null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),

  constraint ck_agent_memory_event_type
    check (event_type in ('created', 'searched', 'archived', 'rejected_by_policy'))
);

create index if not exists idx_agent_memory_event_memory
  on agent_memory_event(tenant_id, memory_id, created_at desc);

create index if not exists idx_agent_memory_event_type
  on agent_memory_event(tenant_id, event_type, created_at desc);
```

---

# 6. jOOQ Codegen

路径：

```txt id="yu3j2a"
modules/aiops-persistence/src/main/resources/jooq-codegen.xml
```

追加：

```txt id="pp3hst"
agent_memory | agent_memory_event
```

---

# 7. Java DTO

## 7.1 `AgentMemoryCreateRequest.java`

路径：

```txt id="a72m55"
modules/aiops-execution/src/main/java/io/aegisops/execution/dto/AgentMemoryCreateRequest.java
```

```java id="x2bot5"
package io.aegisops.execution.dto;

import java.util.List;

public record AgentMemoryCreateRequest(
    String tenantId,
    String scopeType,
    String scopeId,
    String memoryType,
    String sourceType,
    String sourceId,
    String title,
    String content,
    List<String> tags,
    Double confidence,
    Integer ttlSeconds,
    String createdBy) {}
```

---

## 7.2 `AgentMemorySearchRequest.java`

```java id="6u0gxj"
package io.aegisops.execution.dto;

import java.util.List;

public record AgentMemorySearchRequest(
    String tenantId,
    String query,
    String scopeType,
    String scopeId,
    List<String> memoryTypes,
    List<String> tags,
    Integer topK,
    String createdBy) {}
```

---

## 7.3 `AgentMemoryCreateCommand.java`

```java id="0e7dxo"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentMemoryCreateCommand(
    String id,
    String tenantId,
    String scopeType,
    String scopeId,
    String memoryType,
    String sourceType,
    String sourceId,
    String title,
    String content,
    String tagsJson,
    double confidence,
    String status,
    String createdBy,
    OffsetDateTime expiresAt) {}
```

---

## 7.4 `AgentMemoryRecord.java`

```java id="1hknei"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentMemoryRecord(
    String id,
    String tenantId,
    String scopeType,
    String scopeId,
    String memoryType,
    String sourceType,
    String sourceId,
    String title,
    String content,
    String tagsJson,
    double confidence,
    String status,
    String createdBy,
    OffsetDateTime expiresAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 7.5 `AgentMemoryResponse.java`

```java id="k9godi"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AgentMemoryResponse(
    String id,
    String scopeType,
    String scopeId,
    String memoryType,
    String sourceType,
    String sourceId,
    String title,
    String content,
    List<String> tags,
    double confidence,
    String status,
    String createdBy,
    OffsetDateTime expiresAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 7.6 `AgentMemorySearchResult.java`

```java id="o0cmtr"
package io.aegisops.execution.dto;

import java.util.List;

public record AgentMemorySearchResult(
    String id,
    String scopeType,
    String scopeId,
    String memoryType,
    String sourceType,
    String sourceId,
    String title,
    String content,
    List<String> tags,
    double confidence,
    double score,
    String createdBy) {}
```

---

## 7.7 `AgentMemorySearchResponse.java`

```java id="sa9irt"
package io.aegisops.execution.dto;

import java.util.List;

public record AgentMemorySearchResponse(
    String query,
    int topK,
    List<AgentMemorySearchResult> results) {}
```

---

## 7.8 `AgentMemoryEventCreateCommand.java`

```java id="8kl2r4"
package io.aegisops.execution.dto;

public record AgentMemoryEventCreateCommand(
    String id,
    String tenantId,
    String memoryId,
    String eventType,
    String summary,
    String actor,
    String metadataJson) {}
```

---

# 8. Java JSON 工具

## 8.1 `AgentMemoryJson.java`

路径：

```txt id="b4a8sy"
modules/aiops-execution/src/main/java/io/aegisops/execution/AgentMemoryJson.java
```

```java id="5rj5h6"
package io.aegisops.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AgentMemoryJson {
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
  private final ObjectMapper objectMapper;

  public AgentMemoryJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      if (value == null) {
        return "{}";
      }
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new AppException("AGENT_MEMORY_JSON_WRITE_FAILED", "Failed to serialize memory json");
    }
  }

  public List<String> readStringList(String json) {
    try {
      if (json == null || json.isBlank()) {
        return List.of();
      }

      List<String> values = objectMapper.readValue(json, STRING_LIST);
      if (values == null) {
        return List.of();
      }

      return values.stream()
          .filter(value -> value != null && !value.isBlank())
          .map(String::trim)
          .distinct()
          .toList();
    } catch (Exception ex) {
      throw new AppException("AGENT_MEMORY_JSON_READ_FAILED", "Failed to parse memory string list");
    }
  }
}
```

---

# 9. Memory Policy

## 9.1 `AgentMemoryPolicy.java`

路径：

```txt id="a6jpsn"
modules/aiops-execution/src/main/java/io/aegisops/execution/AgentMemoryPolicy.java
```

```java id="rww6a4"
package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.AgentMemoryCreateRequest;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AgentMemoryPolicy {
  private static final int MAX_CONTENT_LENGTH = 4000;

  private static final List<String> ALLOWED_MEMORY_TYPES =
      List.of(
          "incident_summary",
          "root_cause_pattern",
          "service_behavior",
          "runbook_hint",
          "safety_note");

  private static final List<String> ALLOWED_SCOPE_TYPES =
      List.of("tenant", "service", "incident", "asset");

  private static final List<String> ALLOWED_SOURCE_TYPES =
      List.of("diagnosis", "postmortem", "incident_case", "manual");

  private static final List<Pattern> SECRET_PATTERNS =
      List.of(
          Pattern.compile("(?i)password\\s*[:=]"),
          Pattern.compile("(?i)secret\\s*[:=]"),
          Pattern.compile("(?i)token\\s*[:=]"),
          Pattern.compile("(?i)api[_-]?key\\s*[:=]"),
          Pattern.compile("-----BEGIN\\s+(RSA|OPENSSH|PRIVATE)\\s+KEY-----"));

  public void validateCreate(AgentMemoryCreateRequest request) {
    if (request == null) {
      throw new AppException("AGENT_MEMORY_REQUEST_REQUIRED", "Memory create request is required");
    }
    if (request.tenantId() == null || request.tenantId().isBlank()) {
      throw new AppException("AGENT_MEMORY_TENANT_REQUIRED", "Tenant id is required");
    }
    if (request.title() == null || request.title().isBlank()) {
      throw new AppException("AGENT_MEMORY_TITLE_REQUIRED", "Memory title is required");
    }
    if (request.content() == null || request.content().isBlank()) {
      throw new AppException("AGENT_MEMORY_CONTENT_REQUIRED", "Memory content is required");
    }
    if (request.content().length() > MAX_CONTENT_LENGTH) {
      throw new AppException("AGENT_MEMORY_CONTENT_TOO_LONG", "Memory content is too long");
    }
    if (!ALLOWED_MEMORY_TYPES.contains(normalize(request.memoryType(), "incident_summary"))) {
      throw new AppException("AGENT_MEMORY_TYPE_INVALID", "Invalid memory type");
    }
    if (!ALLOWED_SCOPE_TYPES.contains(normalize(request.scopeType(), "tenant"))) {
      throw new AppException("AGENT_MEMORY_SCOPE_INVALID", "Invalid memory scope");
    }
    if (!ALLOWED_SOURCE_TYPES.contains(normalize(request.sourceType(), "diagnosis"))) {
      throw new AppException("AGENT_MEMORY_SOURCE_INVALID", "Invalid memory source type");
    }
    if (containsSecret(request.title()) || containsSecret(request.content())) {
      throw new AppException("AGENT_MEMORY_SECRET_REJECTED", "Memory content contains secret-like text");
    }
  }

  public boolean containsSecret(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }

    for (Pattern pattern : SECRET_PATTERNS) {
      if (pattern.matcher(text).find()) {
        return true;
      }
    }

    return false;
  }

  public String normalize(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim().toLowerCase();
  }
}
```

---

# 10. Repository

## 10.1 `AgentMemoryRepository.java`

路径：

```txt id="y9545m"
modules/aiops-execution/src/main/java/io/aegisops/execution/AgentMemoryRepository.java
```

```java id="8h8wf7"
package io.aegisops.execution;

import io.aegisops.execution.dto.AgentMemoryCreateCommand;
import io.aegisops.execution.dto.AgentMemoryEventCreateCommand;
import io.aegisops.execution.dto.AgentMemoryRecord;
import java.util.List;
import java.util.Optional;

public interface AgentMemoryRepository {
  void create(AgentMemoryCreateCommand command);

  Optional<AgentMemoryRecord> find(String tenantId, String memoryId);

  List<AgentMemoryRecord> listActiveCandidates(
      String tenantId,
      String scopeType,
      String scopeId,
      List<String> memoryTypes,
      List<String> tags,
      int limit);

  boolean archive(String tenantId, String memoryId);

  void createEvent(AgentMemoryEventCreateCommand command);
}
```

---

## 10.2 `JooqAgentMemoryRepository.java`

路径：

```txt id="cg1uyg"
modules/aiops-execution/src/main/java/io/aegisops/execution/JooqAgentMemoryRepository.java
```

```java id="o01qbr"
package io.aegisops.execution;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.AGENT_MEMORY;
import static io.aegisops.persistence.jooq.Tables.AGENT_MEMORY_EVENT;

import io.aegisops.execution.dto.AgentMemoryCreateCommand;
import io.aegisops.execution.dto.AgentMemoryEventCreateCommand;
import io.aegisops.execution.dto.AgentMemoryRecord;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqAgentMemoryRepository implements AgentMemoryRepository {
  private final DSLContext dsl;

  public JooqAgentMemoryRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void create(AgentMemoryCreateCommand command) {
    dsl.insertInto(AGENT_MEMORY)
        .set(AGENT_MEMORY.ID, command.id())
        .set(AGENT_MEMORY.TENANT_ID, command.tenantId())
        .set(AGENT_MEMORY.SCOPE_TYPE, command.scopeType())
        .set(AGENT_MEMORY.SCOPE_ID, command.scopeId())
        .set(AGENT_MEMORY.MEMORY_TYPE, command.memoryType())
        .set(AGENT_MEMORY.SOURCE_TYPE, command.sourceType())
        .set(AGENT_MEMORY.SOURCE_ID, command.sourceId())
        .set(AGENT_MEMORY.TITLE, command.title())
        .set(AGENT_MEMORY.CONTENT, command.content())
        .set(AGENT_MEMORY.TAGS, jsonbValue(command.tagsJson()))
        .set(AGENT_MEMORY.CONFIDENCE, BigDecimal.valueOf(command.confidence()))
        .set(AGENT_MEMORY.STATUS, command.status())
        .set(AGENT_MEMORY.CREATED_BY, command.createdBy())
        .set(AGENT_MEMORY.EXPIRES_AT, command.expiresAt())
        .set(AGENT_MEMORY.CREATED_AT, DSL.currentOffsetDateTime())
        .set(AGENT_MEMORY.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<AgentMemoryRecord> find(String tenantId, String memoryId) {
    return selectMemory()
        .where(AGENT_MEMORY.TENANT_ID.eq(tenantId))
        .and(AGENT_MEMORY.ID.eq(memoryId))
        .fetchOptional(this::toRecord);
  }

  @Override
  public List<AgentMemoryRecord> listActiveCandidates(
      String tenantId,
      String scopeType,
      String scopeId,
      List<String> memoryTypes,
      List<String> tags,
      int limit) {
    Condition condition =
        AGENT_MEMORY.TENANT_ID.eq(tenantId)
            .and(AGENT_MEMORY.STATUS.eq("active"))
            .and(
                AGENT_MEMORY.EXPIRES_AT.isNull()
                    .or(AGENT_MEMORY.EXPIRES_AT.gt(OffsetDateTime.now())));

    if (scopeType != null && !scopeType.isBlank()) {
      condition = condition.and(AGENT_MEMORY.SCOPE_TYPE.eq(scopeType));
    }

    if (scopeId != null && !scopeId.isBlank()) {
      condition = condition.and(AGENT_MEMORY.SCOPE_ID.eq(scopeId));
    }

    if (memoryTypes != null && !memoryTypes.isEmpty()) {
      condition = condition.and(AGENT_MEMORY.MEMORY_TYPE.in(memoryTypes));
    }

    if (tags != null && !tags.isEmpty()) {
      for (String tag : tags) {
        condition =
            condition.and(
                DSL.lower(AGENT_MEMORY.TAGS.cast(String.class)).contains(tag.toLowerCase()));
      }
    }

    return selectMemory()
        .where(condition)
        .orderBy(AGENT_MEMORY.CONFIDENCE.desc(), AGENT_MEMORY.CREATED_AT.desc())
        .limit(Math.max(1, Math.min(limit, 200)))
        .fetch(this::toRecord);
  }

  @Override
  public boolean archive(String tenantId, String memoryId) {
    return dsl.update(AGENT_MEMORY)
            .set(AGENT_MEMORY.STATUS, "archived")
            .set(AGENT_MEMORY.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(AGENT_MEMORY.TENANT_ID.eq(tenantId))
            .and(AGENT_MEMORY.ID.eq(memoryId))
            .and(AGENT_MEMORY.STATUS.eq("active"))
            .execute()
        > 0;
  }

  @Override
  public void createEvent(AgentMemoryEventCreateCommand command) {
    dsl.insertInto(AGENT_MEMORY_EVENT)
        .set(AGENT_MEMORY_EVENT.ID, command.id())
        .set(AGENT_MEMORY_EVENT.TENANT_ID, command.tenantId())
        .set(AGENT_MEMORY_EVENT.MEMORY_ID, command.memoryId())
        .set(AGENT_MEMORY_EVENT.EVENT_TYPE, command.eventType())
        .set(AGENT_MEMORY_EVENT.SUMMARY, command.summary())
        .set(AGENT_MEMORY_EVENT.ACTOR, command.actor())
        .set(AGENT_MEMORY_EVENT.METADATA, jsonbValue(command.metadataJson()))
        .set(AGENT_MEMORY_EVENT.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  private org.jooq.SelectJoinStep<org.jooq.Record> selectMemory() {
    return dsl.select(
            AGENT_MEMORY.ID,
            AGENT_MEMORY.TENANT_ID,
            AGENT_MEMORY.SCOPE_TYPE,
            AGENT_MEMORY.SCOPE_ID,
            AGENT_MEMORY.MEMORY_TYPE,
            AGENT_MEMORY.SOURCE_TYPE,
            AGENT_MEMORY.SOURCE_ID,
            AGENT_MEMORY.TITLE,
            AGENT_MEMORY.CONTENT,
            AGENT_MEMORY.TAGS.cast(String.class).as("tags_json"),
            AGENT_MEMORY.CONFIDENCE,
            AGENT_MEMORY.STATUS,
            AGENT_MEMORY.CREATED_BY,
            AGENT_MEMORY.EXPIRES_AT,
            AGENT_MEMORY.CREATED_AT,
            AGENT_MEMORY.UPDATED_AT)
        .from(AGENT_MEMORY);
  }

  private AgentMemoryRecord toRecord(org.jooq.Record record) {
    return new AgentMemoryRecord(
        record.get(AGENT_MEMORY.ID),
        record.get(AGENT_MEMORY.TENANT_ID),
        record.get(AGENT_MEMORY.SCOPE_TYPE),
        record.get(AGENT_MEMORY.SCOPE_ID),
        record.get(AGENT_MEMORY.MEMORY_TYPE),
        record.get(AGENT_MEMORY.SOURCE_TYPE),
        record.get(AGENT_MEMORY.SOURCE_ID),
        record.get(AGENT_MEMORY.TITLE),
        record.get(AGENT_MEMORY.CONTENT),
        record.get("tags_json", String.class),
        doubleValue(record.get(AGENT_MEMORY.CONFIDENCE)),
        record.get(AGENT_MEMORY.STATUS),
        record.get(AGENT_MEMORY.CREATED_BY),
        record.get(AGENT_MEMORY.EXPIRES_AT),
        record.get(AGENT_MEMORY.CREATED_AT),
        record.get(AGENT_MEMORY.UPDATED_AT));
  }

  private double doubleValue(BigDecimal value) {
    return value == null ? 0.0d : value.doubleValue();
  }
}
```

---

# 11. Memory Scorer

## 11.1 `AgentMemoryScorer.java`

路径：

```txt id="va9rcw"
modules/aiops-execution/src/main/java/io/aegisops/execution/AgentMemoryScorer.java
```

```java id="3gdob6"
package io.aegisops.execution;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AgentMemoryScorer {
  public double score(String query, String title, String content, double confidence) {
    double keyword = keywordScore(query, title + "\n" + content);
    return round(keyword * 0.75d + confidence * 0.25d);
  }

  private double keywordScore(String query, String content) {
    Set<String> queryTokens = tokens(query);
    Set<String> contentTokens = tokens(content);

    if (queryTokens.isEmpty() || contentTokens.isEmpty()) {
      return 0.0d;
    }

    int hit = 0;
    for (String token : queryTokens) {
      if (contentTokens.contains(token)) {
        hit++;
      }
    }

    return (double) hit / (double) queryTokens.size();
  }

  private Set<String> tokens(String text) {
    if (text == null || text.isBlank()) {
      return Set.of();
    }

    String[] parts =
        text.toLowerCase()
            .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", " ")
            .trim()
            .split("\\s+");

    Set<String> result = new HashSet<>();
    for (String part : parts) {
      if (!part.isBlank()) {
        result.add(part);
      }
    }
    return result;
  }

  private double round(double value) {
    return Math.round(value * 10000.0d) / 10000.0d;
  }
}
```

---

# 12. Java Service

## 12.1 `AgentMemoryService.java`

路径：

```txt id="bk16qa"
modules/aiops-execution/src/main/java/io/aegisops/execution/AgentMemoryService.java
```

```java id="o0t5ql"
package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.AgentMemoryCreateCommand;
import io.aegisops.execution.dto.AgentMemoryCreateRequest;
import io.aegisops.execution.dto.AgentMemoryEventCreateCommand;
import io.aegisops.execution.dto.AgentMemoryRecord;
import io.aegisops.execution.dto.AgentMemoryResponse;
import io.aegisops.execution.dto.AgentMemorySearchRequest;
import io.aegisops.execution.dto.AgentMemorySearchResponse;
import io.aegisops.execution.dto.AgentMemorySearchResult;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentMemoryService {
  private final AgentMemoryRepository repository;
  private final AgentMemoryPolicy policy;
  private final AgentMemoryJson json;
  private final AgentMemoryScorer scorer;

  public AgentMemoryService(
      AgentMemoryRepository repository,
      AgentMemoryPolicy policy,
      AgentMemoryJson json,
      AgentMemoryScorer scorer) {
    this.repository = repository;
    this.policy = policy;
    this.json = json;
    this.scorer = scorer;
  }

  @Transactional
  public AgentMemoryResponse createInternal(AgentMemoryCreateRequest request) {
    policy.validateCreate(request);

    String tenantId = request.tenantId().trim();
    String memoryId = newId("agm");

    repository.create(
        new AgentMemoryCreateCommand(
            memoryId,
            tenantId,
            policy.normalize(request.scopeType(), "tenant"),
            blankToNull(request.scopeId()),
            policy.normalize(request.memoryType(), "incident_summary"),
            policy.normalize(request.sourceType(), "diagnosis"),
            blankToNull(request.sourceId()),
            request.title().trim(),
            request.content().trim(),
            json.write(normalizeTags(request.tags())),
            normalizeConfidence(request.confidence()),
            "active",
            blankToDefault(request.createdBy(), "agent"),
            calculateExpiresAt(request.ttlSeconds())));

    repository.createEvent(
        new AgentMemoryEventCreateCommand(
            newId("agme"),
            tenantId,
            memoryId,
            "created",
            "Agent memory created",
            blankToDefault(request.createdBy(), "agent"),
            "{}"));

    return toResponse(load(tenantId, memoryId));
  }

  public AgentMemoryResponse get(String tenantId, String memoryId) {
    return toResponse(load(tenantId, memoryId));
  }

  @Transactional
  public AgentMemoryResponse archive(String tenantId, String memoryId) {
    AgentMemoryRecord memory = load(tenantId, memoryId);

    boolean updated = repository.archive(tenantId, memory.id());
    if (!updated) {
      throw new AppException("AGENT_MEMORY_ARCHIVE_FAILED", "Memory was not archived");
    }

    repository.createEvent(
        new AgentMemoryEventCreateCommand(
            newId("agme"),
            tenantId,
            memoryId,
            "archived",
            "Agent memory archived",
            "system",
            "{}"));

    return get(tenantId, memoryId);
  }

  @Transactional
  public AgentMemorySearchResponse searchInternal(AgentMemorySearchRequest request) {
    validateSearchRequest(request);

    String tenantId = request.tenantId().trim();
    int topK = normalizeTopK(request.topK());

    List<String> tags = normalizeTags(request.tags());
    List<String> memoryTypes = normalizeMemoryTypes(request.memoryTypes());

    List<AgentMemorySearchResult> results =
        repository
            .listActiveCandidates(
                tenantId,
                policy.normalize(request.scopeType(), ""),
                blankToNull(request.scopeId()),
                memoryTypes,
                tags,
                Math.max(topK * 20, 100))
            .stream()
            .map(memory -> toSearchResult(request.query(), memory))
            .filter(result -> result.score() > 0)
            .sorted(Comparator.comparingDouble(AgentMemorySearchResult::score).reversed())
            .limit(topK)
            .toList();

    repository.createEvent(
        new AgentMemoryEventCreateCommand(
            newId("agme"),
            tenantId,
            null,
            "searched",
            "Agent memory searched",
            blankToDefault(request.createdBy(), "agent"),
            json.write(
                java.util.Map.of(
                    "query", request.query(),
                    "resultCount", results.size(),
                    "topK", topK))));

    return new AgentMemorySearchResponse(request.query(), topK, results);
  }

  private AgentMemorySearchResult toSearchResult(String query, AgentMemoryRecord memory) {
    double score =
        scorer.score(
            query,
            memory.title(),
            memory.content(),
            memory.confidence());

    return new AgentMemorySearchResult(
        memory.id(),
        memory.scopeType(),
        memory.scopeId(),
        memory.memoryType(),
        memory.sourceType(),
        memory.sourceId(),
        memory.title(),
        memory.content(),
        json.readStringList(memory.tagsJson()),
        memory.confidence(),
        score,
        memory.createdBy());
  }

  private AgentMemoryRecord load(String tenantId, String memoryId) {
    return repository
        .find(tenantId, memoryId)
        .orElseThrow(() -> new AppException("AGENT_MEMORY_NOT_FOUND", "Agent memory not found"));
  }

  private void validateSearchRequest(AgentMemorySearchRequest request) {
    if (request == null) {
      throw new AppException("AGENT_MEMORY_SEARCH_REQUEST_REQUIRED", "Memory search request is required");
    }
    if (request.tenantId() == null || request.tenantId().isBlank()) {
      throw new AppException("AGENT_MEMORY_TENANT_REQUIRED", "Tenant id is required");
    }
    if (request.query() == null || request.query().isBlank()) {
      throw new AppException("AGENT_MEMORY_QUERY_REQUIRED", "Memory query is required");
    }
  }

  private List<String> normalizeMemoryTypes(List<String> memoryTypes) {
    if (memoryTypes == null || memoryTypes.isEmpty()) {
      return List.of();
    }

    return memoryTypes.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(value -> policy.normalize(value, ""))
        .distinct()
        .toList();
  }

  private List<String> normalizeTags(List<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return List.of();
    }

    return tags.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(this::normalizeTag)
        .filter(value -> !value.isBlank())
        .distinct()
        .toList();
  }

  private String normalizeTag(String value) {
    return value.trim()
        .toLowerCase()
        .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
        .replaceAll("^-+", "")
        .replaceAll("-+$", "");
  }

  private double normalizeConfidence(Double value) {
    if (value == null) {
      return 0.0d;
    }
    return Math.max(0.0d, Math.min(value, 1.0d));
  }

  private int normalizeTopK(Integer topK) {
    if (topK == null) {
      return 5;
    }
    return Math.max(1, Math.min(topK, 50));
  }

  private OffsetDateTime calculateExpiresAt(Integer ttlSeconds) {
    if (ttlSeconds == null || ttlSeconds <= 0) {
      return null;
    }
    return OffsetDateTime.now().plusSeconds(Math.min(ttlSeconds, 365 * 24 * 3600));
  }

  private AgentMemoryResponse toResponse(AgentMemoryRecord record) {
    return new AgentMemoryResponse(
        record.id(),
        record.scopeType(),
        record.scopeId(),
        record.memoryType(),
        record.sourceType(),
        record.sourceId(),
        record.title(),
        record.content(),
        json.readStringList(record.tagsJson()),
        record.confidence(),
        record.status(),
        record.createdBy(),
        record.expiresAt(),
        record.createdAt(),
        record.updatedAt());
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
```

---

# 13. Java Controller

## 13.1 `InternalAgentMemoryController.java`

路径：

```txt id="ollto5"
modules/aiops-execution/src/main/java/io/aegisops/execution/InternalAgentMemoryController.java
```

```java id="9js8vk"
package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.execution.dto.AgentMemoryCreateRequest;
import io.aegisops.execution.dto.AgentMemoryResponse;
import io.aegisops.execution.dto.AgentMemorySearchRequest;
import io.aegisops.execution.dto.AgentMemorySearchResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API for Python Agent memory.
 *
 * This API does not execute actions and must be protected by internal network/security policy.
 */
@RestController
public class InternalAgentMemoryController {
  private final AgentMemoryService service;

  public InternalAgentMemoryController(AgentMemoryService service) {
    this.service = service;
  }

  @PostMapping("/internal/agent/memories")
  public ApiResponse<AgentMemoryResponse> create(@RequestBody AgentMemoryCreateRequest request) {
    return ApiResponse.ok(service.createInternal(request));
  }

  @PostMapping("/internal/agent/memories/search")
  public ApiResponse<AgentMemorySearchResponse> search(@RequestBody AgentMemorySearchRequest request) {
    return ApiResponse.ok(service.searchInternal(request));
  }
}
```

---

## 13.2 `AgentMemoryController.java`

路径：

```txt id="6rkryi"
modules/aiops-execution/src/main/java/io/aegisops/execution/AgentMemoryController.java
```

```java id="ii45et"
package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.AgentMemoryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read/archive API for Agent Memory.
 */
@RestController
public class AgentMemoryController {
  private final AgentMemoryService service;

  public AgentMemoryController(AgentMemoryService service) {
    this.service = service;
  }

  @GetMapping("/api/agent-memories/{memoryId}")
  public ApiResponse<AgentMemoryResponse> get(@PathVariable String memoryId) {
    return ApiResponse.ok(service.get(TenantContext.requireTenantId(), memoryId));
  }

  @PostMapping("/api/agent-memories/{memoryId}/archive")
  public ApiResponse<AgentMemoryResponse> archive(@PathVariable String memoryId) {
    return ApiResponse.ok(service.archive(TenantContext.requireTenantId(), memoryId));
  }
}
```

---

# 14. Python Contract 修改

## 14.1 `contracts.py`

路径：

```txt id="ee38u0"
apps/aiops-agent/src/aiops_agent/workflow/contracts.py
```

增加：

```python id="nablwr"
class AgentMemory(BaseModel):
    memory_id: str
    title: str
    content: str
    memory_type: str
    scope_type: str = "tenant"
    scope_id: str | None = None
    score: float = 0.0
    confidence: float = 0.0
    tags: list[str] = Field(default_factory=list)
```

`DiagnosisRequest` 增加：

```python id="oq32ia"
enable_agent_memory: bool = False
enable_agent_memory_write: bool = False
```

`DiagnosisResponse` 增加：

```python id="p4tflr"
memories: list[AgentMemory] = Field(default_factory=list)
memory_write_status: str | None = None
```

完整关键片段：

```python id="u6o6ol"
class DiagnosisRequest(BaseModel):
    tenant_id: str
    incident_id: str
    title: str
    severity: Severity = "medium"
    description: str | None = None
    alert_summary: str | None = None
    tags: list[str] = Field(default_factory=list)
    enable_case_retrieval: bool = True
    enable_runbook_recommendation: bool = True
    enable_human_checkpoint: bool = False
    enable_multi_agent_collaboration: bool = False
    enable_agent_memory: bool = False
    enable_agent_memory_write: bool = False


class DiagnosisResponse(BaseModel):
    contract_version: str = "agent-diagnosis.v1"
    tenant_id: str
    incident_id: str
    summary: str
    root_cause: str
    confidence: float
    severity: Severity
    risk_level: RiskLevel
    evidence: list[EvidenceItem] = Field(default_factory=list)
    similar_cases: list[SimilarCase] = Field(default_factory=list)
    runbook_candidates: list[RunbookCandidate] = Field(default_factory=list)
    safety_notes: list[str] = Field(default_factory=list)
    next_steps: list[str] = Field(default_factory=list)
    checkpoint_required: bool = False
    checkpoint_id: str | None = None
    checkpoint_status: str | None = None
    agent_messages: list[AgentMessage] = Field(default_factory=list)
    memories: list[AgentMemory] = Field(default_factory=list)
    memory_write_status: str | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)
```

---

# 15. Python Settings 修改

## 15.1 `settings.py`

```python id="pv3mtg"
class AgentSettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="AIOPS_AGENT_", extra="ignore")

    evidence_api_base_url: str = "http://localhost:8080"
    knowledge_api_base_url: str = "http://localhost:8080"
    checkpoint_api_base_url: str = "http://localhost:8080"
    memory_api_base_url: str = "http://localhost:8080"
    request_timeout_seconds: float = 5.0
    graph_version: str = "phase7.3-agent-memory"
    max_evidence_items: int = 8
    max_similar_cases: int = 5
    max_memories: int = 5
    checkpoint_ttl_seconds: int = 86400
    max_agent_messages: int = 20
```

---

# 16. Python State / Context 修改

## 16.1 `graph/state.py`

增加：

```python id="yg8s4f"
    enable_agent_memory: bool
    enable_agent_memory_write: bool
    memories: list[AgentMemory]
    memory_write_status: str | None
```

完整 import 调整：

```python id="eyrn25"
from aiops_agent.workflow.contracts import AgentMemory, AgentMessage, EvidenceItem, RunbookCandidate, SimilarCase
```

---

## 16.2 `graph/context.py`

```python id="o6zwpb"
from __future__ import annotations

from dataclasses import dataclass

from aiops_agent.workflow.tools.checkpoint_client import CheckpointClient
from aiops_agent.workflow.tools.evidence_client import EvidenceClient
from aiops_agent.workflow.tools.knowledge_client import KnowledgeClient
from aiops_agent.workflow.tools.memory_client import MemoryClient


@dataclass(slots=True)
class GraphContext:
    evidence_client: EvidenceClient
    knowledge_client: KnowledgeClient
    checkpoint_client: CheckpointClient | None = None
    memory_client: MemoryClient | None = None
```

---

# 17. Python Memory Client

## 17.1 `tools/memory_client.py`

路径：

```txt id="tj4grw"
apps/aiops-agent/src/aiops_agent/workflow/tools/memory_client.py
```

```python id="iu8a3c"
from __future__ import annotations

from typing import Any

import httpx

from aiops_agent.workflow.contracts import AgentMemory
from aiops_agent.workflow.errors import ToolError
from aiops_agent.settings import settings


class MemoryClient:
    def __init__(self, base_url: str | None = None, timeout: float | None = None):
        self.base_url = (base_url or settings.memory_api_base_url).rstrip("/")
        self.timeout = timeout or settings.request_timeout_seconds

    async def search_memories(
        self,
        tenant_id: str,
        query: str,
        scope_type: str = "tenant",
        scope_id: str | None = None,
        tags: list[str] | None = None,
        memory_types: list[str] | None = None,
        top_k: int = 5,
    ) -> list[AgentMemory]:
        url = f"{self.base_url}/internal/agent/memories/search"
        body = {
            "tenantId": tenant_id,
            "query": query,
            "scopeType": scope_type,
            "scopeId": scope_id,
            "memoryTypes": memory_types or [],
            "tags": tags or [],
            "topK": top_k,
            "createdBy": "agent",
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.post(url, json=body, headers={"X-Tenant-Id": tenant_id})
                response.raise_for_status()
                payload = response.json()
        except (httpx.HTTPError, ValueError, TypeError) as exc:
            raise ToolError("MEMORY_SEARCH_FAILED", f"Failed to search memories: {exc}") from exc

        data = self._extract_data(payload)
        results = data.get("results", [])
        if not isinstance(results, list):
            raise ToolError("MEMORY_RESPONSE_INVALID", "memory search results must be array")

        return [self._to_memory(item) for item in results if isinstance(item, dict)]

    async def create_memory(
        self,
        tenant_id: str,
        scope_type: str,
        scope_id: str | None,
        memory_type: str,
        source_type: str,
        source_id: str | None,
        title: str,
        content: str,
        tags: list[str],
        confidence: float,
    ) -> AgentMemory:
        url = f"{self.base_url}/internal/agent/memories"
        body = {
            "tenantId": tenant_id,
            "scopeType": scope_type,
            "scopeId": scope_id,
            "memoryType": memory_type,
            "sourceType": source_type,
            "sourceId": source_id,
            "title": title,
            "content": content,
            "tags": tags,
            "confidence": max(0.0, min(confidence, 1.0)),
            "createdBy": "agent",
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.post(url, json=body, headers={"X-Tenant-Id": tenant_id})
                response.raise_for_status()
                payload = response.json()
        except (httpx.HTTPError, ValueError, TypeError) as exc:
            raise ToolError("MEMORY_CREATE_FAILED", f"Failed to create memory: {exc}") from exc

        data = self._extract_data(payload)
        return self._to_memory(data)

    def _extract_data(self, payload: Any) -> dict[str, Any]:
        if not isinstance(payload, dict):
            raise ToolError("MEMORY_RESPONSE_INVALID", "memory response must be object")

        data = payload.get("data", payload)
        if not isinstance(data, dict):
            raise ToolError("MEMORY_RESPONSE_INVALID", "memory response data must be object")
        return data

    def _to_memory(self, item: dict[str, Any]) -> AgentMemory:
        return AgentMemory(
            memory_id=str(item.get("id") or item.get("memoryId") or item.get("memory_id") or ""),
            title=str(item.get("title") or ""),
            content=str(item.get("content") or ""),
            memory_type=str(item.get("memoryType") or item.get("memory_type") or ""),
            scope_type=str(item.get("scopeType") or item.get("scope_type") or "tenant"),
            scope_id=item.get("scopeId") or item.get("scope_id"),
            score=float(item.get("score") or 0.0),
            confidence=float(item.get("confidence") or 0.0),
            tags=item.get("tags") if isinstance(item.get("tags"), list) else [],
        )
```

---

# 18. Python Memory Graph

## 18.1 `graph/memory_graph.py`

路径：

```txt id="8fi7mn"
apps/aiops-agent/src/aiops_agent/workflow/graph/memory_graph.py
```

```python id="tlk0kp"
from __future__ import annotations

from aiops_agent.workflow.contracts import AgentMemory
from aiops_agent.workflow.errors import ToolError
from aiops_agent.workflow.graph.evidence_graph import build_evidence_query
from aiops_agent.workflow.graph.state import DiagnosisGraphState
from aiops_agent.settings import settings
from aiops_agent.workflow.tools.memory_client import MemoryClient


async def retrieve_memory_node(state: DiagnosisGraphState, memory_client: MemoryClient | None) -> DiagnosisGraphState:
    if not state.get("enable_agent_memory", False):
        state["memories"] = []
        return state

    client = memory_client or MemoryClient()
    query = build_evidence_query(state)

    try:
        memories = await client.search_memories(
            tenant_id=state["tenant_id"],
            query=query,
            scope_type="tenant",
            scope_id=None,
            tags=state.get("tags", []),
            memory_types=[],
            top_k=settings.max_memories,
        )
    except ToolError as exc:
        state["memories"] = []
        state["safety_notes"] = state.get("safety_notes", []) + [
            f"Memory retrieval failed: {exc.message}"
        ]
        return state
    except Exception as exc:
        state["memories"] = []
        state["safety_notes"] = state.get("safety_notes", []) + [
            f"Memory retrieval failed: {exc}"
        ]
        return state

    state["memories"] = memories[: settings.max_memories]
    return state


async def write_memory_node(state: DiagnosisGraphState, memory_client: MemoryClient | None) -> DiagnosisGraphState:
    if not state.get("enable_agent_memory_write", False):
        state["memory_write_status"] = "skipped"
        return state

    if not _should_write_memory(state):
        state["memory_write_status"] = "rejected_by_policy"
        return state

    client = memory_client or MemoryClient()

    try:
        await client.create_memory(
            tenant_id=state["tenant_id"],
            scope_type="tenant",
            scope_id=None,
            memory_type="root_cause_pattern",
            source_type="diagnosis",
            source_id=state["incident_id"],
            title=f"Root cause pattern: {state.get('title', state['incident_id'])}",
            content=_build_memory_content(state),
            tags=state.get("tags", []),
            confidence=float(state.get("confidence", 0.0)),
        )
    except ToolError as exc:
        state["memory_write_status"] = "failed"
        state["safety_notes"] = state.get("safety_notes", []) + [
            f"Memory write failed: {exc.message}"
        ]
        return state
    except Exception as exc:
        state["memory_write_status"] = "failed"
        state["safety_notes"] = state.get("safety_notes", []) + [
            f"Memory write failed: {exc}"
        ]
        return state

    state["memory_write_status"] = "created"
    return state


def enrich_root_cause_with_memory(
    root_cause: str,
    memories: list[AgentMemory],
) -> str:
    if not memories:
        return root_cause

    top = memories[0]
    if top.score < 0.5:
        return root_cause

    return f"{root_cause}. Related memory: {top.title}"


def _should_write_memory(state: DiagnosisGraphState) -> bool:
    confidence = float(state.get("confidence", 0.0))
    root_cause = state.get("root_cause", "")

    if confidence < 0.60:
        return False

    if not root_cause or root_cause == "Root cause is not confirmed":
        return False

    if state.get("checkpoint_status") in {"pending", "rejected", "failed"}:
        return False

    return True


def _build_memory_content(state: DiagnosisGraphState) -> str:
    runbooks = state.get("runbook_candidates", [])
    runbook_titles = [item.title for item in runbooks[:3]]

    return (
        f"Incident: {state.get('title', '')}\n"
        f"Severity: {state.get('severity', 'medium')}\n"
        f"Root Cause: {state.get('root_cause', '')}\n"
        f"Confidence: {state.get('confidence', 0.0)}\n"
        f"Risk Level: {state.get('risk_level', 'medium')}\n"
        f"Suggested Runbooks: {', '.join(runbook_titles)}\n"
    )
```

---

# 19. 修改 RCA Agent / RCA Graph 使用 Memory

## 19.1 修改 `collaboration/rca_agent.py`

在 `propose(...)` 参数加 `memories`：

```python id="z1rups"
from aiops_agent.workflow.contracts import AgentMemory
```

替换 `propose(...)`：

```python id="221wqf"
    def propose(
        self,
        title: str,
        evidence: list[EvidenceItem],
        similar_cases: list[SimilarCase],
        memories: list[AgentMemory] | None = None,
        evidence_message: AgentMessage | None = None,
    ) -> tuple[str, float, AgentMessage]:
        memories = memories or []
        root_cause = self._infer_root_cause(title, evidence, similar_cases, memories)
        confidence = self._infer_confidence(evidence, similar_cases, memories, evidence_message)

        message = new_agent_message(
            role=self.role,
            title="RCA proposal",
            content=(
                f"Proposed root cause: {root_cause}\n"
                f"Confidence: {confidence:.2f}\n"
                f"Reason: inferred from evidence, similar cases, and agent memory."
            ),
            confidence=confidence,
            metadata={
                "root_cause": root_cause,
                "evidence_count": len(evidence),
                "similar_case_count": len(similar_cases),
                "memory_count": len(memories),
            },
        )

        return root_cause, confidence, message
```

替换 `_infer_root_cause(...)`：

```python id="fis25v"
    def _infer_root_cause(
        self,
        title: str,
        evidence: list[EvidenceItem],
        similar_cases: list[SimilarCase],
        memories: list[AgentMemory],
    ) -> str:
        for case in similar_cases:
            if case.root_cause:
                return case.root_cause

        for memory in memories:
            if memory.memory_type == "root_cause_pattern" and memory.score >= 0.5:
                return f"Possible recurring pattern: {memory.title}"

        text = " ".join(
            [title, *[item.title for item in evidence], *[item.summary for item in evidence]]
        ).lower()

        if "timeout" in text or "latency" in text:
            return "Possible timeout or dependency latency issue"
        if "5xx" in text or "error rate" in text:
            return "Possible service error rate increase"
        if "cpu" in text or "memory" in text:
            return "Possible resource saturation"

        return "Root cause is not confirmed"
```

替换 `_infer_confidence(...)`：

```python id="xbyp5u"
    def _infer_confidence(
        self,
        evidence: list[EvidenceItem],
        similar_cases: list[SimilarCase],
        memories: list[AgentMemory],
        evidence_message: AgentMessage | None,
    ) -> float:
        score = 0.35
        score += min(len(evidence), 5) * 0.08
        score += min(len(similar_cases), 3) * 0.08
        score += min(len(memories), 2) * 0.04
        if evidence_message is not None:
            score = max(score, evidence_message.confidence)
        return round(min(score, 0.9), 2)
```

---

## 19.2 修改 `graph/multi_agent_graph.py`

把 `RCAAgent.propose(...)` 调用改成：

```python id="3tbjv8"
    root_cause, confidence, rca_message = rca_agent.propose(
        title=state.get("title", ""),
        evidence=state.get("evidence", []),
        similar_cases=state.get("similar_cases", []),
        memories=state.get("memories", []),
        evidence_message=evidence_message,
    )
```

---

# 20. 修改 Orchestrator

## 20.1 `graph/orchestrator.py`

核心变化：

```txt id="ytcqj1"
memory_retrieval -> evidence -> case_retrieval
final_report -> memory_write
```

完整替换：

```python id="09ejr2"
from __future__ import annotations

from langgraph.graph import END, StateGraph

from aiops_agent.workflow.contracts import DiagnosisRequest, DiagnosisResponse, DiagnosisResumeRequest
from aiops_agent.workflow.graph.case_retrieval_graph import retrieve_cases_node
from aiops_agent.workflow.graph.context import GraphContext
from aiops_agent.workflow.graph.evidence_graph import fetch_evidence_node
from aiops_agent.workflow.graph.final_report_graph import final_report_node
from aiops_agent.workflow.graph.human_checkpoint_graph import human_checkpoint_node
from aiops_agent.workflow.graph.memory_graph import retrieve_memory_node, write_memory_node
from aiops_agent.workflow.graph.multi_agent_graph import (
    multi_agent_rca_node,
    multi_agent_recommendation_node,
)
from aiops_agent.workflow.graph.rca_graph import analyze_rca_node
from aiops_agent.workflow.graph.runbook_graph import recommend_runbook_node
from aiops_agent.workflow.graph.safety_graph import safety_review_node
from aiops_agent.workflow.graph.state import DiagnosisGraphState


def build_diagnosis_graph(context: GraphContext):
    graph = StateGraph(DiagnosisGraphState)

    async def memory_retrieval_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
        return await retrieve_memory_node(state, context.memory_client)

    async def memory_write_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
        return await write_memory_node(state, context.memory_client)

    async def evidence_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
        return await fetch_evidence_node(state, context)

    async def case_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
        return await retrieve_cases_node(state, context)

    async def checkpoint_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
        return await human_checkpoint_node(state, context)

    graph.add_node("memory_retrieval", memory_retrieval_node)
    graph.add_node("evidence", evidence_node)
    graph.add_node("case_retrieval", case_node)
    graph.add_node("rca", analyze_rca_node)
    graph.add_node("multi_agent_rca", multi_agent_rca_node)
    graph.add_node("human_checkpoint", checkpoint_node)
    graph.add_node("runbook", recommend_runbook_node)
    graph.add_node("multi_agent_recommendation", multi_agent_recommendation_node)
    graph.add_node("safety", safety_review_node)
    graph.add_node("final_report", final_report_node)
    graph.add_node("memory_write", memory_write_node)

    graph.set_entry_point("memory_retrieval")
    graph.add_edge("memory_retrieval", "evidence")
    graph.add_edge("evidence", "case_retrieval")

    graph.add_conditional_edges(
        "case_retrieval",
        _route_to_rca,
        {
            "multi_agent_rca": "multi_agent_rca",
            "rca": "rca",
        },
    )

    graph.add_edge("rca", "human_checkpoint")
    graph.add_edge("multi_agent_rca", "human_checkpoint")

    graph.add_conditional_edges(
        "human_checkpoint",
        _route_after_checkpoint,
        {
            "multi_agent_recommendation": "multi_agent_recommendation",
            "runbook": "runbook",
            "final_report": "final_report",
        },
    )

    graph.add_edge("runbook", "safety")
    graph.add_edge("safety", "final_report")
    graph.add_edge("multi_agent_recommendation", "final_report")
    graph.add_edge("final_report", "memory_write")
    graph.add_edge("memory_write", END)

    return graph.compile()


def build_resume_graph(context: GraphContext):
    graph = StateGraph(DiagnosisGraphState)

    async def memory_write_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
        return await write_memory_node(state, context.memory_client)

    graph.add_node("resume_router", _identity_node)
    graph.add_node("runbook", recommend_runbook_node)
    graph.add_node("multi_agent_recommendation", multi_agent_recommendation_node)
    graph.add_node("safety", safety_review_node)
    graph.add_node("final_report", final_report_node)
    graph.add_node("memory_write", memory_write_node)

    graph.set_entry_point("resume_router")
    graph.add_conditional_edges(
        "resume_router",
        _route_after_resume,
        {
            "multi_agent_recommendation": "multi_agent_recommendation",
            "runbook": "runbook",
        },
    )
    graph.add_edge("runbook", "safety")
    graph.add_edge("safety", "final_report")
    graph.add_edge("multi_agent_recommendation", "final_report")
    graph.add_edge("final_report", "memory_write")
    graph.add_edge("memory_write", END)

    return graph.compile()


async def run_diagnosis_graph(
    request: DiagnosisRequest,
    context: GraphContext,
) -> DiagnosisResponse:
    app = build_diagnosis_graph(context)
    initial_state: DiagnosisGraphState = {
        "tenant_id": request.tenant_id,
        "incident_id": request.incident_id,
        "title": request.title,
        "severity": request.severity,
        "description": request.description,
        "alert_summary": request.alert_summary,
        "tags": request.tags,
        "enable_case_retrieval": request.enable_case_retrieval,
        "enable_runbook_recommendation": request.enable_runbook_recommendation,
        "enable_human_checkpoint": request.enable_human_checkpoint,
        "enable_multi_agent_collaboration": request.enable_multi_agent_collaboration,
        "enable_agent_memory": request.enable_agent_memory,
        "enable_agent_memory_write": request.enable_agent_memory_write,
        "checkpoint_required": False,
        "checkpoint": None,
        "checkpoint_status": None,
        "resume_token": None,
        "evidence": [],
        "similar_cases": [],
        "memories": [],
        "runbook_candidates": [],
        "safety_notes": [],
        "next_steps": [],
        "agent_messages": [],
        "memory_write_status": None,
        "metadata": {},
    }

    final_state = await app.ainvoke(initial_state)
    return _to_response(final_state)


async def resume_diagnosis_graph(
    request: DiagnosisResumeRequest,
    context: GraphContext,
) -> DiagnosisResponse:
    checkpoint = await context.checkpoint_client.get_checkpoint(
        tenant_id=request.tenant_id,
        checkpoint_id=request.checkpoint_id,
        resume_token=request.resume_token,
    )

    state = DiagnosisGraphState(**checkpoint.state_snapshot)
    state["checkpoint"] = checkpoint.checkpoint_id
    state["resume_token"] = checkpoint.resume_token
    state["checkpoint_status"] = checkpoint.status
    state["checkpoint_required"] = False
    state.setdefault("agent_messages", [])
    state.setdefault("memories", [])
    state.setdefault("memory_write_status", None)

    if checkpoint.status == "rejected":
        state["runbook_candidates"] = []
        state["safety_notes"] = state.get("safety_notes", []) + [
            "Human checkpoint rejected. Runbook recommendation is stopped."
        ]
        state["next_steps"] = ["Revise diagnosis or collect additional evidence."]
        final_report_node(state)
        return _to_response(state)

    if checkpoint.status != "approved":
        state["checkpoint_required"] = True
        state["safety_notes"] = state.get("safety_notes", []) + [
            f"Checkpoint is not approved. Current status={checkpoint.status}."
        ]
        final_report_node(state)
        return _to_response(state)

    app = build_resume_graph(context)
    final_state = await app.ainvoke(state)
    return _to_response(final_state)


def _route_to_rca(state: DiagnosisGraphState) -> str:
    if state.get("enable_multi_agent_collaboration", False):
        return "multi_agent_rca"
    return "rca"


def _route_after_checkpoint(state: DiagnosisGraphState) -> str:
    status = state.get("checkpoint_status")
    if status not in {"approved", "skipped"}:
        return "final_report"

    if state.get("enable_multi_agent_collaboration", False):
        return "multi_agent_recommendation"

    return "runbook"


def _route_after_resume(state: DiagnosisGraphState) -> str:
    if state.get("enable_multi_agent_collaboration", False):
        return "multi_agent_recommendation"
    return "runbook"


def _identity_node(state: DiagnosisGraphState) -> DiagnosisGraphState:
    return state


def _to_response(final_state: DiagnosisGraphState) -> DiagnosisResponse:
    return DiagnosisResponse(
        tenant_id=final_state["tenant_id"],
        incident_id=final_state["incident_id"],
        summary=final_state.get("final_summary", ""),
        root_cause=final_state.get("root_cause", "Root cause is not confirmed"),
        confidence=float(final_state.get("confidence", 0.0)),
        severity=final_state.get("severity", "medium"),
        risk_level=final_state.get("risk_level", "medium"),
        evidence=final_state.get("evidence", []),
        similar_cases=final_state.get("similar_cases", []),
        runbook_candidates=final_state.get("runbook_candidates", []),
        safety_notes=final_state.get("safety_notes", []),
        next_steps=final_state.get("next_steps", []),
        checkpoint_required=bool(final_state.get("checkpoint_required", False)),
        checkpoint_id=final_state.get("checkpoint"),
        checkpoint_status=final_state.get("checkpoint_status"),
        agent_messages=final_state.get("agent_messages", []),
        memories=final_state.get("memories", []),
        memory_write_status=final_state.get("memory_write_status"),
        metadata=final_state.get("metadata", {}),
    )
```

---

# 21. 修改 DiagnosisService

## 21.1 `services/diagnosis_service.py`

```python id="xx40gz"
from __future__ import annotations

from aiops_agent.workflow.contracts import DiagnosisRequest, DiagnosisResponse, DiagnosisResumeRequest
from aiops_agent.workflow.graph.context import GraphContext
from aiops_agent.workflow.graph.orchestrator import resume_diagnosis_graph, run_diagnosis_graph
from aiops_agent.workflow.tools.checkpoint_client import CheckpointClient
from aiops_agent.workflow.tools.evidence_client import EvidenceClient
from aiops_agent.workflow.tools.knowledge_client import KnowledgeClient
from aiops_agent.workflow.tools.memory_client import MemoryClient


class DiagnosisService:
    def __init__(self, context: GraphContext | None = None):
        self.context = context or GraphContext(
            evidence_client=EvidenceClient(),
            knowledge_client=KnowledgeClient(),
            checkpoint_client=CheckpointClient(),
            memory_client=MemoryClient(),
        )

    async def diagnose(self, request: DiagnosisRequest) -> DiagnosisResponse:
        return await run_diagnosis_graph(request, self.context)

    async def resume(self, request: DiagnosisResumeRequest) -> DiagnosisResponse:
        return await resume_diagnosis_graph(request, self.context)
```

---

# 22. Java 单元测试

## 22.1 `AgentMemoryPolicyTest.java`

```java id="5gnv7j"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.AgentMemoryCreateRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentMemoryPolicyTest {
  private final AgentMemoryPolicy policy = new AgentMemoryPolicy();

  @Test
  void allowSafeMemory() {
    assertDoesNotThrow(
        () ->
            policy.validateCreate(
                new AgentMemoryCreateRequest(
                    "tenant_1",
                    "tenant",
                    null,
                    "root_cause_pattern",
                    "diagnosis",
                    "inc_1",
                    "Redis timeout pattern",
                    "Order service has recurring redis timeout pattern.",
                    List.of("redis", "timeout"),
                    0.8,
                    null,
                    "agent")));
  }

  @Test
  void rejectSecretLikeMemory() {
    assertThrows(
        AppException.class,
        () ->
            policy.validateCreate(
                new AgentMemoryCreateRequest(
                    "tenant_1",
                    "tenant",
                    null,
                    "root_cause_pattern",
                    "diagnosis",
                    "inc_1",
                    "Secret",
                    "password=123456",
                    List.of(),
                    0.8,
                    null,
                    "agent")));
  }

  @Test
  void rejectInvalidMemoryType() {
    assertThrows(
        AppException.class,
        () ->
            policy.validateCreate(
                new AgentMemoryCreateRequest(
                    "tenant_1",
                    "tenant",
                    null,
                    "free_form",
                    "diagnosis",
                    "inc_1",
                    "Invalid",
                    "content",
                    List.of(),
                    0.8,
                    null,
                    "agent")));
  }
}
```

---

## 22.2 `AgentMemoryServiceTest.java`

```java id="2t9uep"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.dto.AgentMemoryCreateCommand;
import io.aegisops.execution.dto.AgentMemoryCreateRequest;
import io.aegisops.execution.dto.AgentMemoryEventCreateCommand;
import io.aegisops.execution.dto.AgentMemoryRecord;
import io.aegisops.execution.dto.AgentMemorySearchRequest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentMemoryServiceTest {
  @Test
  void createAndSearchMemory() {
    FakeAgentMemoryRepository repository = new FakeAgentMemoryRepository();
    AgentMemoryJson json = new AgentMemoryJson(new ObjectMapper());

    AgentMemoryService service =
        new AgentMemoryService(
            repository,
            new AgentMemoryPolicy(),
            json,
            new AgentMemoryScorer());

    var created =
        service.createInternal(
            new AgentMemoryCreateRequest(
                "tenant_1",
                "tenant",
                null,
                "root_cause_pattern",
                "diagnosis",
                "inc_1",
                "Redis timeout pattern",
                "Order service redis timeout caused 5xx.",
                List.of("redis", "timeout"),
                0.8,
                null,
                "agent"));

    assertEquals("active", created.status());

    var result =
        service.searchInternal(
            new AgentMemorySearchRequest(
                "tenant_1",
                "redis timeout",
                "tenant",
                null,
                List.of("root_cause_pattern"),
                List.of("redis"),
                5,
                "agent"));

    assertEquals(1, result.results().size());
    assertTrue(result.results().get(0).score() > 0);
  }

  @Test
  void archiveMemory() {
    FakeAgentMemoryRepository repository = new FakeAgentMemoryRepository();
    AgentMemoryService service =
        new AgentMemoryService(
            repository,
            new AgentMemoryPolicy(),
            new AgentMemoryJson(new ObjectMapper()),
            new AgentMemoryScorer());

    var created =
        service.createInternal(
            new AgentMemoryCreateRequest(
                "tenant_1",
                "tenant",
                null,
                "safety_note",
                "diagnosis",
                "inc_1",
                "Do not restart",
                "Do not restart during data migration.",
                List.of("safety"),
                0.9,
                null,
                "agent"));

    var archived = service.archive("tenant_1", created.id());

    assertEquals("archived", archived.status());
  }

  private static class FakeAgentMemoryRepository implements AgentMemoryRepository {
    AgentMemoryRecord memory;
    final List<AgentMemoryEventCreateCommand> events = new ArrayList<>();

    @Override
    public void create(AgentMemoryCreateCommand command) {
      memory =
          new AgentMemoryRecord(
              command.id(),
              command.tenantId(),
              command.scopeType(),
              command.scopeId(),
              command.memoryType(),
              command.sourceType(),
              command.sourceId(),
              command.title(),
              command.content(),
              command.tagsJson(),
              command.confidence(),
              command.status(),
              command.createdBy(),
              command.expiresAt(),
              OffsetDateTime.now(),
              OffsetDateTime.now());
    }

    @Override
    public Optional<AgentMemoryRecord> find(String tenantId, String memoryId) {
      return Optional.ofNullable(memory)
          .filter(item -> item.tenantId().equals(tenantId) && item.id().equals(memoryId));
    }

    @Override
    public List<AgentMemoryRecord> listActiveCandidates(
        String tenantId,
        String scopeType,
        String scopeId,
        List<String> memoryTypes,
        List<String> tags,
        int limit) {
      if (memory == null || !"active".equals(memory.status())) {
        return List.of();
      }
      return List.of(memory);
    }

    @Override
    public boolean archive(String tenantId, String memoryId) {
      if (memory == null || !memory.id().equals(memoryId)) {
        return false;
      }
      memory =
          new AgentMemoryRecord(
              memory.id(),
              memory.tenantId(),
              memory.scopeType(),
              memory.scopeId(),
              memory.memoryType(),
              memory.sourceType(),
              memory.sourceId(),
              memory.title(),
              memory.content(),
              memory.tagsJson(),
              memory.confidence(),
              "archived",
              memory.createdBy(),
              memory.expiresAt(),
              memory.createdAt(),
              OffsetDateTime.now());
      return true;
    }

    @Override
    public void createEvent(AgentMemoryEventCreateCommand command) {
      events.add(command);
    }
  }
}
```

---

## 22.3 `JooqAgentMemoryRepositoryGeneratedSqlTest.java`

```java id="qz0zix"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.AgentMemoryCreateCommand;
import io.aegisops.execution.dto.AgentMemoryEventCreateCommand;
import io.aegisops.persistence.JooqTestSupport;
import java.util.List;
import org.jooq.Query;
import org.junit.jupiter.api.Test;

class JooqAgentMemoryRepositoryGeneratedSqlTest {
  @Test
  void createMemoryAndEventUseTables() {
    var dsl = JooqTestSupport.dsl();
    var repository = new JooqAgentMemoryRepository(dsl);

    repository.create(
        new AgentMemoryCreateCommand(
            "agm_1",
            "tenant_1",
            "tenant",
            null,
            "root_cause_pattern",
            "diagnosis",
            "inc_1",
            "Redis timeout pattern",
            "Order service redis timeout.",
            "[\"redis\"]",
            0.8,
            "active",
            "agent",
            null));

    repository.createEvent(
        new AgentMemoryEventCreateCommand(
            "agme_1",
            "tenant_1",
            "agm_1",
            "created",
            "created",
            "agent",
            "{}"));

    String sql = renderedSql(dsl.queries());

    assertTrue(sql.contains("agent_memory"));
    assertTrue(sql.contains("agent_memory_event"));
  }

  private String renderedSql(List<Query> queries) {
    return queries.stream()
        .map(Query::getSQL)
        .reduce("", (a, b) -> a + "\n" + b)
        .toLowerCase();
  }
}
```

---

# 23. Python 单元测试

## 23.1 修改 `tests/fakes.py`

新增：

```python id="qn5a32"
from aiops_agent.workflow.contracts import AgentMemory


class FakeMemoryClient:
    def __init__(self, memories: list[AgentMemory] | None = None):
        self.memories = memories or []
        self.search_called = False
        self.create_called = False

    async def search_memories(
        self,
        tenant_id: str,
        query: str,
        scope_type: str = "tenant",
        scope_id: str | None = None,
        tags: list[str] | None = None,
        memory_types: list[str] | None = None,
        top_k: int = 5,
    ) -> list[AgentMemory]:
        self.search_called = True
        return self.memories[:top_k]

    async def create_memory(
        self,
        tenant_id: str,
        scope_type: str,
        scope_id: str | None,
        memory_type: str,
        source_type: str,
        source_id: str | None,
        title: str,
        content: str,
        tags: list[str],
        confidence: float,
    ) -> AgentMemory:
        self.create_called = True
        memory = AgentMemory(
            memory_id="agm_1",
            title=title,
            content=content,
            memory_type=memory_type,
            scope_type=scope_type,
            scope_id=scope_id,
            score=1.0,
            confidence=confidence,
            tags=tags,
        )
        self.memories.append(memory)
        return memory
```

---

## 23.2 `test_memory_graph.py`

路径：

```txt id="s2gdod"
apps/aiops-agent/tests/test_memory_graph.py
```

```python id="lr91dz"
from __future__ import annotations

import pytest

from aiops_agent.workflow.contracts import AgentMemory, RunbookCandidate
from aiops_agent.workflow.graph.memory_graph import retrieve_memory_node, write_memory_node
from tests.fakes import FakeMemoryClient


@pytest.mark.asyncio
async def test_retrieve_memory_disabled():
    client = FakeMemoryClient()
    state = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "title": "Order timeout",
        "tags": ["redis"],
        "enable_agent_memory": False,
    }

    result = await retrieve_memory_node(state, client)

    assert result["memories"] == []
    assert client.search_called is False


@pytest.mark.asyncio
async def test_retrieve_memory_enabled():
    client = FakeMemoryClient(
        [
            AgentMemory(
                memory_id="agm_1",
                title="Redis timeout pattern",
                content="Redis timeout caused order service 5xx.",
                memory_type="root_cause_pattern",
                score=0.9,
                confidence=0.8,
                tags=["redis"],
            )
        ]
    )

    state = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "title": "Order timeout",
        "tags": ["redis"],
        "enable_agent_memory": True,
    }

    result = await retrieve_memory_node(state, client)

    assert client.search_called is True
    assert len(result["memories"]) == 1


@pytest.mark.asyncio
async def test_write_memory_rejected_when_low_confidence():
    client = FakeMemoryClient()
    state = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "title": "Order timeout",
        "root_cause": "redis timeout",
        "confidence": 0.3,
        "enable_agent_memory_write": True,
    }

    result = await write_memory_node(state, client)

    assert result["memory_write_status"] == "rejected_by_policy"
    assert client.create_called is False


@pytest.mark.asyncio
async def test_write_memory_created_when_policy_allows():
    client = FakeMemoryClient()
    state = {
        "tenant_id": "tenant_1",
        "incident_id": "inc_1",
        "title": "Order timeout",
        "severity": "high",
        "root_cause": "redis timeout",
        "confidence": 0.8,
        "risk_level": "high",
        "tags": ["redis"],
        "runbook_candidates": [
            RunbookCandidate(
                title="Check redis latency",
                action_type="manual",
                target_type="service",
                reason="timeout",
            )
        ],
        "enable_agent_memory_write": True,
    }

    result = await write_memory_node(state, client)

    assert result["memory_write_status"] == "created"
    assert client.create_called is True
```

---

## 23.3 `test_memory_client.py`

路径：

```txt id="4n6tzh"
apps/aiops-agent/tests/test_memory_client.py
```

```python id="dwxi1x"
from __future__ import annotations

import pytest
import respx
from httpx import Response

from aiops_agent.workflow.tools.memory_client import MemoryClient


@pytest.mark.asyncio
@respx.mock
async def test_memory_client_search_parses_results():
    respx.post("http://java/internal/agent/memories/search").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "query": "redis timeout",
                    "topK": 5,
                    "results": [
                        {
                            "id": "agm_1",
                            "title": "Redis timeout pattern",
                            "content": "Redis timeout caused order service errors.",
                            "memoryType": "root_cause_pattern",
                            "scopeType": "tenant",
                            "score": 0.91,
                            "confidence": 0.8,
                            "tags": ["redis"],
                        }
                    ],
                }
            },
        )
    )

    client = MemoryClient(base_url="http://java")

    memories = await client.search_memories(
        tenant_id="tenant_1",
        query="redis timeout",
        tags=["redis"],
    )

    assert len(memories) == 1
    assert memories[0].memory_id == "agm_1"
    assert memories[0].memory_type == "root_cause_pattern"


@pytest.mark.asyncio
@respx.mock
async def test_memory_client_create_parses_response():
    respx.post("http://java/internal/agent/memories").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "id": "agm_1",
                    "title": "Redis timeout pattern",
                    "content": "Redis timeout caused order service errors.",
                    "memoryType": "root_cause_pattern",
                    "scopeType": "tenant",
                    "score": 0.0,
                    "confidence": 0.8,
                    "tags": ["redis"],
                }
            },
        )
    )

    client = MemoryClient(base_url="http://java")

    memory = await client.create_memory(
        tenant_id="tenant_1",
        scope_type="tenant",
        scope_id=None,
        memory_type="root_cause_pattern",
        source_type="diagnosis",
        source_id="inc_1",
        title="Redis timeout pattern",
        content="Redis timeout caused order service errors.",
        tags=["redis"],
        confidence=0.8,
    )

    assert memory.memory_id == "agm_1"
    assert memory.confidence == 0.8
```

---

## 23.4 修改 `test_multi_agent_graph.py`

新增 memory 影响 RCA 的测试：

```python id="506ybp"
from aiops_agent.workflow.contracts import AgentMemory
```

```python id="ydsmy2"
def test_multi_agent_rca_node_uses_memory_when_no_case_root_cause():
    state = {
        "title": "Order service timeout",
        "evidence": [],
        "similar_cases": [],
        "memories": [
            AgentMemory(
                memory_id="agm_1",
                title="Redis timeout pattern",
                content="Redis timeout caused order service errors before.",
                memory_type="root_cause_pattern",
                score=0.9,
                confidence=0.8,
                tags=["redis"],
            )
        ],
        "agent_messages": [],
    }

    result = multi_agent_rca_node(state)

    assert "recurring pattern" in result["root_cause"].lower()
    assert result["confidence"] > 0.3
```

---

## 23.5 修改 `test_phase7_orchestrator.py`

新增 memory end-to-end 测试：

```python id="f7erjp"
from aiops_agent.workflow.contracts import AgentMemory
from tests.fakes import FakeMemoryClient
```

```python id="i7nofk"
@pytest.mark.asyncio
async def test_orchestrator_retrieves_and_writes_memory():
    memory_client = FakeMemoryClient(
        [
            AgentMemory(
                memory_id="agm_1",
                title="Redis timeout pattern",
                content="Redis timeout caused order service errors before.",
                memory_type="root_cause_pattern",
                score=0.9,
                confidence=0.8,
                tags=["redis"],
            )
        ]
    )

    context = GraphContext(
        evidence_client=FakeEvidenceClient(),
        knowledge_client=FakeKnowledgeClient(),
        checkpoint_client=FakeCheckpointClient(),
        memory_client=memory_client,
    )

    response = await run_diagnosis_graph(
        DiagnosisRequest(
            tenant_id="tenant_1",
            incident_id="inc_1",
            title="Order service redis timeout",
            severity="high",
            tags=["redis"],
            enable_multi_agent_collaboration=True,
            enable_agent_memory=True,
            enable_agent_memory_write=True,
        ),
        context,
    )

    assert len(response.memories) == 1
    assert memory_client.search_called is True
    assert memory_client.create_called is True
    assert response.memory_write_status == "created"
    assert response.metadata["graph_version"] == "phase7.3-agent-memory"
```

---

# 24. 文档

路径：

```txt id="dfy3ig"
docs/mvp/design/phase7.3-agent-memory.md
```

````md id="sgvo6w"
# Phase7.3 Agent Memory

## 目标

为 Agent 提供受控、可审计、可检索的记忆能力。

## 不做

- 不记任意内容
- 不记隐私
- 不记密钥
- 不记原始日志全文
- 不跨租户共享
- 不创建 execution
- 不调 runner
- 不自动修复
- 不自动 rollback

## Memory Types

- incident_summary
- root_cause_pattern
- service_behavior
- runbook_hint
- safety_note

## Java Tables

- agent_memory
- agent_memory_event

## Python Graph

input
-> memory_retrieval_graph
-> evidence_graph
-> case_retrieval_graph
-> rca / multi_agent_rca
-> human_checkpoint_graph
-> runbook / multi_agent_recommendation
-> final_report_graph
-> memory_write_graph
-> output

## Request Flags

```json
{
  "enable_agent_memory": true,
  "enable_agent_memory_write": true
}
```
````

## 写入策略

Memory 只有满足以下条件才写入：

1. enable_agent_memory_write=true
2. confidence >= 0.60
3. root_cause 已确认
4. checkpoint 不处于 pending/rejected/failed
5. 内容不包含 password/token/secret/private key
6. content 长度不超过 4000

## 安全边界

Agent Memory 只影响诊断建议，不触发执行。

````

---

# 25. 验证命令

## Java

```powershell id="j8t2gy"
mvn -pl modules/aiops-persistence -am generate-sources
mvn -pl modules/aiops-execution -am test
mvn -pl apps/aiops-server -am test
````

---

## Python

```bash id="klmkvy"
cd apps/aiops-agent
pip install -e ".[test]"
pytest -q
```

---

# 26. 验收标准

```txt id="1n1okd"
1. agent_memory 表存在。
2. agent_memory_event 表存在。
3. internal API 可以创建 memory。
4. internal API 可以搜索 memory。
5. public API 可以查看 memory。
6. public API 可以归档 memory。
7. memory policy 会拒绝 secret-like 内容。
8. memory policy 会拒绝非法 memory_type。
9. Python Agent 可以检索 memory。
10. Python Agent 可以在策略允许时写入 memory。
11. enable_agent_memory=false 时不检索。
12. enable_agent_memory_write=false 时不写入。
13. low confidence 不写入。
14. pending/rejected checkpoint 不写入。
15. memory 会进入 DiagnosisResponse.memories。
16. memory_write_status 会进入 DiagnosisResponse。
17. Agent 不创建 execution。
18. Agent 不调 runner。
19. Agent 不执行 webhook / ansible / ssh。
20. Memory tenant 隔离。
```

---

# 27. 建议提交信息

```txt id="uf6ix9"
feat(agent): add controlled agent memory
```

---

# 28. 下一步 Phase8.0

Phase7.3 完成后，Phase7 结束。下一阶段进入：

```txt id="dqqd0f"
Phase8.0 SaaS Multi-tenant Hardening
```

Phase8.0 重点：

```txt id="m60fkj"
1. 租户资源隔离
2. 内部 API service auth
3. Agent tool token
4. rate limit
5. audit hardening
6. quota
7. helm/private deployment security baseline
```
