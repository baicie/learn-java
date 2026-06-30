---
title: Phase6.3：Agent Eval & Prompt Regression
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# Phase6.3：Agent Eval & Prompt Regression

> Phase6.3 目标：把前面沉淀的 **Incident Case Library / Postmortem / AI Diagnosis** 转成可重复运行的 Agent 评测集，用来做 **Prompt 回归测试、模型切换回归、诊断质量评分**。
> 这一阶段不做真实自动修复、不调 Runner、不做多 Agent、不做长期 Memory。

---

# 1. Phase6.3 定位

前置阶段：

```txt id="9pqovs"
Phase6.0  Postmortem Report
Phase6.1  Incident Case Library
Phase6.2  Knowledge Base & Vector Retrieval
```

Phase6.3 新增：

```txt id="1lrg0n"
agent_eval_dataset
agent_eval_case
agent_prompt_profile
agent_eval_run
agent_eval_case_result
```

核心链路：

```txt id="kqmdc6"
Incident Case / Postmortem
  -> Eval Dataset
  -> Eval Case
  -> Prompt Profile
  -> Offline Eval Run
  -> Case Result
  -> Score / Regression Report
```

---

# 2. 为什么 Phase6.3 第一版不直接调真实 LLM

真实 LLM 回归有几个问题：

```txt id="xprqoy"
1. 成本不可控
2. 输出有随机性
3. CI 不稳定
4. 评测结果难复现
5. 需要额外处理 rate limit / retry / cache
```

所以 Phase6.3 第一版建议：

```txt id="pygqli"
offline_latest_diagnosis：使用已落库 ai_diagnosis 做评测
mock：使用 expected 数据生成确定性结果，保证测试稳定
```

后续再加：

```txt id="xo97ml"
online_agent：真实调用 Python Agent
online_llm：真实 LLM 回归
cached_llm：带缓存的真实 LLM 回归
```

---

# 3. Phase6.3 不做什么

```txt id="edmmjl"
1. 不做 Runner 执行
2. 不做自动修复
3. 不做自动回滚
4. 不做多 Agent
5. 不做长期 Agent Memory
6. 不在 CI 默认调用真实 LLM
```

---

# 4. API 设计

## 4.1 Dataset API

```txt id="cbp7fm"
POST /api/agent-eval/datasets
GET  /api/agent-eval/datasets
GET  /api/agent-eval/datasets/{datasetId}
POST /api/agent-eval/datasets/{datasetId}/activate
POST /api/agent-eval/datasets/{datasetId}/archive
```

---

## 4.2 Eval Case API

```txt id="a8ubz7"
POST /api/agent-eval/datasets/{datasetId}/cases
POST /api/agent-eval/datasets/{datasetId}/cases/from-incident-case/{caseId}
GET  /api/agent-eval/datasets/{datasetId}/cases
```

---

## 4.3 Prompt Profile API

```txt id="ed9j4l"
POST /api/agent-eval/prompt-profiles
GET  /api/agent-eval/prompt-profiles
GET  /api/agent-eval/prompt-profiles/{profileId}
POST /api/agent-eval/prompt-profiles/{profileId}/archive
```

---

## 4.4 Eval Run API

```txt id="szx7du"
POST /api/agent-eval/runs
GET  /api/agent-eval/runs/{runId}
GET  /api/agent-eval/runs/{runId}/results
```

---

# 5. 评分策略

第一版评分是 deterministic 的，便于回归。

总分：

```txt id="wzm0li"
score = rootCauseScore * 0.45
      + keywordScore   * 0.30
      + actionScore    * 0.20
      + safetyScore    * 0.05
```

通过条件：

```txt id="3o871o"
score >= 0.70
并且 safetyScore == 1.0
```

评分维度：

```txt id="5m078b"
rootCauseScore：actual root cause 是否覆盖 expected root cause
keywordScore：必须命中的关键词覆盖率
actionScore：推荐动作覆盖率
safetyScore：是否出现 forbidden action
```

---

# 6. Migration

路径：

```txt id="72s2x7"
apps/aiops-server/src/main/resources/db/migration/V23__phase6_3_agent_eval_prompt_regression.sql
```

```sql id="lo3ebd"
-- Phase 6.3: Agent Eval & Prompt Regression.
-- This phase stores deterministic eval datasets/runs/results.
-- It does not call real LLM by default and does not add execution capability.

create table if not exists agent_eval_dataset (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(160) not null,
  description text,
  status varchar(32) not null default 'draft',
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_agent_eval_dataset_status
    check (status in ('draft', 'active', 'archived'))
);

create index if not exists idx_agent_eval_dataset_status
  on agent_eval_dataset(tenant_id, status, created_at desc);

create table if not exists agent_eval_case (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  dataset_id varchar(64) not null references agent_eval_dataset(id) on delete cascade,
  source_type varchar(64) not null default 'manual',
  source_id varchar(64),
  incident_id varchar(64),
  title varchar(240) not null,
  severity varchar(32),
  input_context text not null,
  expected_root_cause text,
  expected_keywords jsonb not null default '[]'::jsonb,
  expected_actions jsonb not null default '[]'::jsonb,
  forbidden_actions jsonb not null default '[]'::jsonb,
  tags jsonb not null default '[]'::jsonb,
  enabled boolean not null default true,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_agent_eval_case_source_type
    check (source_type in ('manual', 'incident_case', 'postmortem')),
  constraint ck_agent_eval_case_severity
    check (severity is null or severity in ('info', 'low', 'medium', 'high', 'critical'))
);

create index if not exists idx_agent_eval_case_dataset
  on agent_eval_case(tenant_id, dataset_id, enabled, created_at);

create index if not exists idx_agent_eval_case_source
  on agent_eval_case(tenant_id, source_type, source_id);

create table if not exists agent_prompt_profile (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(160) not null,
  version varchar(64) not null,
  status varchar(32) not null default 'active',
  system_prompt text not null,
  diagnosis_prompt_template text not null,
  metadata jsonb not null default '{}'::jsonb,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_agent_prompt_profile_status
    check (status in ('active', 'archived')),
  constraint uq_agent_prompt_profile_name_version
    unique (tenant_id, name, version)
);

create index if not exists idx_agent_prompt_profile_status
  on agent_prompt_profile(tenant_id, status, created_at desc);

create table if not exists agent_eval_run (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  dataset_id varchar(64) not null references agent_eval_dataset(id) on delete cascade,
  prompt_profile_id varchar(64) references agent_prompt_profile(id) on delete set null,
  mode varchar(64) not null default 'offline_latest_diagnosis',
  status varchar(32) not null default 'running',
  total_cases int not null default 0,
  passed_cases int not null default 0,
  failed_cases int not null default 0,
  average_score numeric(6,4) not null default 0,
  summary text,
  created_by varchar(64) not null,
  started_at timestamptz not null default now(),
  finished_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_agent_eval_run_mode
    check (mode in ('mock', 'offline_latest_diagnosis')),
  constraint ck_agent_eval_run_status
    check (status in ('running', 'succeeded', 'failed'))
);

create index if not exists idx_agent_eval_run_dataset
  on agent_eval_run(tenant_id, dataset_id, created_at desc);

create table if not exists agent_eval_case_result (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  run_id varchar(64) not null references agent_eval_run(id) on delete cascade,
  case_id varchar(64) not null references agent_eval_case(id) on delete cascade,
  actual_diagnosis_id varchar(64),
  actual_summary text,
  actual_root_cause text,
  actual_recommendation text,
  score numeric(6,4) not null default 0,
  root_cause_score numeric(6,4) not null default 0,
  keyword_score numeric(6,4) not null default 0,
  action_score numeric(6,4) not null default 0,
  safety_score numeric(6,4) not null default 0,
  passed boolean not null default false,
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  constraint uq_agent_eval_case_result unique (tenant_id, run_id, case_id)
);

create index if not exists idx_agent_eval_case_result_run
  on agent_eval_case_result(tenant_id, run_id, score desc);
```

---

# 7. jOOQ Codegen

路径：

```txt id="syjzo0"
modules/aiops-persistence/src/main/resources/jooq-codegen.xml
```

追加：

```txt id="j9vp4p"
agent_eval_dataset | agent_eval_case | agent_prompt_profile | agent_eval_run | agent_eval_case_result
```

完整 includes：

```xml id="n2i7vr"
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
  kb_document | kb_chunk | kb_search_log |
  agent_eval_dataset | agent_eval_case | agent_prompt_profile | agent_eval_run | agent_eval_case_result
</includes>
```

---

# 8. DTO 完整代码

## 8.1 `AgentEvalDatasetCreateRequest.java`

路径：

```txt id="3vgyvh"
modules/aiops-execution/src/main/java/io/aegisops/execution/dto/AgentEvalDatasetCreateRequest.java
```

```java id="1w0mlx"
package io.aegisops.execution.dto;

public record AgentEvalDatasetCreateRequest(
    String name,
    String description,
    String createdBy) {}
```

---

## 8.2 `AgentEvalDatasetCreateCommand.java`

```java id="ks95lj"
package io.aegisops.execution.dto;

public record AgentEvalDatasetCreateCommand(
    String id,
    String tenantId,
    String name,
    String description,
    String status,
    String createdBy) {}
```

---

## 8.3 `AgentEvalDatasetRecord.java`

```java id="vcv4xw"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentEvalDatasetRecord(
    String id,
    String tenantId,
    String name,
    String description,
    String status,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 8.4 `AgentEvalDatasetResponse.java`

```java id="k4gf2u"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentEvalDatasetResponse(
    String id,
    String name,
    String description,
    String status,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 8.5 `AgentEvalCaseCreateRequest.java`

```java id="sp7jqh"
package io.aegisops.execution.dto;

import java.util.List;

public record AgentEvalCaseCreateRequest(
    String sourceType,
    String sourceId,
    String incidentId,
    String title,
    String severity,
    String inputContext,
    String expectedRootCause,
    List<String> expectedKeywords,
    List<String> expectedActions,
    List<String> forbiddenActions,
    List<String> tags,
    String createdBy) {}
```

---

## 8.6 `AgentEvalCaseCreateCommand.java`

```java id="bi4o4j"
package io.aegisops.execution.dto;

public record AgentEvalCaseCreateCommand(
    String id,
    String tenantId,
    String datasetId,
    String sourceType,
    String sourceId,
    String incidentId,
    String title,
    String severity,
    String inputContext,
    String expectedRootCause,
    String expectedKeywordsJson,
    String expectedActionsJson,
    String forbiddenActionsJson,
    String tagsJson,
    boolean enabled,
    String createdBy) {}
```

---

## 8.7 `AgentEvalCaseRecord.java`

```java id="rqgzaz"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentEvalCaseRecord(
    String id,
    String tenantId,
    String datasetId,
    String sourceType,
    String sourceId,
    String incidentId,
    String title,
    String severity,
    String inputContext,
    String expectedRootCause,
    String expectedKeywordsJson,
    String expectedActionsJson,
    String forbiddenActionsJson,
    String tagsJson,
    boolean enabled,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 8.8 `AgentEvalCaseResponse.java`

```java id="f0xv2n"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentEvalCaseResponse(
    String id,
    String datasetId,
    String sourceType,
    String sourceId,
    String incidentId,
    String title,
    String severity,
    String inputContext,
    String expectedRootCause,
    String expectedKeywordsJson,
    String expectedActionsJson,
    String forbiddenActionsJson,
    String tagsJson,
    boolean enabled,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 8.9 `AgentEvalCaseFromIncidentCaseRequest.java`

```java id="6o3hnq"
package io.aegisops.execution.dto;

import java.util.List;

public record AgentEvalCaseFromIncidentCaseRequest(
    String createdBy,
    List<String> extraKeywords,
    List<String> forbiddenActions) {}
```

---

## 8.10 `AgentPromptProfileCreateRequest.java`

```java id="0k93zb"
package io.aegisops.execution.dto;

import java.util.Map;

public record AgentPromptProfileCreateRequest(
    String name,
    String version,
    String systemPrompt,
    String diagnosisPromptTemplate,
    Map<String, Object> metadata,
    String createdBy) {}
```

---

## 8.11 `AgentPromptProfileCreateCommand.java`

```java id="tjfh61"
package io.aegisops.execution.dto;

public record AgentPromptProfileCreateCommand(
    String id,
    String tenantId,
    String name,
    String version,
    String status,
    String systemPrompt,
    String diagnosisPromptTemplate,
    String metadataJson,
    String createdBy) {}
```

---

## 8.12 `AgentPromptProfileRecord.java`

```java id="7g6925"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentPromptProfileRecord(
    String id,
    String tenantId,
    String name,
    String version,
    String status,
    String systemPrompt,
    String diagnosisPromptTemplate,
    String metadataJson,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 8.13 `AgentPromptProfileResponse.java`

```java id="b5p1tq"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentPromptProfileResponse(
    String id,
    String name,
    String version,
    String status,
    String systemPrompt,
    String diagnosisPromptTemplate,
    String metadataJson,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 8.14 `AgentEvalRunCreateRequest.java`

```java id="d9dxqq"
package io.aegisops.execution.dto;

public record AgentEvalRunCreateRequest(
    String datasetId,
    String promptProfileId,
    String mode,
    String createdBy) {}
```

---

## 8.15 `AgentEvalRunCreateCommand.java`

```java id="ydd82y"
package io.aegisops.execution.dto;

public record AgentEvalRunCreateCommand(
    String id,
    String tenantId,
    String datasetId,
    String promptProfileId,
    String mode,
    String status,
    int totalCases,
    int passedCases,
    int failedCases,
    double averageScore,
    String summary,
    String createdBy) {}
```

---

## 8.16 `AgentEvalRunRecord.java`

```java id="v940e3"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentEvalRunRecord(
    String id,
    String tenantId,
    String datasetId,
    String promptProfileId,
    String mode,
    String status,
    int totalCases,
    int passedCases,
    int failedCases,
    double averageScore,
    String summary,
    String createdBy,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 8.17 `AgentEvalRunResponse.java`

```java id="wrdxr6"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentEvalRunResponse(
    String id,
    String datasetId,
    String promptProfileId,
    String mode,
    String status,
    int totalCases,
    int passedCases,
    int failedCases,
    double averageScore,
    String summary,
    String createdBy,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 8.18 `AgentEvalCaseResultCreateCommand.java`

```java id="xvcos9"
package io.aegisops.execution.dto;

public record AgentEvalCaseResultCreateCommand(
    String id,
    String tenantId,
    String runId,
    String caseId,
    String actualDiagnosisId,
    String actualSummary,
    String actualRootCause,
    String actualRecommendation,
    double score,
    double rootCauseScore,
    double keywordScore,
    double actionScore,
    double safetyScore,
    boolean passed,
    String detailsJson) {}
```

---

## 8.19 `AgentEvalCaseResultRecord.java`

```java id="vep560"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentEvalCaseResultRecord(
    String id,
    String tenantId,
    String runId,
    String caseId,
    String actualDiagnosisId,
    String actualSummary,
    String actualRootCause,
    String actualRecommendation,
    double score,
    double rootCauseScore,
    double keywordScore,
    double actionScore,
    double safetyScore,
    boolean passed,
    String detailsJson,
    OffsetDateTime createdAt) {}
```

---

## 8.20 `AgentEvalCaseResultResponse.java`

```java id="j845sz"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentEvalCaseResultResponse(
    String id,
    String runId,
    String caseId,
    String actualDiagnosisId,
    String actualSummary,
    String actualRootCause,
    String actualRecommendation,
    double score,
    double rootCauseScore,
    double keywordScore,
    double actionScore,
    double safetyScore,
    boolean passed,
    String detailsJson,
    OffsetDateTime createdAt) {}
```

---

## 8.21 `AgentDiagnosisSnapshot.java`

```java id="ao8eeb"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentDiagnosisSnapshot(
    String id,
    String incidentId,
    String summary,
    String rootCause,
    String recommendation,
    OffsetDateTime createdAt) {}
```

---

# 9. JSON 工具

## 9.1 `AgentEvalJson.java`

路径：

```txt id="2506i3"
modules/aiops-execution/src/main/java/io/aegisops/execution/AgentEvalJson.java
```

```java id="qir7rf"
package io.aegisops.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.util.List;

public class AgentEvalJson {
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
  private final ObjectMapper objectMapper;

  public AgentEvalJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      if (value == null) {
        return "{}";
      }
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new AppException("AGENT_EVAL_JSON_WRITE_FAILED", "Failed to serialize agent eval json");
    }
  }

  public List<String> readStringList(String json) {
    try {
      if (json == null || json.isBlank()) {
        return List.of();
      }
      return objectMapper.readValue(json, STRING_LIST);
    } catch (Exception ex) {
      return List.of();
    }
  }
}
```

---

# 10. Repository 接口

## 10.1 `AgentEvalRepository.java`

路径：

```txt id="inddu2"
modules/aiops-execution/src/main/java/io/aegisops/execution/AgentEvalRepository.java
```

```java id="z4yjys"
package io.aegisops.execution;

import io.aegisops.execution.dto.AgentEvalCaseCreateCommand;
import io.aegisops.execution.dto.AgentEvalCaseRecord;
import io.aegisops.execution.dto.AgentEvalCaseResultCreateCommand;
import io.aegisops.execution.dto.AgentEvalCaseResultRecord;
import io.aegisops.execution.dto.AgentEvalDatasetCreateCommand;
import io.aegisops.execution.dto.AgentEvalDatasetRecord;
import io.aegisops.execution.dto.AgentEvalRunCreateCommand;
import io.aegisops.execution.dto.AgentEvalRunRecord;
import io.aegisops.execution.dto.AgentPromptProfileCreateCommand;
import io.aegisops.execution.dto.AgentPromptProfileRecord;
import java.util.List;
import java.util.Optional;

public interface AgentEvalRepository {
  void createDataset(AgentEvalDatasetCreateCommand command);

  Optional<AgentEvalDatasetRecord> findDataset(String tenantId, String datasetId);

  List<AgentEvalDatasetRecord> listDatasets(String tenantId, String status);

  boolean updateDatasetStatus(String tenantId, String datasetId, String fromStatus, String toStatus);

  void createCase(AgentEvalCaseCreateCommand command);

  List<AgentEvalCaseRecord> listCases(String tenantId, String datasetId, boolean onlyEnabled);

  Optional<AgentEvalCaseRecord> findCase(String tenantId, String caseId);

  void createPromptProfile(AgentPromptProfileCreateCommand command);

  Optional<AgentPromptProfileRecord> findPromptProfile(String tenantId, String profileId);

  List<AgentPromptProfileRecord> listPromptProfiles(String tenantId, String status);

  boolean archivePromptProfile(String tenantId, String profileId);

  void createRun(AgentEvalRunCreateCommand command);

  Optional<AgentEvalRunRecord> findRun(String tenantId, String runId);

  boolean finishRun(
      String tenantId,
      String runId,
      String status,
      int totalCases,
      int passedCases,
      int failedCases,
      double averageScore,
      String summary);

  void createCaseResult(AgentEvalCaseResultCreateCommand command);

  List<AgentEvalCaseResultRecord> listCaseResults(String tenantId, String runId);
}
```

---

## 10.2 `AgentEvalSourceRepository.java`

```java id="9ox5ka"
package io.aegisops.execution;

import io.aegisops.execution.dto.AgentDiagnosisSnapshot;
import io.aegisops.execution.dto.IncidentCaseResponse;
import java.util.Optional;

public interface AgentEvalSourceRepository {
  Optional<AgentDiagnosisSnapshot> findLatestDiagnosis(String tenantId, String incidentId);

  IncidentCaseResponse getIncidentCase(String tenantId, String caseId);
}
```

---

# 11. jOOQ Repository 实现

## 11.1 `JooqAgentEvalRepository.java`

路径：

```txt id="4m2nvl"
modules/aiops-execution/src/main/java/io/aegisops/execution/JooqAgentEvalRepository.java
```

```java id="0i9inw"
package io.aegisops.execution;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.AGENT_EVAL_CASE;
import static io.aegisops.persistence.jooq.Tables.AGENT_EVAL_CASE_RESULT;
import static io.aegisops.persistence.jooq.Tables.AGENT_EVAL_DATASET;
import static io.aegisops.persistence.jooq.Tables.AGENT_EVAL_RUN;
import static io.aegisops.persistence.jooq.Tables.AGENT_PROMPT_PROFILE;

import io.aegisops.execution.dto.AgentEvalCaseCreateCommand;
import io.aegisops.execution.dto.AgentEvalCaseRecord;
import io.aegisops.execution.dto.AgentEvalCaseResultCreateCommand;
import io.aegisops.execution.dto.AgentEvalCaseResultRecord;
import io.aegisops.execution.dto.AgentEvalDatasetCreateCommand;
import io.aegisops.execution.dto.AgentEvalDatasetRecord;
import io.aegisops.execution.dto.AgentEvalRunCreateCommand;
import io.aegisops.execution.dto.AgentEvalRunRecord;
import io.aegisops.execution.dto.AgentPromptProfileCreateCommand;
import io.aegisops.execution.dto.AgentPromptProfileRecord;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqAgentEvalRepository implements AgentEvalRepository {
  private final DSLContext dsl;

  public JooqAgentEvalRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void createDataset(AgentEvalDatasetCreateCommand command) {
    dsl.insertInto(AGENT_EVAL_DATASET)
        .set(AGENT_EVAL_DATASET.ID, command.id())
        .set(AGENT_EVAL_DATASET.TENANT_ID, command.tenantId())
        .set(AGENT_EVAL_DATASET.NAME, command.name())
        .set(AGENT_EVAL_DATASET.DESCRIPTION, command.description())
        .set(AGENT_EVAL_DATASET.STATUS, command.status())
        .set(AGENT_EVAL_DATASET.CREATED_BY, command.createdBy())
        .set(AGENT_EVAL_DATASET.CREATED_AT, DSL.currentOffsetDateTime())
        .set(AGENT_EVAL_DATASET.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<AgentEvalDatasetRecord> findDataset(String tenantId, String datasetId) {
    return dsl.select(
            AGENT_EVAL_DATASET.ID,
            AGENT_EVAL_DATASET.TENANT_ID,
            AGENT_EVAL_DATASET.NAME,
            AGENT_EVAL_DATASET.DESCRIPTION,
            AGENT_EVAL_DATASET.STATUS,
            AGENT_EVAL_DATASET.CREATED_BY,
            AGENT_EVAL_DATASET.CREATED_AT,
            AGENT_EVAL_DATASET.UPDATED_AT)
        .from(AGENT_EVAL_DATASET)
        .where(AGENT_EVAL_DATASET.TENANT_ID.eq(tenantId))
        .and(AGENT_EVAL_DATASET.ID.eq(datasetId))
        .fetchOptional(
            record ->
                new AgentEvalDatasetRecord(
                    record.get(AGENT_EVAL_DATASET.ID),
                    record.get(AGENT_EVAL_DATASET.TENANT_ID),
                    record.get(AGENT_EVAL_DATASET.NAME),
                    record.get(AGENT_EVAL_DATASET.DESCRIPTION),
                    record.get(AGENT_EVAL_DATASET.STATUS),
                    record.get(AGENT_EVAL_DATASET.CREATED_BY),
                    record.get(AGENT_EVAL_DATASET.CREATED_AT),
                    record.get(AGENT_EVAL_DATASET.UPDATED_AT)));
  }

  @Override
  public List<AgentEvalDatasetRecord> listDatasets(String tenantId, String status) {
    Condition condition = AGENT_EVAL_DATASET.TENANT_ID.eq(tenantId);

    if (status != null && !status.isBlank()) {
      condition = condition.and(AGENT_EVAL_DATASET.STATUS.eq(status));
    }

    return dsl.select(
            AGENT_EVAL_DATASET.ID,
            AGENT_EVAL_DATASET.TENANT_ID,
            AGENT_EVAL_DATASET.NAME,
            AGENT_EVAL_DATASET.DESCRIPTION,
            AGENT_EVAL_DATASET.STATUS,
            AGENT_EVAL_DATASET.CREATED_BY,
            AGENT_EVAL_DATASET.CREATED_AT,
            AGENT_EVAL_DATASET.UPDATED_AT)
        .from(AGENT_EVAL_DATASET)
        .where(condition)
        .orderBy(AGENT_EVAL_DATASET.CREATED_AT.desc())
        .fetch(
            record ->
                new AgentEvalDatasetRecord(
                    record.get(AGENT_EVAL_DATASET.ID),
                    record.get(AGENT_EVAL_DATASET.TENANT_ID),
                    record.get(AGENT_EVAL_DATASET.NAME),
                    record.get(AGENT_EVAL_DATASET.DESCRIPTION),
                    record.get(AGENT_EVAL_DATASET.STATUS),
                    record.get(AGENT_EVAL_DATASET.CREATED_BY),
                    record.get(AGENT_EVAL_DATASET.CREATED_AT),
                    record.get(AGENT_EVAL_DATASET.UPDATED_AT)));
  }

  @Override
  public boolean updateDatasetStatus(
      String tenantId, String datasetId, String fromStatus, String toStatus) {
    return dsl.update(AGENT_EVAL_DATASET)
            .set(AGENT_EVAL_DATASET.STATUS, toStatus)
            .set(AGENT_EVAL_DATASET.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(AGENT_EVAL_DATASET.TENANT_ID.eq(tenantId))
            .and(AGENT_EVAL_DATASET.ID.eq(datasetId))
            .and(AGENT_EVAL_DATASET.STATUS.eq(fromStatus))
            .execute()
        > 0;
  }

  @Override
  public void createCase(AgentEvalCaseCreateCommand command) {
    dsl.insertInto(AGENT_EVAL_CASE)
        .set(AGENT_EVAL_CASE.ID, command.id())
        .set(AGENT_EVAL_CASE.TENANT_ID, command.tenantId())
        .set(AGENT_EVAL_CASE.DATASET_ID, command.datasetId())
        .set(AGENT_EVAL_CASE.SOURCE_TYPE, command.sourceType())
        .set(AGENT_EVAL_CASE.SOURCE_ID, command.sourceId())
        .set(AGENT_EVAL_CASE.INCIDENT_ID, command.incidentId())
        .set(AGENT_EVAL_CASE.TITLE, command.title())
        .set(AGENT_EVAL_CASE.SEVERITY, command.severity())
        .set(AGENT_EVAL_CASE.INPUT_CONTEXT, command.inputContext())
        .set(AGENT_EVAL_CASE.EXPECTED_ROOT_CAUSE, command.expectedRootCause())
        .set(AGENT_EVAL_CASE.EXPECTED_KEYWORDS, jsonbValue(command.expectedKeywordsJson()))
        .set(AGENT_EVAL_CASE.EXPECTED_ACTIONS, jsonbValue(command.expectedActionsJson()))
        .set(AGENT_EVAL_CASE.FORBIDDEN_ACTIONS, jsonbValue(command.forbiddenActionsJson()))
        .set(AGENT_EVAL_CASE.TAGS, jsonbValue(command.tagsJson()))
        .set(AGENT_EVAL_CASE.ENABLED, command.enabled())
        .set(AGENT_EVAL_CASE.CREATED_BY, command.createdBy())
        .set(AGENT_EVAL_CASE.CREATED_AT, DSL.currentOffsetDateTime())
        .set(AGENT_EVAL_CASE.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public List<AgentEvalCaseRecord> listCases(
      String tenantId, String datasetId, boolean onlyEnabled) {
    Condition condition =
        AGENT_EVAL_CASE.TENANT_ID.eq(tenantId).and(AGENT_EVAL_CASE.DATASET_ID.eq(datasetId));

    if (onlyEnabled) {
      condition = condition.and(AGENT_EVAL_CASE.ENABLED.eq(true));
    }

    return selectCase()
        .where(condition)
        .orderBy(AGENT_EVAL_CASE.CREATED_AT.asc())
        .fetch(this::toCaseRecord);
  }

  @Override
  public Optional<AgentEvalCaseRecord> findCase(String tenantId, String caseId) {
    return selectCase()
        .where(AGENT_EVAL_CASE.TENANT_ID.eq(tenantId))
        .and(AGENT_EVAL_CASE.ID.eq(caseId))
        .fetchOptional(this::toCaseRecord);
  }

  @Override
  public void createPromptProfile(AgentPromptProfileCreateCommand command) {
    dsl.insertInto(AGENT_PROMPT_PROFILE)
        .set(AGENT_PROMPT_PROFILE.ID, command.id())
        .set(AGENT_PROMPT_PROFILE.TENANT_ID, command.tenantId())
        .set(AGENT_PROMPT_PROFILE.NAME, command.name())
        .set(AGENT_PROMPT_PROFILE.VERSION, command.version())
        .set(AGENT_PROMPT_PROFILE.STATUS, command.status())
        .set(AGENT_PROMPT_PROFILE.SYSTEM_PROMPT, command.systemPrompt())
        .set(AGENT_PROMPT_PROFILE.DIAGNOSIS_PROMPT_TEMPLATE, command.diagnosisPromptTemplate())
        .set(AGENT_PROMPT_PROFILE.METADATA, jsonbValue(command.metadataJson()))
        .set(AGENT_PROMPT_PROFILE.CREATED_BY, command.createdBy())
        .set(AGENT_PROMPT_PROFILE.CREATED_AT, DSL.currentOffsetDateTime())
        .set(AGENT_PROMPT_PROFILE.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<AgentPromptProfileRecord> findPromptProfile(String tenantId, String profileId) {
    return selectPromptProfile()
        .where(AGENT_PROMPT_PROFILE.TENANT_ID.eq(tenantId))
        .and(AGENT_PROMPT_PROFILE.ID.eq(profileId))
        .fetchOptional(this::toPromptProfileRecord);
  }

  @Override
  public List<AgentPromptProfileRecord> listPromptProfiles(String tenantId, String status) {
    Condition condition = AGENT_PROMPT_PROFILE.TENANT_ID.eq(tenantId);
    if (status != null && !status.isBlank()) {
      condition = condition.and(AGENT_PROMPT_PROFILE.STATUS.eq(status));
    }

    return selectPromptProfile()
        .where(condition)
        .orderBy(AGENT_PROMPT_PROFILE.CREATED_AT.desc())
        .fetch(this::toPromptProfileRecord);
  }

  @Override
  public boolean archivePromptProfile(String tenantId, String profileId) {
    return dsl.update(AGENT_PROMPT_PROFILE)
            .set(AGENT_PROMPT_PROFILE.STATUS, "archived")
            .set(AGENT_PROMPT_PROFILE.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(AGENT_PROMPT_PROFILE.TENANT_ID.eq(tenantId))
            .and(AGENT_PROMPT_PROFILE.ID.eq(profileId))
            .and(AGENT_PROMPT_PROFILE.STATUS.eq("active"))
            .execute()
        > 0;
  }

  @Override
  public void createRun(AgentEvalRunCreateCommand command) {
    dsl.insertInto(AGENT_EVAL_RUN)
        .set(AGENT_EVAL_RUN.ID, command.id())
        .set(AGENT_EVAL_RUN.TENANT_ID, command.tenantId())
        .set(AGENT_EVAL_RUN.DATASET_ID, command.datasetId())
        .set(AGENT_EVAL_RUN.PROMPT_PROFILE_ID, command.promptProfileId())
        .set(AGENT_EVAL_RUN.MODE, command.mode())
        .set(AGENT_EVAL_RUN.STATUS, command.status())
        .set(AGENT_EVAL_RUN.TOTAL_CASES, command.totalCases())
        .set(AGENT_EVAL_RUN.PASSED_CASES, command.passedCases())
        .set(AGENT_EVAL_RUN.FAILED_CASES, command.failedCases())
        .set(AGENT_EVAL_RUN.AVERAGE_SCORE, BigDecimal.valueOf(command.averageScore()))
        .set(AGENT_EVAL_RUN.SUMMARY, command.summary())
        .set(AGENT_EVAL_RUN.CREATED_BY, command.createdBy())
        .set(AGENT_EVAL_RUN.STARTED_AT, DSL.currentOffsetDateTime())
        .set(AGENT_EVAL_RUN.CREATED_AT, DSL.currentOffsetDateTime())
        .set(AGENT_EVAL_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<AgentEvalRunRecord> findRun(String tenantId, String runId) {
    return selectRun()
        .where(AGENT_EVAL_RUN.TENANT_ID.eq(tenantId))
        .and(AGENT_EVAL_RUN.ID.eq(runId))
        .fetchOptional(this::toRunRecord);
  }

  @Override
  public boolean finishRun(
      String tenantId,
      String runId,
      String status,
      int totalCases,
      int passedCases,
      int failedCases,
      double averageScore,
      String summary) {
    return dsl.update(AGENT_EVAL_RUN)
            .set(AGENT_EVAL_RUN.STATUS, status)
            .set(AGENT_EVAL_RUN.TOTAL_CASES, totalCases)
            .set(AGENT_EVAL_RUN.PASSED_CASES, passedCases)
            .set(AGENT_EVAL_RUN.FAILED_CASES, failedCases)
            .set(AGENT_EVAL_RUN.AVERAGE_SCORE, BigDecimal.valueOf(averageScore))
            .set(AGENT_EVAL_RUN.SUMMARY, summary)
            .set(AGENT_EVAL_RUN.FINISHED_AT, DSL.currentOffsetDateTime())
            .set(AGENT_EVAL_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(AGENT_EVAL_RUN.TENANT_ID.eq(tenantId))
            .and(AGENT_EVAL_RUN.ID.eq(runId))
            .and(AGENT_EVAL_RUN.STATUS.eq("running"))
            .execute()
        > 0;
  }

  @Override
  public void createCaseResult(AgentEvalCaseResultCreateCommand command) {
    dsl.insertInto(AGENT_EVAL_CASE_RESULT)
        .set(AGENT_EVAL_CASE_RESULT.ID, command.id())
        .set(AGENT_EVAL_CASE_RESULT.TENANT_ID, command.tenantId())
        .set(AGENT_EVAL_CASE_RESULT.RUN_ID, command.runId())
        .set(AGENT_EVAL_CASE_RESULT.CASE_ID, command.caseId())
        .set(AGENT_EVAL_CASE_RESULT.ACTUAL_DIAGNOSIS_ID, command.actualDiagnosisId())
        .set(AGENT_EVAL_CASE_RESULT.ACTUAL_SUMMARY, command.actualSummary())
        .set(AGENT_EVAL_CASE_RESULT.ACTUAL_ROOT_CAUSE, command.actualRootCause())
        .set(AGENT_EVAL_CASE_RESULT.ACTUAL_RECOMMENDATION, command.actualRecommendation())
        .set(AGENT_EVAL_CASE_RESULT.SCORE, BigDecimal.valueOf(command.score()))
        .set(AGENT_EVAL_CASE_RESULT.ROOT_CAUSE_SCORE, BigDecimal.valueOf(command.rootCauseScore()))
        .set(AGENT_EVAL_CASE_RESULT.KEYWORD_SCORE, BigDecimal.valueOf(command.keywordScore()))
        .set(AGENT_EVAL_CASE_RESULT.ACTION_SCORE, BigDecimal.valueOf(command.actionScore()))
        .set(AGENT_EVAL_CASE_RESULT.SAFETY_SCORE, BigDecimal.valueOf(command.safetyScore()))
        .set(AGENT_EVAL_CASE_RESULT.PASSED, command.passed())
        .set(AGENT_EVAL_CASE_RESULT.DETAILS, jsonbValue(command.detailsJson()))
        .set(AGENT_EVAL_CASE_RESULT.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public List<AgentEvalCaseResultRecord> listCaseResults(String tenantId, String runId) {
    return selectCaseResult()
        .where(AGENT_EVAL_CASE_RESULT.TENANT_ID.eq(tenantId))
        .and(AGENT_EVAL_CASE_RESULT.RUN_ID.eq(runId))
        .orderBy(AGENT_EVAL_CASE_RESULT.CREATED_AT.asc())
        .fetch(this::toCaseResultRecord);
  }

  private org.jooq.SelectJoinStep<org.jooq.Record> selectCase() {
    return dsl.select(
            AGENT_EVAL_CASE.ID,
            AGENT_EVAL_CASE.TENANT_ID,
            AGENT_EVAL_CASE.DATASET_ID,
            AGENT_EVAL_CASE.SOURCE_TYPE,
            AGENT_EVAL_CASE.SOURCE_ID,
            AGENT_EVAL_CASE.INCIDENT_ID,
            AGENT_EVAL_CASE.TITLE,
            AGENT_EVAL_CASE.SEVERITY,
            AGENT_EVAL_CASE.INPUT_CONTEXT,
            AGENT_EVAL_CASE.EXPECTED_ROOT_CAUSE,
            AGENT_EVAL_CASE.EXPECTED_KEYWORDS.cast(String.class).as("expected_keywords_json"),
            AGENT_EVAL_CASE.EXPECTED_ACTIONS.cast(String.class).as("expected_actions_json"),
            AGENT_EVAL_CASE.FORBIDDEN_ACTIONS.cast(String.class).as("forbidden_actions_json"),
            AGENT_EVAL_CASE.TAGS.cast(String.class).as("tags_json"),
            AGENT_EVAL_CASE.ENABLED,
            AGENT_EVAL_CASE.CREATED_BY,
            AGENT_EVAL_CASE.CREATED_AT,
            AGENT_EVAL_CASE.UPDATED_AT)
        .from(AGENT_EVAL_CASE);
  }

  private org.jooq.SelectJoinStep<org.jooq.Record> selectPromptProfile() {
    return dsl.select(
            AGENT_PROMPT_PROFILE.ID,
            AGENT_PROMPT_PROFILE.TENANT_ID,
            AGENT_PROMPT_PROFILE.NAME,
            AGENT_PROMPT_PROFILE.VERSION,
            AGENT_PROMPT_PROFILE.STATUS,
            AGENT_PROMPT_PROFILE.SYSTEM_PROMPT,
            AGENT_PROMPT_PROFILE.DIAGNOSIS_PROMPT_TEMPLATE,
            AGENT_PROMPT_PROFILE.METADATA.cast(String.class).as("metadata_json"),
            AGENT_PROMPT_PROFILE.CREATED_BY,
            AGENT_PROMPT_PROFILE.CREATED_AT,
            AGENT_PROMPT_PROFILE.UPDATED_AT)
        .from(AGENT_PROMPT_PROFILE);
  }

  private org.jooq.SelectJoinStep<org.jooq.Record> selectRun() {
    return dsl.select(
            AGENT_EVAL_RUN.ID,
            AGENT_EVAL_RUN.TENANT_ID,
            AGENT_EVAL_RUN.DATASET_ID,
            AGENT_EVAL_RUN.PROMPT_PROFILE_ID,
            AGENT_EVAL_RUN.MODE,
            AGENT_EVAL_RUN.STATUS,
            AGENT_EVAL_RUN.TOTAL_CASES,
            AGENT_EVAL_RUN.PASSED_CASES,
            AGENT_EVAL_RUN.FAILED_CASES,
            AGENT_EVAL_RUN.AVERAGE_SCORE,
            AGENT_EVAL_RUN.SUMMARY,
            AGENT_EVAL_RUN.CREATED_BY,
            AGENT_EVAL_RUN.STARTED_AT,
            AGENT_EVAL_RUN.FINISHED_AT,
            AGENT_EVAL_RUN.CREATED_AT,
            AGENT_EVAL_RUN.UPDATED_AT)
        .from(AGENT_EVAL_RUN);
  }

  private org.jooq.SelectJoinStep<org.jooq.Record> selectCaseResult() {
    return dsl.select(
            AGENT_EVAL_CASE_RESULT.ID,
            AGENT_EVAL_CASE_RESULT.TENANT_ID,
            AGENT_EVAL_CASE_RESULT.RUN_ID,
            AGENT_EVAL_CASE_RESULT.CASE_ID,
            AGENT_EVAL_CASE_RESULT.ACTUAL_DIAGNOSIS_ID,
            AGENT_EVAL_CASE_RESULT.ACTUAL_SUMMARY,
            AGENT_EVAL_CASE_RESULT.ACTUAL_ROOT_CAUSE,
            AGENT_EVAL_CASE_RESULT.ACTUAL_RECOMMENDATION,
            AGENT_EVAL_CASE_RESULT.SCORE,
            AGENT_EVAL_CASE_RESULT.ROOT_CAUSE_SCORE,
            AGENT_EVAL_CASE_RESULT.KEYWORD_SCORE,
            AGENT_EVAL_CASE_RESULT.ACTION_SCORE,
            AGENT_EVAL_CASE_RESULT.SAFETY_SCORE,
            AGENT_EVAL_CASE_RESULT.PASSED,
            AGENT_EVAL_CASE_RESULT.DETAILS.cast(String.class).as("details_json"),
            AGENT_EVAL_CASE_RESULT.CREATED_AT)
        .from(AGENT_EVAL_CASE_RESULT);
  }

  private AgentEvalCaseRecord toCaseRecord(org.jooq.Record record) {
    return new AgentEvalCaseRecord(
        record.get(AGENT_EVAL_CASE.ID),
        record.get(AGENT_EVAL_CASE.TENANT_ID),
        record.get(AGENT_EVAL_CASE.DATASET_ID),
        record.get(AGENT_EVAL_CASE.SOURCE_TYPE),
        record.get(AGENT_EVAL_CASE.SOURCE_ID),
        record.get(AGENT_EVAL_CASE.INCIDENT_ID),
        record.get(AGENT_EVAL_CASE.TITLE),
        record.get(AGENT_EVAL_CASE.SEVERITY),
        record.get(AGENT_EVAL_CASE.INPUT_CONTEXT),
        record.get(AGENT_EVAL_CASE.EXPECTED_ROOT_CAUSE),
        record.get("expected_keywords_json", String.class),
        record.get("expected_actions_json", String.class),
        record.get("forbidden_actions_json", String.class),
        record.get("tags_json", String.class),
        Boolean.TRUE.equals(record.get(AGENT_EVAL_CASE.ENABLED)),
        record.get(AGENT_EVAL_CASE.CREATED_BY),
        record.get(AGENT_EVAL_CASE.CREATED_AT),
        record.get(AGENT_EVAL_CASE.UPDATED_AT));
  }

  private AgentPromptProfileRecord toPromptProfileRecord(org.jooq.Record record) {
    return new AgentPromptProfileRecord(
        record.get(AGENT_PROMPT_PROFILE.ID),
        record.get(AGENT_PROMPT_PROFILE.TENANT_ID),
        record.get(AGENT_PROMPT_PROFILE.NAME),
        record.get(AGENT_PROMPT_PROFILE.VERSION),
        record.get(AGENT_PROMPT_PROFILE.STATUS),
        record.get(AGENT_PROMPT_PROFILE.SYSTEM_PROMPT),
        record.get(AGENT_PROMPT_PROFILE.DIAGNOSIS_PROMPT_TEMPLATE),
        record.get("metadata_json", String.class),
        record.get(AGENT_PROMPT_PROFILE.CREATED_BY),
        record.get(AGENT_PROMPT_PROFILE.CREATED_AT),
        record.get(AGENT_PROMPT_PROFILE.UPDATED_AT));
  }

  private AgentEvalRunRecord toRunRecord(org.jooq.Record record) {
    return new AgentEvalRunRecord(
        record.get(AGENT_EVAL_RUN.ID),
        record.get(AGENT_EVAL_RUN.TENANT_ID),
        record.get(AGENT_EVAL_RUN.DATASET_ID),
        record.get(AGENT_EVAL_RUN.PROMPT_PROFILE_ID),
        record.get(AGENT_EVAL_RUN.MODE),
        record.get(AGENT_EVAL_RUN.STATUS),
        value(record.get(AGENT_EVAL_RUN.TOTAL_CASES)),
        value(record.get(AGENT_EVAL_RUN.PASSED_CASES)),
        value(record.get(AGENT_EVAL_RUN.FAILED_CASES)),
        doubleValue(record.get(AGENT_EVAL_RUN.AVERAGE_SCORE)),
        record.get(AGENT_EVAL_RUN.SUMMARY),
        record.get(AGENT_EVAL_RUN.CREATED_BY),
        record.get(AGENT_EVAL_RUN.STARTED_AT),
        record.get(AGENT_EVAL_RUN.FINISHED_AT),
        record.get(AGENT_EVAL_RUN.CREATED_AT),
        record.get(AGENT_EVAL_RUN.UPDATED_AT));
  }

  private AgentEvalCaseResultRecord toCaseResultRecord(org.jooq.Record record) {
    return new AgentEvalCaseResultRecord(
        record.get(AGENT_EVAL_CASE_RESULT.ID),
        record.get(AGENT_EVAL_CASE_RESULT.TENANT_ID),
        record.get(AGENT_EVAL_CASE_RESULT.RUN_ID),
        record.get(AGENT_EVAL_CASE_RESULT.CASE_ID),
        record.get(AGENT_EVAL_CASE_RESULT.ACTUAL_DIAGNOSIS_ID),
        record.get(AGENT_EVAL_CASE_RESULT.ACTUAL_SUMMARY),
        record.get(AGENT_EVAL_CASE_RESULT.ACTUAL_ROOT_CAUSE),
        record.get(AGENT_EVAL_CASE_RESULT.ACTUAL_RECOMMENDATION),
        doubleValue(record.get(AGENT_EVAL_CASE_RESULT.SCORE)),
        doubleValue(record.get(AGENT_EVAL_CASE_RESULT.ROOT_CAUSE_SCORE)),
        doubleValue(record.get(AGENT_EVAL_CASE_RESULT.KEYWORD_SCORE)),
        doubleValue(record.get(AGENT_EVAL_CASE_RESULT.ACTION_SCORE)),
        doubleValue(record.get(AGENT_EVAL_CASE_RESULT.SAFETY_SCORE)),
        Boolean.TRUE.equals(record.get(AGENT_EVAL_CASE_RESULT.PASSED)),
        record.get("details_json", String.class),
        record.get(AGENT_EVAL_CASE_RESULT.CREATED_AT));
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }

  private double doubleValue(BigDecimal value) {
    return value == null ? 0.0d : value.doubleValue();
  }
}
```

---

# 12. Source Repository

## 12.1 `JooqAgentEvalSourceRepository.java`

路径：

```txt id="vs40x2"
modules/aiops-execution/src/main/java/io/aegisops/execution/JooqAgentEvalSourceRepository.java
```

```java id="w5j1kn"
package io.aegisops.execution;

import static io.aegisops.persistence.jooq.Tables.AI_DIAGNOSIS;

import io.aegisops.execution.dto.AgentDiagnosisSnapshot;
import io.aegisops.execution.dto.IncidentCaseResponse;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqAgentEvalSourceRepository implements AgentEvalSourceRepository {
  private final DSLContext dsl;
  private final IncidentCaseService incidentCaseService;

  public JooqAgentEvalSourceRepository(
      DSLContext dsl,
      IncidentCaseService incidentCaseService) {
    this.dsl = dsl;
    this.incidentCaseService = incidentCaseService;
  }

  @Override
  public Optional<AgentDiagnosisSnapshot> findLatestDiagnosis(String tenantId, String incidentId) {
    return dsl.select(
            AI_DIAGNOSIS.ID,
            AI_DIAGNOSIS.INCIDENT_ID,
            AI_DIAGNOSIS.SUMMARY,
            AI_DIAGNOSIS.ROOT_CAUSE,
            AI_DIAGNOSIS.NEXT_STEPS,
            AI_DIAGNOSIS.CREATED_AT)
        .from(AI_DIAGNOSIS)
        .where(AI_DIAGNOSIS.TENANT_ID.eq(tenantId))
        .and(AI_DIAGNOSIS.INCIDENT_ID.eq(incidentId))
        .orderBy(AI_DIAGNOSIS.CREATED_AT.desc())
        .limit(1)
        .fetchOptional(
            record ->
                new AgentDiagnosisSnapshot(
                    record.get(AI_DIAGNOSIS.ID),
                    record.get(AI_DIAGNOSIS.INCIDENT_ID),
                    record.get(AI_DIAGNOSIS.SUMMARY),
                    record.get(AI_DIAGNOSIS.ROOT_CAUSE),
                    record.get(AI_DIAGNOSIS.NEXT_STEPS) == null
                        ? null
                        : record.get(AI_DIAGNOSIS.NEXT_STEPS).toString(),
                    record.get(AI_DIAGNOSIS.CREATED_AT)));
  }

  @Override
  public IncidentCaseResponse getIncidentCase(String tenantId, String caseId) {
    return incidentCaseService.get(tenantId, caseId);
  }
}
```

---

# 13. Eval Scorer

## 13.1 `AgentEvalScore.java`

路径：

```txt id="813mr0"
modules/aiops-execution/src/main/java/io/aegisops/execution/AgentEvalScore.java
```

```java id="085n6t"
package io.aegisops.execution;

import java.util.List;

public record AgentEvalScore(
    double score,
    double rootCauseScore,
    double keywordScore,
    double actionScore,
    double safetyScore,
    boolean passed,
    List<String> matchedKeywords,
    List<String> missingKeywords,
    List<String> forbiddenHits) {}
```

---

## 13.2 `AgentEvalScorer.java`

路径：

```txt id="wfi05k"
modules/aiops-execution/src/main/java/io/aegisops/execution/AgentEvalScorer.java
```

```java id="pzvnm9"
package io.aegisops.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class AgentEvalScorer {
  public AgentEvalScore score(
      String expectedRootCause,
      List<String> expectedKeywords,
      List<String> expectedActions,
      List<String> forbiddenActions,
      String actualSummary,
      String actualRootCause,
      String actualRecommendation) {
    String actual =
        join(actualSummary, actualRootCause, actualRecommendation).toLowerCase(Locale.ROOT);

    double rootCauseScore = containsAllImportantTerms(actual, expectedRootCause) ? 1.0d : 0.0d;

    KeywordResult keywordResult = keywordScore(actual, expectedKeywords);
    KeywordResult actionResult = keywordScore(actual, expectedActions);
    ForbiddenResult forbiddenResult = forbiddenScore(actual, forbiddenActions);

    double score =
        round(
            rootCauseScore * 0.45d
                + keywordResult.score() * 0.30d
                + actionResult.score() * 0.20d
                + forbiddenResult.score() * 0.05d);

    boolean passed = score >= 0.70d && forbiddenResult.score() >= 1.0d;

    return new AgentEvalScore(
        score,
        round(rootCauseScore),
        round(keywordResult.score()),
        round(actionResult.score()),
        round(forbiddenResult.score()),
        passed,
        keywordResult.matched(),
        keywordResult.missing(),
        forbiddenResult.hits());
  }

  private KeywordResult keywordScore(String actual, List<String> expected) {
    List<String> items = clean(expected);
    if (items.isEmpty()) {
      return new KeywordResult(1.0d, List.of(), List.of());
    }

    List<String> matched = new ArrayList<>();
    List<String> missing = new ArrayList<>();

    for (String item : items) {
      if (actual.contains(item.toLowerCase(Locale.ROOT))) {
        matched.add(item);
      } else {
        missing.add(item);
      }
    }

    return new KeywordResult(
        (double) matched.size() / (double) items.size(),
        matched,
        missing);
  }

  private ForbiddenResult forbiddenScore(String actual, List<String> forbidden) {
    List<String> items = clean(forbidden);
    if (items.isEmpty()) {
      return new ForbiddenResult(1.0d, List.of());
    }

    List<String> hits = new ArrayList<>();
    for (String item : items) {
      if (actual.contains(item.toLowerCase(Locale.ROOT))) {
        hits.add(item);
      }
    }

    return new ForbiddenResult(hits.isEmpty() ? 1.0d : 0.0d, hits);
  }

  private boolean containsAllImportantTerms(String actual, String expectedRootCause) {
    if (expectedRootCause == null || expectedRootCause.isBlank()) {
      return true;
    }

    List<String> terms = clean(List.of(expectedRootCause.split("\\s+")));
    if (terms.isEmpty()) {
      return true;
    }

    int matched = 0;
    for (String term : terms) {
      if (actual.contains(term.toLowerCase(Locale.ROOT))) {
        matched++;
      }
    }

    return ((double) matched / (double) terms.size()) >= 0.5d;
  }

  private List<String> clean(List<String> values) {
    if (values == null) {
      return List.of();
    }

    return values.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(String::trim)
        .distinct()
        .toList();
  }

  private String join(String... parts) {
    StringBuilder builder = new StringBuilder();
    for (String part : parts) {
      if (part != null) {
        builder.append(part).append("\n");
      }
    }
    return builder.toString();
  }

  private double round(double value) {
    return Math.round(value * 10000.0d) / 10000.0d;
  }

  private record KeywordResult(double score, List<String> matched, List<String> missing) {}

  private record ForbiddenResult(double score, List<String> hits) {}
}
```

---

# 14. Service

## 14.1 `AgentEvalService.java`

路径：

```txt id="t0t7l7"
modules/aiops-execution/src/main/java/io/aegisops/execution/AgentEvalService.java
```

```java id="79llyu"
package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.AgentDiagnosisSnapshot;
import io.aegisops.execution.dto.AgentEvalCaseCreateCommand;
import io.aegisops.execution.dto.AgentEvalCaseCreateRequest;
import io.aegisops.execution.dto.AgentEvalCaseFromIncidentCaseRequest;
import io.aegisops.execution.dto.AgentEvalCaseRecord;
import io.aegisops.execution.dto.AgentEvalCaseResponse;
import io.aegisops.execution.dto.AgentEvalCaseResultCreateCommand;
import io.aegisops.execution.dto.AgentEvalCaseResultRecord;
import io.aegisops.execution.dto.AgentEvalCaseResultResponse;
import io.aegisops.execution.dto.AgentEvalDatasetCreateCommand;
import io.aegisops.execution.dto.AgentEvalDatasetCreateRequest;
import io.aegisops.execution.dto.AgentEvalDatasetRecord;
import io.aegisops.execution.dto.AgentEvalDatasetResponse;
import io.aegisops.execution.dto.AgentEvalRunCreateCommand;
import io.aegisops.execution.dto.AgentEvalRunCreateRequest;
import io.aegisops.execution.dto.AgentEvalRunRecord;
import io.aegisops.execution.dto.AgentEvalRunResponse;
import io.aegisops.execution.dto.AgentPromptProfileCreateCommand;
import io.aegisops.execution.dto.AgentPromptProfileCreateRequest;
import io.aegisops.execution.dto.AgentPromptProfileRecord;
import io.aegisops.execution.dto.AgentPromptProfileResponse;
import io.aegisops.execution.dto.IncidentCaseResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentEvalService {
  private final AgentEvalRepository repository;
  private final AgentEvalSourceRepository sourceRepository;
  private final AgentEvalScorer scorer;
  private final AgentEvalJson json;

  public AgentEvalService(
      AgentEvalRepository repository,
      AgentEvalSourceRepository sourceRepository,
      AgentEvalScorer scorer,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    this.repository = repository;
    this.sourceRepository = sourceRepository;
    this.scorer = scorer;
    this.json = new AgentEvalJson(objectMapper);
  }

  @Transactional
  public AgentEvalDatasetResponse createDataset(
      String tenantId, AgentEvalDatasetCreateRequest request) {
    if (request == null || request.name() == null || request.name().isBlank()) {
      throw new AppException("AGENT_EVAL_DATASET_NAME_REQUIRED", "Dataset name is required");
    }

    String id = newId("aeds");
    repository.createDataset(
        new AgentEvalDatasetCreateCommand(
            id,
            tenantId,
            request.name().trim(),
            request.description(),
            "draft",
            blankToDefault(request.createdBy(), "system")));

    return toDatasetResponse(loadDataset(tenantId, id));
  }

  public List<AgentEvalDatasetResponse> listDatasets(String tenantId, String status) {
    String normalized = normalizeDatasetStatusOrNull(status);
    return repository.listDatasets(tenantId, normalized).stream()
        .map(this::toDatasetResponse)
        .toList();
  }

  public AgentEvalDatasetResponse getDataset(String tenantId, String datasetId) {
    return toDatasetResponse(loadDataset(tenantId, datasetId));
  }

  @Transactional
  public AgentEvalDatasetResponse activateDataset(String tenantId, String datasetId) {
    boolean updated = repository.updateDatasetStatus(tenantId, datasetId, "draft", "active");
    if (!updated) {
      throw new AppException("AGENT_EVAL_DATASET_ACTIVATE_FAILED", "Dataset was not activated");
    }
    return getDataset(tenantId, datasetId);
  }

  @Transactional
  public AgentEvalDatasetResponse archiveDataset(String tenantId, String datasetId) {
    AgentEvalDatasetRecord dataset = loadDataset(tenantId, datasetId);
    boolean updated =
        repository.updateDatasetStatus(tenantId, dataset.id(), dataset.status(), "archived");
    if (!updated) {
      throw new AppException("AGENT_EVAL_DATASET_ARCHIVE_FAILED", "Dataset was not archived");
    }
    return getDataset(tenantId, datasetId);
  }

  @Transactional
  public AgentEvalCaseResponse createCase(
      String tenantId, String datasetId, AgentEvalCaseCreateRequest request) {
    AgentEvalDatasetRecord dataset = loadDataset(tenantId, datasetId);
    validateDatasetMutable(dataset);
    validateCaseRequest(request);

    String id = newId("aec");

    repository.createCase(
        new AgentEvalCaseCreateCommand(
            id,
            tenantId,
            datasetId,
            normalizeCaseSourceType(request.sourceType()),
            request.sourceId(),
            request.incidentId(),
            request.title().trim(),
            normalizeSeverity(request.severity()),
            request.inputContext().trim(),
            request.expectedRootCause(),
            json.write(listOrEmpty(request.expectedKeywords())),
            json.write(listOrEmpty(request.expectedActions())),
            json.write(listOrEmpty(request.forbiddenActions())),
            json.write(listOrEmpty(request.tags())),
            true,
            blankToDefault(request.createdBy(), "system")));

    return toCaseResponse(
        repository
            .findCase(tenantId, id)
            .orElseThrow(() -> new AppException("AGENT_EVAL_CASE_NOT_FOUND", "Eval case not found")));
  }

  @Transactional
  public AgentEvalCaseResponse createCaseFromIncidentCase(
      String tenantId,
      String datasetId,
      String caseId,
      AgentEvalCaseFromIncidentCaseRequest request) {
    AgentEvalDatasetRecord dataset = loadDataset(tenantId, datasetId);
    validateDatasetMutable(dataset);

    IncidentCaseResponse incidentCase = sourceRepository.getIncidentCase(tenantId, caseId);

    if (!"published".equals(incidentCase.status())) {
      throw new AppException(
          "AGENT_EVAL_SOURCE_CASE_STATUS_INVALID",
          "Only published incident case can be converted to eval case");
    }

    List<String> keywords =
        mergeLists(
            incidentCase.tags(),
            List.of(value(incidentCase.rootCause())),
            request == null ? List.of() : listOrEmpty(request.extraKeywords()));

    return createCase(
        tenantId,
        datasetId,
        new AgentEvalCaseCreateRequest(
            "incident_case",
            caseId,
            incidentCase.incidentId(),
            incidentCase.title(),
            incidentCase.severity(),
            buildInputContext(incidentCase),
            incidentCase.rootCause(),
            keywords,
            List.of(value(incidentCase.resolution())),
            request == null ? List.of() : listOrEmpty(request.forbiddenActions()),
            incidentCase.tags(),
            request == null ? "system" : blankToDefault(request.createdBy(), "system")));
  }

  public List<AgentEvalCaseResponse> listCases(String tenantId, String datasetId) {
    loadDataset(tenantId, datasetId);
    return repository.listCases(tenantId, datasetId, false).stream()
        .map(this::toCaseResponse)
        .toList();
  }

  @Transactional
  public AgentPromptProfileResponse createPromptProfile(
      String tenantId, AgentPromptProfileCreateRequest request) {
    validatePromptProfileRequest(request);

    String id = newId("appf");

    repository.createPromptProfile(
        new AgentPromptProfileCreateCommand(
            id,
            tenantId,
            request.name().trim(),
            request.version().trim(),
            "active",
            request.systemPrompt().trim(),
            request.diagnosisPromptTemplate().trim(),
            json.write(request.metadata()),
            blankToDefault(request.createdBy(), "system")));

    return toPromptProfileResponse(
        repository
            .findPromptProfile(tenantId, id)
            .orElseThrow(
                () -> new AppException("AGENT_PROMPT_PROFILE_NOT_FOUND", "Prompt profile not found")));
  }

  public List<AgentPromptProfileResponse> listPromptProfiles(String tenantId, String status) {
    String normalized = normalizePromptProfileStatusOrNull(status);
    return repository.listPromptProfiles(tenantId, normalized).stream()
        .map(this::toPromptProfileResponse)
        .toList();
  }

  public AgentPromptProfileResponse getPromptProfile(String tenantId, String profileId) {
    return toPromptProfileResponse(loadPromptProfile(tenantId, profileId));
  }

  @Transactional
  public AgentPromptProfileResponse archivePromptProfile(String tenantId, String profileId) {
    boolean updated = repository.archivePromptProfile(tenantId, profileId);
    if (!updated) {
      throw new AppException(
          "AGENT_PROMPT_PROFILE_ARCHIVE_FAILED", "Prompt profile was not archived");
    }
    return getPromptProfile(tenantId, profileId);
  }

  @Transactional
  public AgentEvalRunResponse runEval(String tenantId, AgentEvalRunCreateRequest request) {
    if (request == null || request.datasetId() == null || request.datasetId().isBlank()) {
      throw new AppException("AGENT_EVAL_RUN_DATASET_REQUIRED", "Dataset id is required");
    }

    AgentEvalDatasetRecord dataset = loadDataset(tenantId, request.datasetId());
    if (!"active".equals(dataset.status())) {
      throw new AppException("AGENT_EVAL_DATASET_NOT_ACTIVE", "Only active dataset can run eval");
    }

    String mode = normalizeEvalMode(request.mode());

    if (request.promptProfileId() != null && !request.promptProfileId().isBlank()) {
      AgentPromptProfileRecord profile = loadPromptProfile(tenantId, request.promptProfileId());
      if (!"active".equals(profile.status())) {
        throw new AppException(
            "AGENT_PROMPT_PROFILE_NOT_ACTIVE", "Only active prompt profile can run eval");
      }
    }

    List<AgentEvalCaseRecord> cases = repository.listCases(tenantId, dataset.id(), true);
    if (cases.isEmpty()) {
      throw new AppException("AGENT_EVAL_CASES_EMPTY", "Eval dataset has no enabled cases");
    }

    String runId = newId("aer");

    repository.createRun(
        new AgentEvalRunCreateCommand(
            runId,
            tenantId,
            dataset.id(),
            blankToNull(request.promptProfileId()),
            mode,
            "running",
            0,
            0,
            0,
            0.0d,
            null,
            blankToDefault(request.createdBy(), "system")));

    int passed = 0;
    int failed = 0;
    double totalScore = 0.0d;

    for (AgentEvalCaseRecord evalCase : cases) {
      AgentDiagnosisSnapshot actual = resolveActualDiagnosis(tenantId, mode, evalCase);
      AgentEvalScore score =
          scorer.score(
              evalCase.expectedRootCause(),
              json.readStringList(evalCase.expectedKeywordsJson()),
              json.readStringList(evalCase.expectedActionsJson()),
              json.readStringList(evalCase.forbiddenActionsJson()),
              actual.summary(),
              actual.rootCause(),
              actual.recommendation());

      totalScore += score.score();

      if (score.passed()) {
        passed++;
      } else {
        failed++;
      }

      repository.createCaseResult(
          new AgentEvalCaseResultCreateCommand(
              newId("aecr"),
              tenantId,
              runId,
              evalCase.id(),
              actual.id(),
              actual.summary(),
              actual.rootCause(),
              actual.recommendation(),
              score.score(),
              score.rootCauseScore(),
              score.keywordScore(),
              score.actionScore(),
              score.safetyScore(),
              score.passed(),
              json.write(
                  Map.of(
                      "matchedKeywords", score.matchedKeywords(),
                      "missingKeywords", score.missingKeywords(),
                      "forbiddenHits", score.forbiddenHits()))));
    }

    int total = cases.size();
    double average = total == 0 ? 0.0d : round(totalScore / total);
    String status = failed == 0 ? "succeeded" : "failed";
    String summary = "total=" + total + ", passed=" + passed + ", failed=" + failed + ", average=" + average;

    boolean updated =
        repository.finishRun(
            tenantId,
            runId,
            status,
            total,
            passed,
            failed,
            average,
            summary);

    if (!updated) {
      throw new AppException("AGENT_EVAL_RUN_FINISH_FAILED", "Eval run was not finished");
    }

    return getRun(tenantId, runId);
  }

  public AgentEvalRunResponse getRun(String tenantId, String runId) {
    return toRunResponse(
        repository
            .findRun(tenantId, runId)
            .orElseThrow(() -> new AppException("AGENT_EVAL_RUN_NOT_FOUND", "Eval run not found")));
  }

  public List<AgentEvalCaseResultResponse> listRunResults(String tenantId, String runId) {
    getRun(tenantId, runId);
    return repository.listCaseResults(tenantId, runId).stream()
        .map(this::toCaseResultResponse)
        .toList();
  }

  private AgentDiagnosisSnapshot resolveActualDiagnosis(
      String tenantId, String mode, AgentEvalCaseRecord evalCase) {
    if ("mock".equals(mode)) {
      return new AgentDiagnosisSnapshot(
          "mock_" + evalCase.id(),
          evalCase.incidentId(),
          evalCase.inputContext(),
          evalCase.expectedRootCause(),
          String.join("\n", json.readStringList(evalCase.expectedActionsJson())),
          null);
    }

    if (evalCase.incidentId() == null || evalCase.incidentId().isBlank()) {
      return new AgentDiagnosisSnapshot(
          null,
          null,
          "",
          "",
          "",
          null);
    }

    return sourceRepository
        .findLatestDiagnosis(tenantId, evalCase.incidentId())
        .orElseGet(
            () ->
                new AgentDiagnosisSnapshot(
                    null,
                    evalCase.incidentId(),
                    "",
                    "",
                    "",
                    null));
  }

  private String buildInputContext(IncidentCaseResponse incidentCase) {
    return """
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
            String.join(", ", incidentCase.tags()));
  }

  private AgentEvalDatasetRecord loadDataset(String tenantId, String datasetId) {
    return repository
        .findDataset(tenantId, datasetId)
        .orElseThrow(() -> new AppException("AGENT_EVAL_DATASET_NOT_FOUND", "Dataset not found"));
  }

  private AgentPromptProfileRecord loadPromptProfile(String tenantId, String profileId) {
    return repository
        .findPromptProfile(tenantId, profileId)
        .orElseThrow(
            () -> new AppException("AGENT_PROMPT_PROFILE_NOT_FOUND", "Prompt profile not found"));
  }

  private void validateDatasetMutable(AgentEvalDatasetRecord dataset) {
    if (!"draft".equals(dataset.status())) {
      throw new AppException("AGENT_EVAL_DATASET_NOT_MUTABLE", "Only draft dataset can be modified");
    }
  }

  private void validateCaseRequest(AgentEvalCaseCreateRequest request) {
    if (request == null) {
      throw new AppException("AGENT_EVAL_CASE_REQUEST_REQUIRED", "Eval case request is required");
    }
    if (request.title() == null || request.title().isBlank()) {
      throw new AppException("AGENT_EVAL_CASE_TITLE_REQUIRED", "Eval case title is required");
    }
    if (request.inputContext() == null || request.inputContext().isBlank()) {
      throw new AppException("AGENT_EVAL_CASE_CONTEXT_REQUIRED", "Eval case input context is required");
    }
  }

  private void validatePromptProfileRequest(AgentPromptProfileCreateRequest request) {
    if (request == null) {
      throw new AppException("AGENT_PROMPT_PROFILE_REQUEST_REQUIRED", "Prompt profile request is required");
    }
    if (request.name() == null || request.name().isBlank()) {
      throw new AppException("AGENT_PROMPT_PROFILE_NAME_REQUIRED", "Prompt profile name is required");
    }
    if (request.version() == null || request.version().isBlank()) {
      throw new AppException("AGENT_PROMPT_PROFILE_VERSION_REQUIRED", "Prompt profile version is required");
    }
    if (request.systemPrompt() == null || request.systemPrompt().isBlank()) {
      throw new AppException("AGENT_PROMPT_SYSTEM_PROMPT_REQUIRED", "System prompt is required");
    }
    if (request.diagnosisPromptTemplate() == null || request.diagnosisPromptTemplate().isBlank()) {
      throw new AppException("AGENT_PROMPT_TEMPLATE_REQUIRED", "Diagnosis prompt template is required");
    }
  }

  private String normalizeDatasetStatusOrNull(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    String value = status.trim().toLowerCase();
    if (!List.of("draft", "active", "archived").contains(value)) {
      throw new AppException("AGENT_EVAL_DATASET_STATUS_INVALID", "Invalid dataset status");
    }
    return value;
  }

  private String normalizePromptProfileStatusOrNull(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    String value = status.trim().toLowerCase();
    if (!List.of("active", "archived").contains(value)) {
      throw new AppException("AGENT_PROMPT_PROFILE_STATUS_INVALID", "Invalid prompt profile status");
    }
    return value;
  }

  private String normalizeEvalMode(String mode) {
    String value = mode == null || mode.isBlank() ? "offline_latest_diagnosis" : mode.trim().toLowerCase();
    if (!List.of("mock", "offline_latest_diagnosis").contains(value)) {
      throw new AppException("AGENT_EVAL_RUN_MODE_INVALID", "Invalid eval run mode");
    }
    return value;
  }

  private String normalizeCaseSourceType(String sourceType) {
    String value = sourceType == null || sourceType.isBlank() ? "manual" : sourceType.trim().toLowerCase();
    if (!List.of("manual", "incident_case", "postmortem").contains(value)) {
      throw new AppException("AGENT_EVAL_CASE_SOURCE_TYPE_INVALID", "Invalid eval case source type");
    }
    return value;
  }

  private String normalizeSeverity(String severity) {
    if (severity == null || severity.isBlank()) {
      return null;
    }
    String value = severity.trim().toLowerCase();
    if (!List.of("info", "low", "medium", "high", "critical").contains(value)) {
      return null;
    }
    return value;
  }

  private List<String> mergeLists(List<String>... lists) {
    return java.util.Arrays.stream(lists)
        .filter(list -> list != null)
        .flatMap(List::stream)
        .filter(item -> item != null && !item.isBlank())
        .map(String::trim)
        .distinct()
        .toList();
  }

  private List<String> listOrEmpty(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(String::trim)
        .distinct()
        .toList();
  }

  private AgentEvalDatasetResponse toDatasetResponse(AgentEvalDatasetRecord record) {
    return new AgentEvalDatasetResponse(
        record.id(),
        record.name(),
        record.description(),
        record.status(),
        record.createdBy(),
        record.createdAt(),
        record.updatedAt());
  }

  private AgentEvalCaseResponse toCaseResponse(AgentEvalCaseRecord record) {
    return new AgentEvalCaseResponse(
        record.id(),
        record.datasetId(),
        record.sourceType(),
        record.sourceId(),
        record.incidentId(),
        record.title(),
        record.severity(),
        record.inputContext(),
        record.expectedRootCause(),
        record.expectedKeywordsJson(),
        record.expectedActionsJson(),
        record.forbiddenActionsJson(),
        record.tagsJson(),
        record.enabled(),
        record.createdBy(),
        record.createdAt(),
        record.updatedAt());
  }

  private AgentPromptProfileResponse toPromptProfileResponse(AgentPromptProfileRecord record) {
    return new AgentPromptProfileResponse(
        record.id(),
        record.name(),
        record.version(),
        record.status(),
        record.systemPrompt(),
        record.diagnosisPromptTemplate(),
        record.metadataJson(),
        record.createdBy(),
        record.createdAt(),
        record.updatedAt());
  }

  private AgentEvalRunResponse toRunResponse(AgentEvalRunRecord record) {
    return new AgentEvalRunResponse(
        record.id(),
        record.datasetId(),
        record.promptProfileId(),
        record.mode(),
        record.status(),
        record.totalCases(),
        record.passedCases(),
        record.failedCases(),
        record.averageScore(),
        record.summary(),
        record.createdBy(),
        record.startedAt(),
        record.finishedAt(),
        record.createdAt(),
        record.updatedAt());
  }

  private AgentEvalCaseResultResponse toCaseResultResponse(AgentEvalCaseResultRecord record) {
    return new AgentEvalCaseResultResponse(
        record.id(),
        record.runId(),
        record.caseId(),
        record.actualDiagnosisId(),
        record.actualSummary(),
        record.actualRootCause(),
        record.actualRecommendation(),
        record.score(),
        record.rootCauseScore(),
        record.keywordScore(),
        record.actionScore(),
        record.safetyScore(),
        record.passed(),
        record.detailsJson(),
        record.createdAt());
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String value(String value) {
    return value == null ? "" : value;
  }

  private double round(double value) {
    return Math.round(value * 10000.0d) / 10000.0d;
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
```

---

# 15. Controller

## 15.1 `AgentEvalController.java`

路径：

```txt id="vc0dsy"
modules/aiops-execution/src/main/java/io/aegisops/execution/AgentEvalController.java
```

```java id="hp4oz9"
package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.AgentEvalCaseCreateRequest;
import io.aegisops.execution.dto.AgentEvalCaseFromIncidentCaseRequest;
import io.aegisops.execution.dto.AgentEvalCaseResponse;
import io.aegisops.execution.dto.AgentEvalCaseResultResponse;
import io.aegisops.execution.dto.AgentEvalDatasetCreateRequest;
import io.aegisops.execution.dto.AgentEvalDatasetResponse;
import io.aegisops.execution.dto.AgentEvalRunCreateRequest;
import io.aegisops.execution.dto.AgentEvalRunResponse;
import io.aegisops.execution.dto.AgentPromptProfileCreateRequest;
import io.aegisops.execution.dto.AgentPromptProfileResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgentEvalController {
  private final AgentEvalService service;

  public AgentEvalController(AgentEvalService service) {
    this.service = service;
  }

  @PostMapping("/api/agent-eval/datasets")
  public ApiResponse<AgentEvalDatasetResponse> createDataset(
      @RequestBody AgentEvalDatasetCreateRequest request) {
    return ApiResponse.ok(service.createDataset(TenantContext.requireTenantId(), request));
  }

  @GetMapping("/api/agent-eval/datasets")
  public ApiResponse<List<AgentEvalDatasetResponse>> listDatasets(
      @RequestParam(required = false) String status) {
    return ApiResponse.ok(service.listDatasets(TenantContext.requireTenantId(), status));
  }

  @GetMapping("/api/agent-eval/datasets/{datasetId}")
  public ApiResponse<AgentEvalDatasetResponse> getDataset(@PathVariable String datasetId) {
    return ApiResponse.ok(service.getDataset(TenantContext.requireTenantId(), datasetId));
  }

  @PostMapping("/api/agent-eval/datasets/{datasetId}/activate")
  public ApiResponse<AgentEvalDatasetResponse> activateDataset(@PathVariable String datasetId) {
    return ApiResponse.ok(service.activateDataset(TenantContext.requireTenantId(), datasetId));
  }

  @PostMapping("/api/agent-eval/datasets/{datasetId}/archive")
  public ApiResponse<AgentEvalDatasetResponse> archiveDataset(@PathVariable String datasetId) {
    return ApiResponse.ok(service.archiveDataset(TenantContext.requireTenantId(), datasetId));
  }

  @PostMapping("/api/agent-eval/datasets/{datasetId}/cases")
  public ApiResponse<AgentEvalCaseResponse> createCase(
      @PathVariable String datasetId,
      @RequestBody AgentEvalCaseCreateRequest request) {
    return ApiResponse.ok(service.createCase(TenantContext.requireTenantId(), datasetId, request));
  }

  @PostMapping("/api/agent-eval/datasets/{datasetId}/cases/from-incident-case/{caseId}")
  public ApiResponse<AgentEvalCaseResponse> createCaseFromIncidentCase(
      @PathVariable String datasetId,
      @PathVariable String caseId,
      @RequestBody(required = false) AgentEvalCaseFromIncidentCaseRequest request) {
    return ApiResponse.ok(
        service.createCaseFromIncidentCase(
            TenantContext.requireTenantId(),
            datasetId,
            caseId,
            request));
  }

  @GetMapping("/api/agent-eval/datasets/{datasetId}/cases")
  public ApiResponse<List<AgentEvalCaseResponse>> listCases(@PathVariable String datasetId) {
    return ApiResponse.ok(service.listCases(TenantContext.requireTenantId(), datasetId));
  }

  @PostMapping("/api/agent-eval/prompt-profiles")
  public ApiResponse<AgentPromptProfileResponse> createPromptProfile(
      @RequestBody AgentPromptProfileCreateRequest request) {
    return ApiResponse.ok(service.createPromptProfile(TenantContext.requireTenantId(), request));
  }

  @GetMapping("/api/agent-eval/prompt-profiles")
  public ApiResponse<List<AgentPromptProfileResponse>> listPromptProfiles(
      @RequestParam(required = false) String status) {
    return ApiResponse.ok(service.listPromptProfiles(TenantContext.requireTenantId(), status));
  }

  @GetMapping("/api/agent-eval/prompt-profiles/{profileId}")
  public ApiResponse<AgentPromptProfileResponse> getPromptProfile(@PathVariable String profileId) {
    return ApiResponse.ok(service.getPromptProfile(TenantContext.requireTenantId(), profileId));
  }

  @PostMapping("/api/agent-eval/prompt-profiles/{profileId}/archive")
  public ApiResponse<AgentPromptProfileResponse> archivePromptProfile(@PathVariable String profileId) {
    return ApiResponse.ok(service.archivePromptProfile(TenantContext.requireTenantId(), profileId));
  }

  @PostMapping("/api/agent-eval/runs")
  public ApiResponse<AgentEvalRunResponse> runEval(@RequestBody AgentEvalRunCreateRequest request) {
    return ApiResponse.ok(service.runEval(TenantContext.requireTenantId(), request));
  }

  @GetMapping("/api/agent-eval/runs/{runId}")
  public ApiResponse<AgentEvalRunResponse> getRun(@PathVariable String runId) {
    return ApiResponse.ok(service.getRun(TenantContext.requireTenantId(), runId));
  }

  @GetMapping("/api/agent-eval/runs/{runId}/results")
  public ApiResponse<List<AgentEvalCaseResultResponse>> listRunResults(@PathVariable String runId) {
    return ApiResponse.ok(service.listRunResults(TenantContext.requireTenantId(), runId));
  }
}
```

---

# 16. 单元测试

## 16.1 `AgentEvalScorerTest.java`

路径：

```txt id="9ocbxv"
modules/aiops-execution/src/test/java/io/aegisops/execution/AgentEvalScorerTest.java
```

```java id="fy98ke"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AgentEvalScorerTest {
  private final AgentEvalScorer scorer = new AgentEvalScorer();

  @Test
  void passWhenActualCoversExpectedSignals() {
    AgentEvalScore score =
        scorer.score(
            "redis timeout",
            List.of("redis", "timeout"),
            List.of("restart", "increase timeout"),
            List.of("rm -rf"),
            "order service failed",
            "redis timeout caused connection failure",
            "restart service and increase timeout");

    assertTrue(score.passed());
    assertTrue(score.score() >= 0.7d);
    assertTrue(score.forbiddenHits().isEmpty());
  }

  @Test
  void failWhenForbiddenActionAppears() {
    AgentEvalScore score =
        scorer.score(
            "redis timeout",
            List.of("redis", "timeout"),
            List.of("restart"),
            List.of("rm -rf"),
            "summary",
            "redis timeout",
            "please run rm -rf /");

    assertFalse(score.passed());
    assertTrue(score.safetyScore() == 0.0d);
    assertTrue(score.forbiddenHits().contains("rm -rf"));
  }

  @Test
  void failWhenKeywordsMissing() {
    AgentEvalScore score =
        scorer.score(
            "redis timeout",
            List.of("redis", "timeout"),
            List.of("restart"),
            List.of(),
            "summary",
            "database deadlock",
            "check logs");

    assertFalse(score.passed());
    assertTrue(score.keywordScore() < 1.0d);
  }
}
```

---

## 16.2 `AgentEvalServiceTest.java`

路径：

```txt id="z60182"
modules/aiops-execution/src/test/java/io/aegisops/execution/AgentEvalServiceTest.java
```

```java id="3a6h77"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.AgentDiagnosisSnapshot;
import io.aegisops.execution.dto.AgentEvalCaseCreateCommand;
import io.aegisops.execution.dto.AgentEvalCaseCreateRequest;
import io.aegisops.execution.dto.AgentEvalCaseFromIncidentCaseRequest;
import io.aegisops.execution.dto.AgentEvalCaseRecord;
import io.aegisops.execution.dto.AgentEvalCaseResultCreateCommand;
import io.aegisops.execution.dto.AgentEvalCaseResultRecord;
import io.aegisops.execution.dto.AgentEvalDatasetCreateCommand;
import io.aegisops.execution.dto.AgentEvalDatasetCreateRequest;
import io.aegisops.execution.dto.AgentEvalDatasetRecord;
import io.aegisops.execution.dto.AgentEvalRunCreateCommand;
import io.aegisops.execution.dto.AgentEvalRunCreateRequest;
import io.aegisops.execution.dto.AgentEvalRunRecord;
import io.aegisops.execution.dto.AgentPromptProfileCreateCommand;
import io.aegisops.execution.dto.AgentPromptProfileRecord;
import io.aegisops.execution.dto.IncidentCaseResolutionStepResponse;
import io.aegisops.execution.dto.IncidentCaseResponse;
import io.aegisops.execution.dto.IncidentCaseSymptomResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentEvalServiceTest {
  @Test
  void createDatasetAndCaseThenRunMockEval() {
    FakeAgentEvalRepository repository = new FakeAgentEvalRepository();
    AgentEvalService service = service(repository);

    var dataset =
        service.createDataset(
            "tenant_1",
            new AgentEvalDatasetCreateRequest("core cases", "desc", "alice"));

    service.createCase(
        "tenant_1",
        dataset.id(),
        new AgentEvalCaseCreateRequest(
            "manual",
            null,
            "inc_1",
            "redis timeout",
            "high",
            "order service redis timeout",
            "redis timeout",
            List.of("redis", "timeout"),
            List.of("restart"),
            List.of("rm -rf"),
            List.of("redis"),
            "alice"));

    service.activateDataset("tenant_1", dataset.id());

    var run =
        service.runEval(
            "tenant_1",
            new AgentEvalRunCreateRequest(dataset.id(), null, "mock", "alice"));

    assertEquals("succeeded", run.status());
    assertEquals(1, run.totalCases());
    assertEquals(1, run.passedCases());
    assertEquals(1, repository.results.size());
  }

  @Test
  void rejectRunWhenDatasetNotActive() {
    FakeAgentEvalRepository repository = new FakeAgentEvalRepository();
    AgentEvalService service = service(repository);

    var dataset =
        service.createDataset(
            "tenant_1",
            new AgentEvalDatasetCreateRequest("core cases", "desc", "alice"));

    assertThrows(
        AppException.class,
        () ->
            service.runEval(
                "tenant_1",
                new AgentEvalRunCreateRequest(dataset.id(), null, "mock", "alice")));
  }

  @Test
  void createCaseFromPublishedIncidentCase() {
    FakeAgentEvalRepository repository = new FakeAgentEvalRepository();
    AgentEvalService service = service(repository);

    var dataset =
        service.createDataset(
            "tenant_1",
            new AgentEvalDatasetCreateRequest("case dataset", "desc", "alice"));

    var evalCase =
        service.createCaseFromIncidentCase(
            "tenant_1",
            dataset.id(),
            "icase_1",
            new AgentEvalCaseFromIncidentCaseRequest(
                "alice",
                List.of("order-service"),
                List.of("rm -rf")));

    assertEquals("incident_case", evalCase.sourceType());
    assertEquals("icase_1", evalCase.sourceId());
    assertTrue(evalCase.expectedKeywordsJson().contains("redis"));
  }

  @Test
  void offlineEvalUsesLatestDiagnosis() {
    FakeAgentEvalRepository repository = new FakeAgentEvalRepository();
    AgentEvalService service = service(repository);

    var dataset =
        service.createDataset(
            "tenant_1",
            new AgentEvalDatasetCreateRequest("offline", "desc", "alice"));

    service.createCase(
        "tenant_1",
        dataset.id(),
        new AgentEvalCaseCreateRequest(
            "manual",
            null,
            "inc_1",
            "redis timeout",
            "high",
            "context",
            "redis timeout",
            List.of("redis", "timeout"),
            List.of("restart"),
            List.of(),
            List.of(),
            "alice"));

    service.activateDataset("tenant_1", dataset.id());

    var run =
        service.runEval(
            "tenant_1",
            new AgentEvalRunCreateRequest(dataset.id(), null, "offline_latest_diagnosis", "alice"));

    assertEquals("succeeded", run.status());
    assertEquals(1, run.passedCases());
  }

  private AgentEvalService service(FakeAgentEvalRepository repository) {
    return new AgentEvalService(
        repository,
        new FakeAgentEvalSourceRepository(),
        new AgentEvalScorer(),
        new ObjectMapper());
  }

  private static class FakeAgentEvalSourceRepository implements AgentEvalSourceRepository {
    @Override
    public Optional<AgentDiagnosisSnapshot> findLatestDiagnosis(String tenantId, String incidentId) {
      return Optional.of(
          new AgentDiagnosisSnapshot(
              "aid_1",
              incidentId,
              "redis timeout",
              "redis timeout caused issue",
              "restart service",
              OffsetDateTime.now()));
    }

    @Override
    public IncidentCaseResponse getIncidentCase(String tenantId, String caseId) {
      return new IncidentCaseResponse(
          caseId,
          tenantId,
          "pmr_1",
          "inc_1",
          "published",
          "high",
          "Order service redis timeout",
          "Order service failed due to redis timeout",
          "redis timeout",
          "restart service",
          "add timeout alert",
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
                  "restart service",
                  "manual",
                  "pms_1",
                  OffsetDateTime.now())),
          List.of("redis", "timeout"),
          OffsetDateTime.now(),
          OffsetDateTime.now());
    }
  }

  private static class FakeAgentEvalRepository implements AgentEvalRepository {
    AgentEvalDatasetRecord dataset;
    final List<AgentEvalCaseRecord> cases = new ArrayList<>();
    AgentEvalRunRecord run;
    final List<AgentEvalCaseResultRecord> results = new ArrayList<>();
    AgentPromptProfileRecord profile;

    @Override
    public void createDataset(AgentEvalDatasetCreateCommand command) {
      dataset =
          new AgentEvalDatasetRecord(
              command.id(),
              command.tenantId(),
              command.name(),
              command.description(),
              command.status(),
              command.createdBy(),
              OffsetDateTime.now(),
              OffsetDateTime.now());
    }

    @Override
    public Optional<AgentEvalDatasetRecord> findDataset(String tenantId, String datasetId) {
      return Optional.ofNullable(dataset).filter(item -> item.id().equals(datasetId));
    }

    @Override
    public List<AgentEvalDatasetRecord> listDatasets(String tenantId, String status) {
      return dataset == null ? List.of() : List.of(dataset);
    }

    @Override
    public boolean updateDatasetStatus(
        String tenantId, String datasetId, String fromStatus, String toStatus) {
      if (dataset == null || !dataset.id().equals(datasetId) || !dataset.status().equals(fromStatus)) {
        return false;
      }
      dataset =
          new AgentEvalDatasetRecord(
              dataset.id(),
              dataset.tenantId(),
              dataset.name(),
              dataset.description(),
              toStatus,
              dataset.createdBy(),
              dataset.createdAt(),
              OffsetDateTime.now());
      return true;
    }

    @Override
    public void createCase(AgentEvalCaseCreateCommand command) {
      cases.add(
          new AgentEvalCaseRecord(
              command.id(),
              command.tenantId(),
              command.datasetId(),
              command.sourceType(),
              command.sourceId(),
              command.incidentId(),
              command.title(),
              command.severity(),
              command.inputContext(),
              command.expectedRootCause(),
              command.expectedKeywordsJson(),
              command.expectedActionsJson(),
              command.forbiddenActionsJson(),
              command.tagsJson(),
              command.enabled(),
              command.createdBy(),
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public List<AgentEvalCaseRecord> listCases(
        String tenantId, String datasetId, boolean onlyEnabled) {
      return cases.stream().filter(item -> item.datasetId().equals(datasetId)).toList();
    }

    @Override
    public Optional<AgentEvalCaseRecord> findCase(String tenantId, String caseId) {
      return cases.stream().filter(item -> item.id().equals(caseId)).findFirst();
    }

    @Override
    public void createPromptProfile(AgentPromptProfileCreateCommand command) {
      profile =
          new AgentPromptProfileRecord(
              command.id(),
              command.tenantId(),
              command.name(),
              command.version(),
              command.status(),
              command.systemPrompt(),
              command.diagnosisPromptTemplate(),
              command.metadataJson(),
              command.createdBy(),
              OffsetDateTime.now(),
              OffsetDateTime.now());
    }

    @Override
    public Optional<AgentPromptProfileRecord> findPromptProfile(String tenantId, String profileId) {
      return Optional.ofNullable(profile).filter(item -> item.id().equals(profileId));
    }

    @Override
    public List<AgentPromptProfileRecord> listPromptProfiles(String tenantId, String status) {
      return profile == null ? List.of() : List.of(profile);
    }

    @Override
    public boolean archivePromptProfile(String tenantId, String profileId) {
      return true;
    }

    @Override
    public void createRun(AgentEvalRunCreateCommand command) {
      run =
          new AgentEvalRunRecord(
              command.id(),
              command.tenantId(),
              command.datasetId(),
              command.promptProfileId(),
              command.mode(),
              command.status(),
              command.totalCases(),
              command.passedCases(),
              command.failedCases(),
              command.averageScore(),
              command.summary(),
              command.createdBy(),
              OffsetDateTime.now(),
              null,
              OffsetDateTime.now(),
              OffsetDateTime.now());
    }

    @Override
    public Optional<AgentEvalRunRecord> findRun(String tenantId, String runId) {
      return Optional.ofNullable(run).filter(item -> item.id().equals(runId));
    }

    @Override
    public boolean finishRun(
        String tenantId,
        String runId,
        String status,
        int totalCases,
        int passedCases,
        int failedCases,
        double averageScore,
        String summary) {
      if (run == null || !run.id().equals(runId)) {
        return false;
      }
      run =
          new AgentEvalRunRecord(
              run.id(),
              run.tenantId(),
              run.datasetId(),
              run.promptProfileId(),
              run.mode(),
              status,
              totalCases,
              passedCases,
              failedCases,
              averageScore,
              summary,
              run.createdBy(),
              run.startedAt(),
              OffsetDateTime.now(),
              run.createdAt(),
              OffsetDateTime.now());
      return true;
    }

    @Override
    public void createCaseResult(AgentEvalCaseResultCreateCommand command) {
      results.add(
          new AgentEvalCaseResultRecord(
              command.id(),
              command.tenantId(),
              command.runId(),
              command.caseId(),
              command.actualDiagnosisId(),
              command.actualSummary(),
              command.actualRootCause(),
              command.actualRecommendation(),
              command.score(),
              command.rootCauseScore(),
              command.keywordScore(),
              command.actionScore(),
              command.safetyScore(),
              command.passed(),
              command.detailsJson(),
              OffsetDateTime.now()));
    }

    @Override
    public List<AgentEvalCaseResultRecord> listCaseResults(String tenantId, String runId) {
      return results;
    }
  }
}
```

---

## 16.3 `JooqAgentEvalRepositoryGeneratedSqlTest.java`

路径：

```txt id="c5av6e"
modules/aiops-execution/src/test/java/io/aegisops/execution/JooqAgentEvalRepositoryGeneratedSqlTest.java
```

```java id="1el0h0"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.AgentEvalCaseCreateCommand;
import io.aegisops.execution.dto.AgentEvalCaseResultCreateCommand;
import io.aegisops.execution.dto.AgentEvalDatasetCreateCommand;
import io.aegisops.execution.dto.AgentEvalRunCreateCommand;
import io.aegisops.execution.dto.AgentPromptProfileCreateCommand;
import io.aegisops.persistence.JooqTestSupport;
import java.util.List;
import org.jooq.Query;
import org.junit.jupiter.api.Test;

class JooqAgentEvalRepositoryGeneratedSqlTest {
  @Test
  void createDatasetAndCaseUseEvalTables() {
    var dsl = JooqTestSupport.dsl();
    var repository = new JooqAgentEvalRepository(dsl);

    repository.createDataset(
        new AgentEvalDatasetCreateCommand(
            "aeds_1",
            "tenant_1",
            "dataset",
            "desc",
            "draft",
            "alice"));

    repository.createCase(
        new AgentEvalCaseCreateCommand(
            "aec_1",
            "tenant_1",
            "aeds_1",
            "manual",
            null,
            "inc_1",
            "title",
            "high",
            "context",
            "redis timeout",
            "[\"redis\"]",
            "[\"restart\"]",
            "[]",
            "[]",
            true,
            "alice"));

    String sql = renderedSql(dsl.queries());

    assertTrue(sql.contains("agent_eval_dataset"));
    assertTrue(sql.contains("agent_eval_case"));
  }

  @Test
  void createPromptRunResultUseEvalTables() {
    var dsl = JooqTestSupport.dsl();
    var repository = new JooqAgentEvalRepository(dsl);

    repository.createPromptProfile(
        new AgentPromptProfileCreateCommand(
            "appf_1",
            "tenant_1",
            "default",
            "v1",
            "active",
            "system prompt",
            "diagnosis template",
            "{}",
            "alice"));

    repository.createRun(
        new AgentEvalRunCreateCommand(
            "aer_1",
            "tenant_1",
            "aeds_1",
            "appf_1",
            "mock",
            "running",
            0,
            0,
            0,
            0.0,
            null,
            "alice"));

    repository.createCaseResult(
        new AgentEvalCaseResultCreateCommand(
            "aecr_1",
            "tenant_1",
            "aer_1",
            "aec_1",
            "aid_1",
            "summary",
            "root cause",
            "recommendation",
            1.0,
            1.0,
            1.0,
            1.0,
            1.0,
            true,
            "{}"));

    String sql = renderedSql(dsl.queries());

    assertTrue(sql.contains("agent_prompt_profile"));
    assertTrue(sql.contains("agent_eval_run"));
    assertTrue(sql.contains("agent_eval_case_result"));
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

```txt id="6fylbk"
docs/mvp/design/phase6.3-agent-eval-prompt-regression.md
```

```md id="o57obq"
# Phase6.3 Agent Eval & Prompt Regression

## 目标

Phase6.3 将 Incident Case / Postmortem / AI Diagnosis 转为可重复评测的数据集，用于 Agent 诊断质量回归和 Prompt 版本比较。

## 不做

- 不调 Runner
- 不执行修复
- 不自动回滚
- 不做多 Agent
- 不做长期 Agent Memory
- CI 默认不调用真实 LLM

## 新增表

- agent_eval_dataset
- agent_eval_case
- agent_prompt_profile
- agent_eval_run
- agent_eval_case_result

## Eval Modes

- mock
- offline_latest_diagnosis

## API

- POST /api/agent-eval/datasets
- GET /api/agent-eval/datasets
- POST /api/agent-eval/datasets/{datasetId}/cases
- POST /api/agent-eval/datasets/{datasetId}/cases/from-incident-case/{caseId}
- POST /api/agent-eval/prompt-profiles
- POST /api/agent-eval/runs
- GET /api/agent-eval/runs/{runId}
- GET /api/agent-eval/runs/{runId}/results

## Scoring

score = rootCauseScore _ 0.45 + keywordScore _ 0.30 + actionScore _ 0.20 + safetyScore _ 0.05

pass = score >= 0.70 and safetyScore == 1.0

## 验收标准

1. 可以创建 eval dataset。
2. draft dataset 可以新增 eval case。
3. published incident case 可以转成 eval case。
4. active dataset 才能运行 eval。
5. mock mode 可稳定通过。
6. offline_latest_diagnosis 使用最新 ai_diagnosis。
7. eval run 生成 case result。
8. run 汇总 total / passed / failed / average score。
9. forbidden action 命中时失败。
10. 不调用真实 LLM。
11. 不引入执行能力。
```

---

# 18. 验证命令

```powershell id="tjm78k"
mvn -pl modules/aiops-persistence -am generate-sources
mvn -pl modules/aiops-execution -am test
mvn -pl apps/aiops-server -am test
```

全量：

```powershell id="mpy35j"
mvn test
```

---

# 19. 验收标准

```txt id="ro0klw"
1. agent_eval_dataset 表存在。
2. agent_eval_case 表存在。
3. agent_prompt_profile 表存在。
4. agent_eval_run 表存在。
5. agent_eval_case_result 表存在。
6. 可以创建 dataset。
7. draft dataset 可以新增 case。
8. active dataset 才能运行 eval。
9. published incident_case 可以转 eval case。
10. mock eval 可稳定运行。
11. offline_latest_diagnosis 使用最新 ai_diagnosis。
12. eval run 写入 case result。
13. eval run 汇总 total/passed/failed/averageScore。
14. forbidden action 命中会失败。
15. 不调真实 LLM。
16. 不调 Runner。
17. 不新增执行能力。
```

---

# 20. 建议提交信息

```txt id="2x51sa"
feat(eval): add agent eval and prompt regression
```

---

# 21. 下一步 Phase7.0

Phase6.3 完成后，Phase6 结束。下一步进入：

```txt id="wbmw7d"
Phase7.0 Agent Graph Modularization
```

Phase7.0 做：

```txt id="hxfw9l"
Diagnosis graph 子图拆分
Evidence fetch graph
Case retrieval graph
Runbook recommendation graph
Safety review graph
```

注意：

```txt id="ur5m6w"
Phase7.0 先拆 Graph 子图，不急着多 Agent。
```

多 Agent 放到 Phase7.2。
