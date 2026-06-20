# Phase6.2：Knowledge Base & Vector Retrieval

> Phase6.2 目标：把 Phase6.1 的 **Incident Case Library** 变成可检索知识库。
> 本阶段做：**Case Chunking / Embedding / Hybrid Search / Agent 只读检索接口**。
> 不做：Agent Memory、不做 Prompt Eval、不做多 Agent、不引入执行能力。

---

# 1. Phase6.2 定位

前置阶段：

```txt id="w6c19s"
Phase6.0  Postmortem Report
Phase6.1  Incident Case Library
```

Phase6.2 新增：

```txt id="9n41x5"
kb_document
kb_chunk
kb_search_log

KnowledgeBaseIndexService
KnowledgeBaseSearchService
EmbeddingProvider
HashingEmbeddingProvider
KnowledgeBaseController
AgentKnowledgeToolController
```

核心链路：

```txt id="ek2o9s"
published incident_case
  -> index to kb_document
  -> split into kb_chunk
  -> generate embedding
  -> hybrid search
  -> return similar cases / chunks
  -> Agent 可通过只读 API 调用
```

---

# 2. 设计原则

## 2.1 Java / Python / Runner 边界

```txt id="m0q8h8"
Java = Knowledge Base / Case Index / Search API / 权限 / 租户 / 审计
Python = Agent Runtime / 可调用 Java 的 search_cases 工具
Runner = 不参与 Phase6.2
```

---

## 2.2 不引入执行能力

Phase6.2 是只读检索，不允许：

```txt id="swr4fj"
1. 触发 Runbook
2. 创建 Execution
3. 调 Runner
4. 执行 Webhook / Ansible / SSH
5. 自动修改 Incident
```

---

## 2.3 Vector Store 策略

为了 MVP 稳定，我建议第一版不用 pgvector / Milvus，而是：

```txt id="e2lb1w"
PostgreSQL jsonb 存 embedding
Java 内存 cosine rerank
关键词匹配 + 向量相似度混合排序
```

原因：

```txt id="u1wd4g"
1. 不增加 pgvector 扩展安装门槛
2. 不增加 Milvus 运维复杂度
3. 单租户 MVP 几千到几万 chunk 足够用
4. 后续 Phase6.2.1 可平滑替换为 pgvector
```

后续替换方向：

```txt id="tr4s2s"
kb_chunk.embedding jsonb
  -> vector(384) / vector(768)
  -> HNSW index
```

---

# 3. Phase6.2 API

## 3.1 Index API

```txt id="3nwv57"
POST /api/incident-cases/{caseId}/kb/index
GET  /api/knowledge-base/documents/{documentId}
GET  /api/knowledge-base/documents/source/{sourceType}/{sourceId}
```

---

## 3.2 Search API

```txt id="e4xyol"
POST /api/knowledge-base/search
```

请求：

```json id="voix6j"
{
  "query": "订单服务数据库超时导致错误率升高",
  "sourceTypes": ["incident_case"],
  "tags": ["order-service", "db-timeout"],
  "topK": 5,
  "createdBy": "alice"
}
```

返回：

```json id="h2m79s"
{
  "query": "订单服务数据库超时导致错误率升高",
  "results": [
    {
      "documentId": "kbd_xxx",
      "chunkId": "kbc_xxx",
      "sourceType": "incident_case",
      "sourceId": "icase_xxx",
      "title": "Postmortem - Order service error",
      "content": "Root Cause: db timeout...",
      "score": 0.83,
      "vectorScore": 0.78,
      "keywordScore": 0.2,
      "metadataJson": "{}"
    }
  ]
}
```

---

## 3.3 Agent Tool API

给 Python Agent 使用的只读接口：

```txt id="tk25rn"
POST /internal/agent/tools/search-cases
```

请求：

```json id="ze8nrf"
{
  "tenantId": "tenant_1",
  "query": "redis connection timeout after deploy",
  "topK": 5
}
```

返回和 Search API 一样。

---

# 4. Migration

路径：

```txt id="r6zim7"
apps/aiops-server/src/main/resources/db/migration/V22__phase6_2_knowledge_base_vector_retrieval.sql
```

```sql id="49jkar"
-- Phase 6.2: Knowledge Base & Vector Retrieval.
-- Portable MVP implementation:
-- - Embeddings are stored as jsonb numeric arrays.
-- - Java service performs cosine rerank.
-- - No pgvector / Milvus dependency in this phase.
-- - No execution capability is added.

create table if not exists kb_document (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  source_type varchar(64) not null,
  source_id varchar(64) not null,
  title varchar(240) not null,
  status varchar(32) not null default 'indexed',
  metadata jsonb not null default '{}'::jsonb,
  indexed_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_kb_document_source_type
    check (source_type in ('incident_case', 'postmortem', 'manual')),
  constraint ck_kb_document_status
    check (status in ('indexed', 'stale', 'disabled'))
);

create unique index if not exists uq_kb_document_source
  on kb_document(tenant_id, source_type, source_id);

create index if not exists idx_kb_document_status
  on kb_document(tenant_id, status, indexed_at desc);

create table if not exists kb_chunk (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  document_id varchar(64) not null references kb_document(id) on delete cascade,
  chunk_order int not null,
  source_type varchar(64) not null,
  source_id varchar(64) not null,
  title varchar(240) not null,
  content text not null,
  content_hash varchar(128) not null,
  token_estimate int not null default 0,
  embedding jsonb not null default '[]'::jsonb,
  metadata jsonb not null default '{}'::jsonb,
  status varchar(32) not null default 'indexed',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_kb_chunk_source_type
    check (source_type in ('incident_case', 'postmortem', 'manual')),
  constraint ck_kb_chunk_status
    check (status in ('indexed', 'stale', 'disabled')),
  constraint uq_kb_chunk_document_order unique (tenant_id, document_id, chunk_order)
);

create index if not exists idx_kb_chunk_document
  on kb_chunk(tenant_id, document_id, chunk_order);

create index if not exists idx_kb_chunk_source
  on kb_chunk(tenant_id, source_type, source_id);

create index if not exists idx_kb_chunk_content_hash
  on kb_chunk(tenant_id, content_hash);

create table if not exists kb_search_log (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  query text not null,
  source_types jsonb not null default '[]'::jsonb,
  tags jsonb not null default '[]'::jsonb,
  top_k int not null default 5,
  result_count int not null default 0,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  constraint ck_kb_search_log_top_k check (top_k >= 1 and top_k <= 50)
);

create index if not exists idx_kb_search_log_tenant_created
  on kb_search_log(tenant_id, created_at desc);
```

---

# 5. jOOQ Codegen

路径：

```txt id="s7b4zu"
modules/aiops-persistence/src/main/resources/jooq-codegen.xml
```

追加：

```txt id="x1spka"
kb_document | kb_chunk | kb_search_log
```

完整 includes：

```xml id="un7mav"
<includes>
  tenant | sys_user | sys_role | sys_permission | sys_user_role | sys_role_permission |
  datasource | datasource_sync_run | asset | asset_relation | alert_event | incident |
  incident_event | incident_timeline | audit_log | rca_analysis | ai_diagnosis |
  agent_run | agent_run_step | agent_eval_result | log_event | change_event |
  runbook | runbook_step_template | automation_plan | automation_plan_step |
  approval_policy | automation_approval | approval_decision |
  execution_run | execution_step | execution_artifact |
  webhook_connector | webhook_execution_policy |
  ansible_inventory | ansible_playbook | ansible_execution_policy | ansible_credential_ref |
  rollback_plan | rollback_plan_step | rollback_decision |
  execution_report | execution_report_section | execution_verification | execution_audit_event |
  postmortem_report | postmortem_section | postmortem_action_item |
  incident_case | incident_case_symptom | incident_case_resolution_step | incident_case_tag |
  kb_document | kb_chunk | kb_search_log
</includes>
```

---

# 6. DTO 完整代码

## 6.1 `KnowledgeBaseIndexResponse.java`

路径：

```txt id="g7qrji"
modules/aiops-execution/src/main/java/io/aegisops/execution/dto/KnowledgeBaseIndexResponse.java
```

```java id="f3thwf"
package io.aegisops.execution.dto;

public record KnowledgeBaseIndexResponse(
    String documentId,
    String sourceType,
    String sourceId,
    int chunkCount,
    String status) {}
```

---

## 6.2 `KnowledgeBaseDocumentCreateCommand.java`

```java id="sbjchx"
package io.aegisops.execution.dto;

public record KnowledgeBaseDocumentCreateCommand(
    String id,
    String tenantId,
    String sourceType,
    String sourceId,
    String title,
    String status,
    String metadataJson) {}
```

---

## 6.3 `KnowledgeBaseChunkCreateCommand.java`

```java id="sgl5oj"
package io.aegisops.execution.dto;

public record KnowledgeBaseChunkCreateCommand(
    String id,
    String tenantId,
    String documentId,
    int chunkOrder,
    String sourceType,
    String sourceId,
    String title,
    String content,
    String contentHash,
    int tokenEstimate,
    String embeddingJson,
    String metadataJson,
    String status) {}
```

---

## 6.4 `KnowledgeBaseDocumentRecord.java`

```java id="j9p53j"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record KnowledgeBaseDocumentRecord(
    String id,
    String tenantId,
    String sourceType,
    String sourceId,
    String title,
    String status,
    String metadataJson,
    OffsetDateTime indexedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 6.5 `KnowledgeBaseChunkRecord.java`

```java id="cjxojf"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record KnowledgeBaseChunkRecord(
    String id,
    String tenantId,
    String documentId,
    int chunkOrder,
    String sourceType,
    String sourceId,
    String title,
    String content,
    String contentHash,
    int tokenEstimate,
    String embeddingJson,
    String metadataJson,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 6.6 `KnowledgeBaseSearchRequest.java`

```java id="0m5ggh"
package io.aegisops.execution.dto;

import java.util.List;

public record KnowledgeBaseSearchRequest(
    String query,
    List<String> sourceTypes,
    List<String> tags,
    Integer topK,
    String createdBy) {}
```

---

## 6.7 `KnowledgeBaseSearchResponse.java`

```java id="fr56y8"
package io.aegisops.execution.dto;

import java.util.List;

public record KnowledgeBaseSearchResponse(
    String query,
    int topK,
    List<KnowledgeBaseSearchResult> results) {}
```

---

## 6.8 `KnowledgeBaseSearchResult.java`

```java id="jc5axv"
package io.aegisops.execution.dto;

public record KnowledgeBaseSearchResult(
    String documentId,
    String chunkId,
    String sourceType,
    String sourceId,
    String title,
    String content,
    double score,
    double vectorScore,
    double keywordScore,
    String metadataJson) {}
```

---

## 6.9 `KnowledgeBaseSearchLogCreateCommand.java`

```java id="fcrge2"
package io.aegisops.execution.dto;

public record KnowledgeBaseSearchLogCreateCommand(
    String id,
    String tenantId,
    String query,
    String sourceTypesJson,
    String tagsJson,
    int topK,
    int resultCount,
    String createdBy) {}
```

---

## 6.10 `AgentSearchCasesRequest.java`

```java id="dfdk47"
package io.aegisops.execution.dto;

import java.util.List;

public record AgentSearchCasesRequest(
    String tenantId,
    String query,
    List<String> tags,
    Integer topK) {}
```

---

# 7. JSON 工具

## 7.1 `KnowledgeBaseJson.java`

路径：

```txt id="2n6sze"
modules/aiops-execution/src/main/java/io/aegisops/execution/KnowledgeBaseJson.java
```

```java id="mffou8"
package io.aegisops.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.util.List;

public class KnowledgeBaseJson {
  private static final TypeReference<List<Double>> DOUBLE_LIST = new TypeReference<>() {};
  private final ObjectMapper objectMapper;

  public KnowledgeBaseJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      if (value == null) {
        return "{}";
      }
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new AppException("KB_JSON_WRITE_FAILED", "Failed to serialize knowledge base json");
    }
  }

  public List<Double> readDoubleList(String json) {
    try {
      if (json == null || json.isBlank()) {
        return List.of();
      }
      return objectMapper.readValue(json, DOUBLE_LIST);
    } catch (Exception ex) {
      return List.of();
    }
  }
}
```

---

# 8. Embedding Provider

## 8.1 `EmbeddingProvider.java`

路径：

```txt id="5pdwhj"
modules/aiops-execution/src/main/java/io/aegisops/execution/EmbeddingProvider.java
```

```java id="1xqmbv"
package io.aegisops.execution;

import java.util.List;

public interface EmbeddingProvider {
  int dimension();

  List<Double> embed(String text);
}
```

---

## 8.2 `HashingEmbeddingProvider.java`

路径：

```txt id="kw6z53"
modules/aiops-execution/src/main/java/io/aegisops/execution/HashingEmbeddingProvider.java
```

```java id="g7x24w"
package io.aegisops.execution;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HashingEmbeddingProvider implements EmbeddingProvider {
  private static final int DIMENSION = 128;

  @Override
  public int dimension() {
    return DIMENSION;
  }

  @Override
  public List<Double> embed(String text) {
    double[] vector = new double[DIMENSION];

    for (String token : tokenize(text)) {
      int index = positiveHash(token) % DIMENSION;
      vector[index] += 1.0d;
    }

    normalize(vector);

    List<Double> result = new ArrayList<>(DIMENSION);
    for (double value : vector) {
      result.add(value);
    }
    return result;
  }

  private List<String> tokenize(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }

    return List.of(
        text.toLowerCase()
            .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", " ")
            .trim()
            .split("\\s+"));
  }

  private int positiveHash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      int hash = 0;
      for (int i = 0; i < 4; i++) {
        hash = (hash << 8) | (bytes[i] & 0xff);
      }
      return hash & 0x7fffffff;
    } catch (Exception ex) {
      return Math.abs(value.hashCode());
    }
  }

  private void normalize(double[] vector) {
    double sum = 0.0d;
    for (double value : vector) {
      sum += value * value;
    }

    if (sum <= 0.0d) {
      return;
    }

    double norm = Math.sqrt(sum);
    for (int i = 0; i < vector.length; i++) {
      vector[i] = vector[i] / norm;
    }
  }
}
```

---

# 9. Vector Scoring

## 9.1 `KnowledgeBaseScorer.java`

路径：

```txt id="nms81p"
modules/aiops-execution/src/main/java/io/aegisops/execution/KnowledgeBaseScorer.java
```

```java id="r47dng"
package io.aegisops.execution;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBaseScorer {
  public double cosine(List<Double> left, List<Double> right) {
    if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
      return 0.0d;
    }

    int size = Math.min(left.size(), right.size());
    double dot = 0.0d;
    double leftNorm = 0.0d;
    double rightNorm = 0.0d;

    for (int i = 0; i < size; i++) {
      double l = left.get(i) == null ? 0.0d : left.get(i);
      double r = right.get(i) == null ? 0.0d : right.get(i);
      dot += l * r;
      leftNorm += l * l;
      rightNorm += r * r;
    }

    if (leftNorm <= 0.0d || rightNorm <= 0.0d) {
      return 0.0d;
    }

    return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
  }

  public double keywordScore(String query, String content) {
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

  public double hybridScore(double vectorScore, double keywordScore) {
    return vectorScore * 0.8d + keywordScore * 0.2d;
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

    Set<String> tokens = new HashSet<>();
    for (String part : parts) {
      if (!part.isBlank()) {
        tokens.add(part);
      }
    }
    return tokens;
  }
}
```

---

# 10. Chunk Builder

## 10.1 `KnowledgeBaseChunkDraft.java`

路径：

```txt id="anqhoe"
modules/aiops-execution/src/main/java/io/aegisops/execution/KnowledgeBaseChunkDraft.java
```

```java id="mn6uo0"
package io.aegisops.execution;

public record KnowledgeBaseChunkDraft(
    int chunkOrder,
    String title,
    String content,
    String metadataJson) {}
```

---

## 10.2 `IncidentCaseChunkBuilder.java`

路径：

```txt id="or06bx"
modules/aiops-execution/src/main/java/io/aegisops/execution/IncidentCaseChunkBuilder.java
```

```java id="fmrkbi"
package io.aegisops.execution;

import io.aegisops.execution.dto.IncidentCaseResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class IncidentCaseChunkBuilder {
  private final KnowledgeBaseJson json;

  public IncidentCaseChunkBuilder(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    this.json = new KnowledgeBaseJson(objectMapper);
  }

  public List<KnowledgeBaseChunkDraft> build(IncidentCaseResponse incidentCase) {
    List<KnowledgeBaseChunkDraft> chunks = new ArrayList<>();
    int order = 1;

    chunks.add(
        new KnowledgeBaseChunkDraft(
            order++,
            incidentCase.title() + " - summary",
            """
            Title: %s
            Severity: %s
            Summary: %s
            Root Cause: %s
            Resolution: %s
            Prevention: %s
            Tags: %s
            """
                .formatted(
                    value(incidentCase.title()),
                    value(incidentCase.severity()),
                    value(incidentCase.summary()),
                    value(incidentCase.rootCause()),
                    value(incidentCase.resolution()),
                    value(incidentCase.prevention()),
                    String.join(", ", incidentCase.tags())),
            json.write(Map.of("section", "summary"))));

    for (var symptom : incidentCase.symptoms()) {
      chunks.add(
          new KnowledgeBaseChunkDraft(
              order++,
              incidentCase.title() + " - symptom - " + symptom.name(),
              """
              Symptom Type: %s
              Symptom Name: %s
              Description: %s
              """
                  .formatted(
                      value(symptom.symptomType()),
                      value(symptom.name()),
                      value(symptom.description())),
              json.write(Map.of("section", "symptom", "symptomId", symptom.id()))));
    }

    for (var step : incidentCase.resolutionSteps()) {
      chunks.add(
          new KnowledgeBaseChunkDraft(
              order++,
              incidentCase.title() + " - resolution step " + step.stepOrder(),
              """
              Resolution Step: %s
              Action Type: %s
              Description: %s
              """
                  .formatted(
                      value(step.title()),
                      value(step.actionType()),
                      value(step.description())),
              json.write(Map.of("section", "resolution_step", "stepId", step.id()))));
    }

    return chunks;
  }

  private String value(String value) {
    return value == null ? "" : value;
  }
}
```

---

# 11. Repository 接口

## 11.1 `KnowledgeBaseRepository.java`

路径：

```txt id="y6xep9"
modules/aiops-execution/src/main/java/io/aegisops/execution/KnowledgeBaseRepository.java
```

```java id="6wtdzs"
package io.aegisops.execution;

import io.aegisops.execution.dto.KnowledgeBaseChunkCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseChunkRecord;
import io.aegisops.execution.dto.KnowledgeBaseDocumentCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseDocumentRecord;
import io.aegisops.execution.dto.KnowledgeBaseSearchLogCreateCommand;
import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository {
  void upsertDocument(KnowledgeBaseDocumentCreateCommand command);

  Optional<KnowledgeBaseDocumentRecord> findDocument(String tenantId, String documentId);

  Optional<KnowledgeBaseDocumentRecord> findDocumentBySource(
      String tenantId, String sourceType, String sourceId);

  void deleteChunksByDocument(String tenantId, String documentId);

  void createChunk(KnowledgeBaseChunkCreateCommand command);

  List<KnowledgeBaseChunkRecord> listChunksByDocument(String tenantId, String documentId);

  List<KnowledgeBaseChunkRecord> listCandidateChunks(
      String tenantId,
      List<String> sourceTypes,
      List<String> tags,
      int candidateLimit);

  void createSearchLog(KnowledgeBaseSearchLogCreateCommand command);
}
```

---

# 12. jOOQ Repository 实现

## 12.1 `JooqKnowledgeBaseRepository.java`

路径：

```txt id="z55br9"
modules/aiops-execution/src/main/java/io/aegisops/execution/JooqKnowledgeBaseRepository.java
```

```java id="43a1sx"
package io.aegisops.execution;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.KB_CHUNK;
import static io.aegisops.persistence.jooq.Tables.KB_DOCUMENT;
import static io.aegisops.persistence.jooq.Tables.KB_SEARCH_LOG;

import io.aegisops.execution.dto.KnowledgeBaseChunkCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseChunkRecord;
import io.aegisops.execution.dto.KnowledgeBaseDocumentCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseDocumentRecord;
import io.aegisops.execution.dto.KnowledgeBaseSearchLogCreateCommand;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqKnowledgeBaseRepository implements KnowledgeBaseRepository {
  private final DSLContext dsl;

  public JooqKnowledgeBaseRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void upsertDocument(KnowledgeBaseDocumentCreateCommand command) {
    dsl.insertInto(KB_DOCUMENT)
        .set(KB_DOCUMENT.ID, command.id())
        .set(KB_DOCUMENT.TENANT_ID, command.tenantId())
        .set(KB_DOCUMENT.SOURCE_TYPE, command.sourceType())
        .set(KB_DOCUMENT.SOURCE_ID, command.sourceId())
        .set(KB_DOCUMENT.TITLE, command.title())
        .set(KB_DOCUMENT.STATUS, command.status())
        .set(KB_DOCUMENT.METADATA, jsonbValue(command.metadataJson()))
        .set(KB_DOCUMENT.INDEXED_AT, DSL.currentOffsetDateTime())
        .set(KB_DOCUMENT.CREATED_AT, DSL.currentOffsetDateTime())
        .set(KB_DOCUMENT.UPDATED_AT, DSL.currentOffsetDateTime())
        .onConflict(KB_DOCUMENT.TENANT_ID, KB_DOCUMENT.SOURCE_TYPE, KB_DOCUMENT.SOURCE_ID)
        .doUpdate()
        .set(KB_DOCUMENT.TITLE, command.title())
        .set(KB_DOCUMENT.STATUS, command.status())
        .set(KB_DOCUMENT.METADATA, jsonbValue(command.metadataJson()))
        .set(KB_DOCUMENT.INDEXED_AT, DSL.currentOffsetDateTime())
        .set(KB_DOCUMENT.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<KnowledgeBaseDocumentRecord> findDocument(String tenantId, String documentId) {
    return selectDocument()
        .where(KB_DOCUMENT.TENANT_ID.eq(tenantId))
        .and(KB_DOCUMENT.ID.eq(documentId))
        .fetchOptional(this::toDocumentRecord);
  }

  @Override
  public Optional<KnowledgeBaseDocumentRecord> findDocumentBySource(
      String tenantId, String sourceType, String sourceId) {
    return selectDocument()
        .where(KB_DOCUMENT.TENANT_ID.eq(tenantId))
        .and(KB_DOCUMENT.SOURCE_TYPE.eq(sourceType))
        .and(KB_DOCUMENT.SOURCE_ID.eq(sourceId))
        .fetchOptional(this::toDocumentRecord);
  }

  @Override
  public void deleteChunksByDocument(String tenantId, String documentId) {
    dsl.deleteFrom(KB_CHUNK)
        .where(KB_CHUNK.TENANT_ID.eq(tenantId))
        .and(KB_CHUNK.DOCUMENT_ID.eq(documentId))
        .execute();
  }

  @Override
  public void createChunk(KnowledgeBaseChunkCreateCommand command) {
    dsl.insertInto(KB_CHUNK)
        .set(KB_CHUNK.ID, command.id())
        .set(KB_CHUNK.TENANT_ID, command.tenantId())
        .set(KB_CHUNK.DOCUMENT_ID, command.documentId())
        .set(KB_CHUNK.CHUNK_ORDER, command.chunkOrder())
        .set(KB_CHUNK.SOURCE_TYPE, command.sourceType())
        .set(KB_CHUNK.SOURCE_ID, command.sourceId())
        .set(KB_CHUNK.TITLE, command.title())
        .set(KB_CHUNK.CONTENT, command.content())
        .set(KB_CHUNK.CONTENT_HASH, command.contentHash())
        .set(KB_CHUNK.TOKEN_ESTIMATE, command.tokenEstimate())
        .set(KB_CHUNK.EMBEDDING, jsonbValue(command.embeddingJson()))
        .set(KB_CHUNK.METADATA, jsonbValue(command.metadataJson()))
        .set(KB_CHUNK.STATUS, command.status())
        .set(KB_CHUNK.CREATED_AT, DSL.currentOffsetDateTime())
        .set(KB_CHUNK.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public List<KnowledgeBaseChunkRecord> listChunksByDocument(String tenantId, String documentId) {
    return selectChunk()
        .where(KB_CHUNK.TENANT_ID.eq(tenantId))
        .and(KB_CHUNK.DOCUMENT_ID.eq(documentId))
        .orderBy(KB_CHUNK.CHUNK_ORDER.asc())
        .fetch(this::toChunkRecord);
  }

  @Override
  public List<KnowledgeBaseChunkRecord> listCandidateChunks(
      String tenantId, List<String> sourceTypes, List<String> tags, int candidateLimit) {
    Condition condition =
        KB_CHUNK.TENANT_ID.eq(tenantId)
            .and(KB_CHUNK.STATUS.eq("indexed"));

    if (sourceTypes != null && !sourceTypes.isEmpty()) {
      condition = condition.and(KB_CHUNK.SOURCE_TYPE.in(sourceTypes));
    }

    if (tags != null && !tags.isEmpty()) {
      for (String tag : tags) {
        condition =
            condition.and(
                DSL.lower(KB_CHUNK.CONTENT).contains(tag.toLowerCase()));
      }
    }

    return selectChunk()
        .where(condition)
        .orderBy(KB_CHUNK.CREATED_AT.desc())
        .limit(Math.max(1, Math.min(candidateLimit, 500)))
        .fetch(this::toChunkRecord);
  }

  @Override
  public void createSearchLog(KnowledgeBaseSearchLogCreateCommand command) {
    dsl.insertInto(KB_SEARCH_LOG)
        .set(KB_SEARCH_LOG.ID, command.id())
        .set(KB_SEARCH_LOG.TENANT_ID, command.tenantId())
        .set(KB_SEARCH_LOG.QUERY, command.query())
        .set(KB_SEARCH_LOG.SOURCE_TYPES, jsonbValue(command.sourceTypesJson()))
        .set(KB_SEARCH_LOG.TAGS, jsonbValue(command.tagsJson()))
        .set(KB_SEARCH_LOG.TOP_K, command.topK())
        .set(KB_SEARCH_LOG.RESULT_COUNT, command.resultCount())
        .set(KB_SEARCH_LOG.CREATED_BY, command.createdBy())
        .set(KB_SEARCH_LOG.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  private org.jooq.SelectJoinStep<org.jooq.Record> selectDocument() {
    return dsl.select(
            KB_DOCUMENT.ID,
            KB_DOCUMENT.TENANT_ID,
            KB_DOCUMENT.SOURCE_TYPE,
            KB_DOCUMENT.SOURCE_ID,
            KB_DOCUMENT.TITLE,
            KB_DOCUMENT.STATUS,
            KB_DOCUMENT.METADATA.cast(String.class).as("metadata_json"),
            KB_DOCUMENT.INDEXED_AT,
            KB_DOCUMENT.CREATED_AT,
            KB_DOCUMENT.UPDATED_AT)
        .from(KB_DOCUMENT);
  }

  private org.jooq.SelectJoinStep<org.jooq.Record> selectChunk() {
    return dsl.select(
            KB_CHUNK.ID,
            KB_CHUNK.TENANT_ID,
            KB_CHUNK.DOCUMENT_ID,
            KB_CHUNK.CHUNK_ORDER,
            KB_CHUNK.SOURCE_TYPE,
            KB_CHUNK.SOURCE_ID,
            KB_CHUNK.TITLE,
            KB_CHUNK.CONTENT,
            KB_CHUNK.CONTENT_HASH,
            KB_CHUNK.TOKEN_ESTIMATE,
            KB_CHUNK.EMBEDDING.cast(String.class).as("embedding_json"),
            KB_CHUNK.METADATA.cast(String.class).as("metadata_json"),
            KB_CHUNK.STATUS,
            KB_CHUNK.CREATED_AT,
            KB_CHUNK.UPDATED_AT)
        .from(KB_CHUNK);
  }

  private KnowledgeBaseDocumentRecord toDocumentRecord(org.jooq.Record record) {
    return new KnowledgeBaseDocumentRecord(
        record.get(KB_DOCUMENT.ID),
        record.get(KB_DOCUMENT.TENANT_ID),
        record.get(KB_DOCUMENT.SOURCE_TYPE),
        record.get(KB_DOCUMENT.SOURCE_ID),
        record.get(KB_DOCUMENT.TITLE),
        record.get(KB_DOCUMENT.STATUS),
        record.get("metadata_json", String.class),
        record.get(KB_DOCUMENT.INDEXED_AT),
        record.get(KB_DOCUMENT.CREATED_AT),
        record.get(KB_DOCUMENT.UPDATED_AT));
  }

  private KnowledgeBaseChunkRecord toChunkRecord(org.jooq.Record record) {
    return new KnowledgeBaseChunkRecord(
        record.get(KB_CHUNK.ID),
        record.get(KB_CHUNK.TENANT_ID),
        record.get(KB_CHUNK.DOCUMENT_ID),
        value(record.get(KB_CHUNK.CHUNK_ORDER)),
        record.get(KB_CHUNK.SOURCE_TYPE),
        record.get(KB_CHUNK.SOURCE_ID),
        record.get(KB_CHUNK.TITLE),
        record.get(KB_CHUNK.CONTENT),
        record.get(KB_CHUNK.CONTENT_HASH),
        value(record.get(KB_CHUNK.TOKEN_ESTIMATE)),
        record.get("embedding_json", String.class),
        record.get("metadata_json", String.class),
        record.get(KB_CHUNK.STATUS),
        record.get(KB_CHUNK.CREATED_AT),
        record.get(KB_CHUNK.UPDATED_AT));
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }
}
```

---

# 13. Index Service

## 13.1 `KnowledgeBaseIndexService.java`

路径：

```txt id="wydo3u"
modules/aiops-execution/src/main/java/io/aegisops/execution/KnowledgeBaseIndexService.java
```

```java id="j6xph6"
package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.IncidentCaseResponse;
import io.aegisops.execution.dto.KnowledgeBaseChunkCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseDocumentCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseIndexResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeBaseIndexService {
  private final IncidentCaseService incidentCaseService;
  private final IncidentCaseChunkBuilder chunkBuilder;
  private final KnowledgeBaseRepository repository;
  private final EmbeddingProvider embeddingProvider;
  private final KnowledgeBaseJson json;

  public KnowledgeBaseIndexService(
      IncidentCaseService incidentCaseService,
      IncidentCaseChunkBuilder chunkBuilder,
      KnowledgeBaseRepository repository,
      EmbeddingProvider embeddingProvider,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    this.incidentCaseService = incidentCaseService;
    this.chunkBuilder = chunkBuilder;
    this.repository = repository;
    this.embeddingProvider = embeddingProvider;
    this.json = new KnowledgeBaseJson(objectMapper);
  }

  @Transactional
  public KnowledgeBaseIndexResponse indexIncidentCase(String tenantId, String caseId) {
    IncidentCaseResponse incidentCase = incidentCaseService.get(tenantId, caseId);

    if (!"published".equals(incidentCase.status())) {
      throw new AppException(
          "KB_INDEX_CASE_STATUS_INVALID",
          "Only published incident case can be indexed");
    }

    String documentId =
        repository
            .findDocumentBySource(tenantId, "incident_case", caseId)
            .map(item -> item.id())
            .orElseGet(() -> newId("kbd"));

    repository.upsertDocument(
        new KnowledgeBaseDocumentCreateCommand(
            documentId,
            tenantId,
            "incident_case",
            caseId,
            incidentCase.title(),
            "indexed",
            json.write(
                Map.of(
                    "incidentId", incidentCase.incidentId(),
                    "severity", value(incidentCase.severity()),
                    "qualityScore", incidentCase.qualityScore(),
                    "tags", incidentCase.tags()))));

    repository.deleteChunksByDocument(tenantId, documentId);

    var chunks = chunkBuilder.build(incidentCase);
    for (var chunk : chunks) {
      String embeddingJson = json.write(embeddingProvider.embed(chunk.content()));

      repository.createChunk(
          new KnowledgeBaseChunkCreateCommand(
              newId("kbc"),
              tenantId,
              documentId,
              chunk.chunkOrder(),
              "incident_case",
              caseId,
              chunk.title(),
              chunk.content(),
              sha256(chunk.content()),
              estimateTokens(chunk.content()),
              embeddingJson,
              chunk.metadataJson(),
              "indexed"));
    }

    return new KnowledgeBaseIndexResponse(
        documentId,
        "incident_case",
        caseId,
        chunks.size(),
        "indexed");
  }

  public KnowledgeBaseIndexResponse getDocumentBySource(
      String tenantId, String sourceType, String sourceId) {
    var document =
        repository
            .findDocumentBySource(tenantId, sourceType, sourceId)
            .orElseThrow(
                () -> new AppException("KB_DOCUMENT_NOT_FOUND", "Knowledge base document not found"));

    int chunkCount = repository.listChunksByDocument(tenantId, document.id()).size();

    return new KnowledgeBaseIndexResponse(
        document.id(),
        document.sourceType(),
        document.sourceId(),
        chunkCount,
        document.status());
  }

  public KnowledgeBaseIndexResponse getDocument(String tenantId, String documentId) {
    var document =
        repository
            .findDocument(tenantId, documentId)
            .orElseThrow(
                () -> new AppException("KB_DOCUMENT_NOT_FOUND", "Knowledge base document not found"));

    int chunkCount = repository.listChunksByDocument(tenantId, document.id()).size();

    return new KnowledgeBaseIndexResponse(
        document.id(),
        document.sourceType(),
        document.sourceId(),
        chunkCount,
        document.status());
  }

  private int estimateTokens(String content) {
    if (content == null || content.isBlank()) {
      return 0;
    }
    return Math.max(1, content.length() / 4);
  }

  private String sha256(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new AppException("KB_CONTENT_HASH_FAILED", "Failed to hash content");
    }
  }

  private String value(String value) {
    return value == null ? "" : value;
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
```

---

# 14. Search Service

## 14.1 `KnowledgeBaseSearchService.java`

路径：

```txt id="zezcjs"
modules/aiops-execution/src/main/java/io/aegisops/execution/KnowledgeBaseSearchService.java
```

```java id="pggzts"
package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.KnowledgeBaseSearchLogCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseSearchRequest;
import io.aegisops.execution.dto.KnowledgeBaseSearchResponse;
import io.aegisops.execution.dto.KnowledgeBaseSearchResult;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseSearchService {
  private final KnowledgeBaseRepository repository;
  private final EmbeddingProvider embeddingProvider;
  private final KnowledgeBaseScorer scorer;
  private final KnowledgeBaseJson json;

  public KnowledgeBaseSearchService(
      KnowledgeBaseRepository repository,
      EmbeddingProvider embeddingProvider,
      KnowledgeBaseScorer scorer,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    this.repository = repository;
    this.embeddingProvider = embeddingProvider;
    this.scorer = scorer;
    this.json = new KnowledgeBaseJson(objectMapper);
  }

  public KnowledgeBaseSearchResponse search(String tenantId, KnowledgeBaseSearchRequest request) {
    validateRequest(request);

    int topK = normalizeTopK(request.topK());
    List<Double> queryEmbedding = embeddingProvider.embed(request.query());

    List<KnowledgeBaseSearchResult> results =
        repository
            .listCandidateChunks(
                tenantId,
                normalizeSourceTypes(request.sourceTypes()),
                normalizeTags(request.tags()),
                Math.max(topK * 20, 100))
            .stream()
            .map(
                chunk -> {
                  double vectorScore =
                      scorer.cosine(queryEmbedding, json.readDoubleList(chunk.embeddingJson()));
                  double keywordScore = scorer.keywordScore(request.query(), chunk.content());
                  double score = scorer.hybridScore(vectorScore, keywordScore);

                  return new KnowledgeBaseSearchResult(
                      chunk.documentId(),
                      chunk.id(),
                      chunk.sourceType(),
                      chunk.sourceId(),
                      chunk.title(),
                      chunk.content(),
                      round(score),
                      round(vectorScore),
                      round(keywordScore),
                      chunk.metadataJson());
                })
            .filter(result -> result.score() > 0.0d)
            .sorted(Comparator.comparingDouble(KnowledgeBaseSearchResult::score).reversed())
            .limit(topK)
            .toList();

    repository.createSearchLog(
        new KnowledgeBaseSearchLogCreateCommand(
            newId("kbs"),
            tenantId,
            request.query(),
            json.write(normalizeSourceTypes(request.sourceTypes())),
            json.write(normalizeTags(request.tags())),
            topK,
            results.size(),
            blankToDefault(request.createdBy(), "system")));

    return new KnowledgeBaseSearchResponse(request.query(), topK, results);
  }

  private void validateRequest(KnowledgeBaseSearchRequest request) {
    if (request == null || request.query() == null || request.query().isBlank()) {
      throw new AppException("KB_SEARCH_QUERY_REQUIRED", "Knowledge base search query is required");
    }
  }

  private int normalizeTopK(Integer value) {
    if (value == null) {
      return 5;
    }
    return Math.max(1, Math.min(value, 50));
  }

  private List<String> normalizeSourceTypes(List<String> sourceTypes) {
    if (sourceTypes == null || sourceTypes.isEmpty()) {
      return List.of("incident_case");
    }

    return sourceTypes.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(value -> value.trim().toLowerCase())
        .filter(value -> List.of("incident_case", "postmortem", "manual").contains(value))
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

  private double round(double value) {
    return Math.round(value * 10000.0d) / 10000.0d;
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

# 15. Controller

## 15.1 `KnowledgeBaseController.java`

路径：

```txt id="tctqi4"
modules/aiops-execution/src/main/java/io/aegisops/execution/KnowledgeBaseController.java
```

```java id="p89z2j"
package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.KnowledgeBaseIndexResponse;
import io.aegisops.execution.dto.KnowledgeBaseSearchRequest;
import io.aegisops.execution.dto.KnowledgeBaseSearchResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KnowledgeBaseController {
  private final KnowledgeBaseIndexService indexService;
  private final KnowledgeBaseSearchService searchService;

  public KnowledgeBaseController(
      KnowledgeBaseIndexService indexService,
      KnowledgeBaseSearchService searchService) {
    this.indexService = indexService;
    this.searchService = searchService;
  }

  @PostMapping("/api/incident-cases/{caseId}/kb/index")
  public ApiResponse<KnowledgeBaseIndexResponse> indexIncidentCase(@PathVariable String caseId) {
    return ApiResponse.ok(indexService.indexIncidentCase(TenantContext.requireTenantId(), caseId));
  }

  @GetMapping("/api/knowledge-base/documents/{documentId}")
  public ApiResponse<KnowledgeBaseIndexResponse> getDocument(@PathVariable String documentId) {
    return ApiResponse.ok(indexService.getDocument(TenantContext.requireTenantId(), documentId));
  }

  @GetMapping("/api/knowledge-base/documents/source/{sourceType}/{sourceId}")
  public ApiResponse<KnowledgeBaseIndexResponse> getDocumentBySource(
      @PathVariable String sourceType,
      @PathVariable String sourceId) {
    return ApiResponse.ok(
        indexService.getDocumentBySource(TenantContext.requireTenantId(), sourceType, sourceId));
  }

  @PostMapping("/api/knowledge-base/search")
  public ApiResponse<KnowledgeBaseSearchResponse> search(
      @RequestBody KnowledgeBaseSearchRequest request) {
    return ApiResponse.ok(searchService.search(TenantContext.requireTenantId(), request));
  }
}
```

---

## 15.2 `AgentKnowledgeToolController.java`

路径：

```txt id="0ib4d2"
modules/aiops-execution/src/main/java/io/aegisops/execution/AgentKnowledgeToolController.java
```

```java id="icl117"
package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.execution.dto.AgentSearchCasesRequest;
import io.aegisops.execution.dto.KnowledgeBaseSearchRequest;
import io.aegisops.execution.dto.KnowledgeBaseSearchResponse;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal read-only tool endpoint for Python Agent.
 *
 * This endpoint does not trigger execution, rollback, runbook, or approval.
 */
@RestController
public class AgentKnowledgeToolController {
  private final KnowledgeBaseSearchService searchService;

  public AgentKnowledgeToolController(KnowledgeBaseSearchService searchService) {
    this.searchService = searchService;
  }

  @PostMapping("/internal/agent/tools/search-cases")
  public ApiResponse<KnowledgeBaseSearchResponse> searchCases(
      @RequestBody AgentSearchCasesRequest request) {
    return ApiResponse.ok(
        searchService.search(
            request.tenantId(),
            new KnowledgeBaseSearchRequest(
                request.query(),
                List.of("incident_case"),
                request.tags(),
                request.topK(),
                "agent")));
  }
}
```

---

# 16. 单元测试

## 16.1 `HashingEmbeddingProviderTest.java`

路径：

```txt id="968vhv"
modules/aiops-execution/src/test/java/io/aegisops/execution/HashingEmbeddingProviderTest.java
```

```java id="v0ta6m"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HashingEmbeddingProviderTest {
  private final HashingEmbeddingProvider provider = new HashingEmbeddingProvider();

  @Test
  void embedReturnsFixedDimension() {
    var embedding = provider.embed("redis timeout order service");

    assertEquals(provider.dimension(), embedding.size());
  }

  @Test
  void embedNormalizesVector() {
    var embedding = provider.embed("redis timeout order service");

    double norm =
        Math.sqrt(embedding.stream().mapToDouble(value -> value * value).sum());

    assertTrue(norm > 0.99d && norm < 1.01d);
  }

  @Test
  void sameTextProducesSameEmbedding() {
    assertEquals(provider.embed("same text"), provider.embed("same text"));
  }
}
```

---

## 16.2 `KnowledgeBaseScorerTest.java`

路径：

```txt id="dv2yaw"
modules/aiops-execution/src/test/java/io/aegisops/execution/KnowledgeBaseScorerTest.java
```

```java id="4b94w0"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeBaseScorerTest {
  private final KnowledgeBaseScorer scorer = new KnowledgeBaseScorer();

  @Test
  void cosineIdenticalVectorIsOne() {
    double score = scorer.cosine(List.of(1.0, 0.0, 0.0), List.of(1.0, 0.0, 0.0));

    assertEquals(1.0d, score, 0.0001d);
  }

  @Test
  void cosineOrthogonalVectorIsZero() {
    double score = scorer.cosine(List.of(1.0, 0.0), List.of(0.0, 1.0));

    assertEquals(0.0d, score, 0.0001d);
  }

  @Test
  void keywordScoreFindsOverlap() {
    double score =
        scorer.keywordScore(
            "redis timeout",
            "order service redis connection timeout happened");

    assertTrue(score > 0.9d);
  }

  @Test
  void hybridScoreUsesBothSignals() {
    double score = scorer.hybridScore(0.8d, 0.5d);

    assertEquals(0.74d, score, 0.0001d);
  }
}
```

---

## 16.3 `IncidentCaseChunkBuilderTest.java`

路径：

```txt id="kw2keg"
modules/aiops-execution/src/test/java/io/aegisops/execution/IncidentCaseChunkBuilderTest.java
```

```java id="x65op9"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.dto.IncidentCaseResolutionStepResponse;
import io.aegisops.execution.dto.IncidentCaseResponse;
import io.aegisops.execution.dto.IncidentCaseSymptomResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncidentCaseChunkBuilderTest {
  @Test
  void buildChunksFromIncidentCase() {
    IncidentCaseChunkBuilder builder = new IncidentCaseChunkBuilder(new ObjectMapper());

    var chunks = builder.build(caseResponse());

    assertTrue(chunks.size() >= 3);
    assertTrue(chunks.get(0).content().contains("Root Cause"));
    assertTrue(chunks.stream().anyMatch(chunk -> chunk.content().contains("High error rate")));
    assertTrue(chunks.stream().anyMatch(chunk -> chunk.content().contains("Restart service")));
  }

  private IncidentCaseResponse caseResponse() {
    return new IncidentCaseResponse(
        "icase_1",
        "tenant_1",
        "pmr_1",
        "inc_1",
        "published",
        "high",
        "Order service db timeout",
        "Order service error rate increased",
        "db timeout",
        "Restart service",
        "Add timeout alert",
        80,
        "alice",
        "reviewer",
        OffsetDateTime.now(),
        null,
        List.of(
            new IncidentCaseSymptomResponse(
                "sym_1",
                "impact",
                "High error rate",
                "5xx increased",
                OffsetDateTime.now())),
        List.of(
            new IncidentCaseResolutionStepResponse(
                "step_1",
                1,
                "Restart service",
                "Restart order service",
                "manual",
                "pms_1",
                OffsetDateTime.now())),
        List.of("order-service", "db-timeout"),
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}
```

---

## 16.4 `KnowledgeBaseIndexServiceTest.java`

路径：

```txt id="qjo012"
modules/aiops-execution/src/test/java/io/aegisops/execution/KnowledgeBaseIndexServiceTest.java
```

```java id="lu1f5q"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.IncidentCaseResponse;
import io.aegisops.execution.dto.KnowledgeBaseChunkCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseChunkRecord;
import io.aegisops.execution.dto.KnowledgeBaseDocumentCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseDocumentRecord;
import io.aegisops.execution.dto.KnowledgeBaseSearchLogCreateCommand;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KnowledgeBaseIndexServiceTest {
  @Test
  void indexPublishedIncidentCase() {
    FakeIncidentCaseService caseService = new FakeIncidentCaseService("published");
    FakeKnowledgeBaseRepository repository = new FakeKnowledgeBaseRepository();

    KnowledgeBaseIndexService service =
        new KnowledgeBaseIndexService(
            caseService,
            new IncidentCaseChunkBuilder(new ObjectMapper()),
            repository,
            new HashingEmbeddingProvider(),
            new ObjectMapper());

    var response = service.indexIncidentCase("tenant_1", "icase_1");

    assertEquals("incident_case", response.sourceType());
    assertEquals("icase_1", response.sourceId());
    assertTrue(response.chunkCount() > 0);
    assertTrue(repository.chunks.size() > 0);
  }

  @Test
  void rejectNonPublishedIncidentCase() {
    FakeIncidentCaseService caseService = new FakeIncidentCaseService("draft");

    KnowledgeBaseIndexService service =
        new KnowledgeBaseIndexService(
            caseService,
            new IncidentCaseChunkBuilder(new ObjectMapper()),
            new FakeKnowledgeBaseRepository(),
            new HashingEmbeddingProvider(),
            new ObjectMapper());

    assertThrows(
        AppException.class,
        () -> service.indexIncidentCase("tenant_1", "icase_1"));
  }

  private static class FakeIncidentCaseService extends IncidentCaseService {
    private final String status;

    FakeIncidentCaseService(String status) {
      super(null, null, null);
      this.status = status;
    }

    @Override
    public IncidentCaseResponse get(String tenantId, String caseId) {
      return KnowledgeBaseTestFixtures.caseResponse(status);
    }
  }

  private static class FakeKnowledgeBaseRepository implements KnowledgeBaseRepository {
    KnowledgeBaseDocumentRecord document;
    final List<KnowledgeBaseChunkRecord> chunks = new ArrayList<>();

    @Override
    public void upsertDocument(KnowledgeBaseDocumentCreateCommand command) {
      document =
          new KnowledgeBaseDocumentRecord(
              command.id(),
              command.tenantId(),
              command.sourceType(),
              command.sourceId(),
              command.title(),
              command.status(),
              command.metadataJson(),
              OffsetDateTime.now(),
              OffsetDateTime.now(),
              OffsetDateTime.now());
    }

    @Override
    public Optional<KnowledgeBaseDocumentRecord> findDocument(String tenantId, String documentId) {
      return Optional.ofNullable(document);
    }

    @Override
    public Optional<KnowledgeBaseDocumentRecord> findDocumentBySource(
        String tenantId, String sourceType, String sourceId) {
      return Optional.ofNullable(document)
          .filter(
              item ->
                  item.sourceType().equals(sourceType)
                      && item.sourceId().equals(sourceId));
    }

    @Override
    public void deleteChunksByDocument(String tenantId, String documentId) {
      chunks.clear();
    }

    @Override
    public void createChunk(KnowledgeBaseChunkCreateCommand command) {
      chunks.add(
          new KnowledgeBaseChunkRecord(
              command.id(),
              command.tenantId(),
              command.documentId(),
              command.chunkOrder(),
              command.sourceType(),
              command.sourceId(),
              command.title(),
              command.content(),
              command.contentHash(),
              command.tokenEstimate(),
              command.embeddingJson(),
              command.metadataJson(),
              command.status(),
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public List<KnowledgeBaseChunkRecord> listChunksByDocument(String tenantId, String documentId) {
      return chunks;
    }

    @Override
    public List<KnowledgeBaseChunkRecord> listCandidateChunks(
        String tenantId, List<String> sourceTypes, List<String> tags, int candidateLimit) {
      return chunks;
    }

    @Override
    public void createSearchLog(KnowledgeBaseSearchLogCreateCommand command) {}
  }
}
```

---

## 16.5 `KnowledgeBaseSearchServiceTest.java`

路径：

```txt id="0vo642"
modules/aiops-execution/src/test/java/io/aegisops/execution/KnowledgeBaseSearchServiceTest.java
```

```java id="1kfquv"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.KnowledgeBaseChunkCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseChunkRecord;
import io.aegisops.execution.dto.KnowledgeBaseDocumentCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseDocumentRecord;
import io.aegisops.execution.dto.KnowledgeBaseSearchLogCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseSearchRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KnowledgeBaseSearchServiceTest {
  @Test
  void searchReturnsRelevantChunks() {
    ObjectMapper objectMapper = new ObjectMapper();
    HashingEmbeddingProvider embeddingProvider = new HashingEmbeddingProvider();
    KnowledgeBaseJson json = new KnowledgeBaseJson(objectMapper);

    FakeKnowledgeBaseRepository repository =
        new FakeKnowledgeBaseRepository(
            List.of(
                chunk(
                    "kbc_1",
                    "order service redis timeout",
                    json.write(embeddingProvider.embed("order service redis timeout"))),
                chunk(
                    "kbc_2",
                    "frontend css error",
                    json.write(embeddingProvider.embed("frontend css error")))));

    KnowledgeBaseSearchService service =
        new KnowledgeBaseSearchService(
            repository,
            embeddingProvider,
            new KnowledgeBaseScorer(),
            objectMapper);

    var response =
        service.search(
            "tenant_1",
            new KnowledgeBaseSearchRequest(
                "redis timeout",
                List.of("incident_case"),
                List.of(),
                3,
                "alice"));

    assertEquals(1, response.results().size());
    assertEquals("kbc_1", response.results().get(0).chunkId());
    assertTrue(repository.logged);
  }

  @Test
  void rejectBlankQuery() {
    KnowledgeBaseSearchService service =
        new KnowledgeBaseSearchService(
            new FakeKnowledgeBaseRepository(List.of()),
            new HashingEmbeddingProvider(),
            new KnowledgeBaseScorer(),
            new ObjectMapper());

    assertThrows(
        AppException.class,
        () ->
            service.search(
                "tenant_1",
                new KnowledgeBaseSearchRequest(
                    " ",
                    List.of("incident_case"),
                    List.of(),
                    5,
                    "alice")));
  }

  private static KnowledgeBaseChunkRecord chunk(String id, String content, String embeddingJson) {
    return new KnowledgeBaseChunkRecord(
        id,
        "tenant_1",
        "kbd_1",
        1,
        "incident_case",
        "icase_1",
        "title",
        content,
        "hash",
        10,
        embeddingJson,
        "{}",
        "indexed",
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private static class FakeKnowledgeBaseRepository implements KnowledgeBaseRepository {
    private final List<KnowledgeBaseChunkRecord> chunks;
    boolean logged;

    FakeKnowledgeBaseRepository(List<KnowledgeBaseChunkRecord> chunks) {
      this.chunks = chunks;
    }

    @Override
    public List<KnowledgeBaseChunkRecord> listCandidateChunks(
        String tenantId, List<String> sourceTypes, List<String> tags, int candidateLimit) {
      return chunks;
    }

    @Override
    public void createSearchLog(KnowledgeBaseSearchLogCreateCommand command) {
      logged = true;
    }

    @Override public void upsertDocument(KnowledgeBaseDocumentCreateCommand command) {}
    @Override public Optional<KnowledgeBaseDocumentRecord> findDocument(String tenantId, String documentId) { return Optional.empty(); }
    @Override public Optional<KnowledgeBaseDocumentRecord> findDocumentBySource(String tenantId, String sourceType, String sourceId) { return Optional.empty(); }
    @Override public void deleteChunksByDocument(String tenantId, String documentId) {}
    @Override public void createChunk(KnowledgeBaseChunkCreateCommand command) {}
    @Override public List<KnowledgeBaseChunkRecord> listChunksByDocument(String tenantId, String documentId) { return List.of(); }
  }
}
```

---

## 16.6 `KnowledgeBaseTestFixtures.java`

路径：

```txt id="2va42g"
modules/aiops-execution/src/test/java/io/aegisops/execution/KnowledgeBaseTestFixtures.java
```

```java id="zkyepc"
package io.aegisops.execution;

import io.aegisops.execution.dto.IncidentCaseResolutionStepResponse;
import io.aegisops.execution.dto.IncidentCaseResponse;
import io.aegisops.execution.dto.IncidentCaseSymptomResponse;
import java.time.OffsetDateTime;
import java.util.List;

final class KnowledgeBaseTestFixtures {
  private KnowledgeBaseTestFixtures() {}

  static IncidentCaseResponse caseResponse(String status) {
    return new IncidentCaseResponse(
        "icase_1",
        "tenant_1",
        "pmr_1",
        "inc_1",
        status,
        "high",
        "Order service redis timeout",
        "Order service failed due to redis timeout",
        "redis timeout",
        "restart order service and increase redis timeout",
        "add redis timeout alert",
        80,
        "alice",
        "reviewer",
        OffsetDateTime.now(),
        null,
        List.of(
            new IncidentCaseSymptomResponse(
                "sym_1",
                "impact",
                "High error rate",
                "order service 5xx increased",
                OffsetDateTime.now())),
        List.of(
            new IncidentCaseResolutionStepResponse(
                "step_1",
                1,
                "Restart service",
                "restart order service",
                "manual",
                "pms_1",
                OffsetDateTime.now())),
        List.of("order-service", "redis-timeout"),
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}
```

---

## 16.7 `JooqKnowledgeBaseRepositoryGeneratedSqlTest.java`

路径：

```txt id="12z7iq"
modules/aiops-execution/src/test/java/io/aegisops/execution/JooqKnowledgeBaseRepositoryGeneratedSqlTest.java
```

```java id="1zq1bc"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.KnowledgeBaseChunkCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseDocumentCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseSearchLogCreateCommand;
import io.aegisops.persistence.JooqTestSupport;
import java.util.List;
import org.jooq.Query;
import org.junit.jupiter.api.Test;

class JooqKnowledgeBaseRepositoryGeneratedSqlTest {
  @Test
  void upsertDocumentUsesKbDocumentTable() {
    var dsl = JooqTestSupport.dsl();
    var repository = new JooqKnowledgeBaseRepository(dsl);

    repository.upsertDocument(
        new KnowledgeBaseDocumentCreateCommand(
            "kbd_1",
            "tenant_1",
            "incident_case",
            "icase_1",
            "title",
            "indexed",
            "{}"));

    String sql = renderedSql(dsl.queries());

    assertTrue(sql.contains("kb_document"));
    assertTrue(sql.contains("source_type"));
  }

  @Test
  void createChunkUsesKbChunkTable() {
    var dsl = JooqTestSupport.dsl();
    var repository = new JooqKnowledgeBaseRepository(dsl);

    repository.createChunk(
        new KnowledgeBaseChunkCreateCommand(
            "kbc_1",
            "tenant_1",
            "kbd_1",
            1,
            "incident_case",
            "icase_1",
            "title",
            "content",
            "hash",
            10,
            "[]",
            "{}",
            "indexed"));

    String sql = renderedSql(dsl.queries());

    assertTrue(sql.contains("kb_chunk"));
    assertTrue(sql.contains("embedding"));
  }

  @Test
  void createSearchLogUsesKbSearchLogTable() {
    var dsl = JooqTestSupport.dsl();
    var repository = new JooqKnowledgeBaseRepository(dsl);

    repository.createSearchLog(
        new KnowledgeBaseSearchLogCreateCommand(
            "kbs_1",
            "tenant_1",
            "redis timeout",
            "[\"incident_case\"]",
            "[]",
            5,
            1,
            "alice"));

    String sql = renderedSql(dsl.queries());

    assertTrue(sql.contains("kb_search_log"));
    assertTrue(sql.contains("result_count"));
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

# 17. 文档

路径：

```txt id="l07gyc"
docs/mvp/design/phase6.2-knowledge-base-vector-retrieval.md
```

```md id="jnz7w0"
# Phase6.2 Knowledge Base & Vector Retrieval

## 目标

Phase6.2 将 published incident case 建索引，形成可检索知识库。

## 不做

- 不做 Agent Memory
- 不做 Prompt Eval
- 不做多 Agent
- 不新增执行能力
- 不接 Runner
- 不触发 Runbook

## 存储策略

MVP 使用 PostgreSQL jsonb 存储 embedding，Java 内存计算 cosine similarity。

后续可替换为 pgvector 或 Milvus。

## 新增表

- kb_document
- kb_chunk
- kb_search_log

## API

- POST /api/incident-cases/{caseId}/kb/index
- GET /api/knowledge-base/documents/{documentId}
- GET /api/knowledge-base/documents/source/{sourceType}/{sourceId}
- POST /api/knowledge-base/search
- POST /internal/agent/tools/search-cases

## 检索策略

Hybrid Search:

- vectorScore: cosine(query_embedding, chunk_embedding)
- keywordScore: query token overlap
- score = vectorScore _ 0.8 + keywordScore _ 0.2

## 验收标准

1. published case 可以 index。
2. draft case 不能 index。
3. index 会生成 kb_document。
4. index 会生成 kb_chunk。
5. 重新 index 会删除旧 chunk 后重建。
6. search 可以返回相关 chunk。
7. search 写 kb_search_log。
8. Agent tool endpoint 只读。
9. 不引入执行能力。
```

---

# 18. 验证命令

```powershell id="7ts5q8"
mvn -pl modules/aiops-persistence -am generate-sources
mvn -pl modules/aiops-execution -am test
mvn -pl apps/aiops-server -am test
```

全量：

```powershell id="z8fhw1"
mvn test
```

---

# 19. 验收标准

```txt id="ia9x3d"
1. kb_document 表存在。
2. kb_chunk 表存在。
3. kb_search_log 表存在。
4. published incident_case 可以 index。
5. draft / archived incident_case 不能 index。
6. index 会创建或更新 kb_document。
7. reindex 会删除旧 kb_chunk 并重建。
8. chunk 包含 summary / symptom / resolution step。
9. chunk embedding 写入 jsonb。
10. search 可以返回相关 chunk。
11. search 结果包含 vectorScore / keywordScore / score。
12. search 会写 kb_search_log。
13. Agent search-cases 接口只读。
14. 不引入 pgvector 依赖。
15. 不引入 Milvus。
16. 不新增执行能力。
17. 不修改 runner。
```

---

# 20. 建议提交信息

```txt id="27ateo"
feat(kb): add incident case vector retrieval
```

---

# 21. 下一步 Phase6.3

Phase6.2 完成后，进入：

```txt id="0b00fb"
Phase6.3 Agent Eval & Prompt Regression
```

Phase6.3 建议做：

```txt id="kpja4j"
agent_eval_case
agent_eval_dataset
agent_eval_run
case-to-eval conversion
diagnosis expected output
prompt regression
CI optional eval smoke test
```

不要把 Agent Eval 提前塞进 Phase6.2。
