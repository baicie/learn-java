---
title: Phase4：Python LangGraph OSS Diagnosis Agent + Java Agent Client
type: design
status: deprecated
phase: global
owner: ai
created: 2026-06-30
updated: 2026-07-30
related:
  - docs/adr/0010-service-authentication-oauth2-only.md
---

# Phase4：Python LangGraph OSS Diagnosis Agent + Java Agent Client

> 历史设计（已废弃）：本文保留最初实现过程，其中静态 Token 配置与代码示例不得继续
> 执行。当前 Java/Agent 服务鉴权以
> `docs/adr/0010-service-authentication-oauth2-only.md` 为准。

这版基于前面选型讨论重做：**不使用 Google ADK，不把 Agent 写进 Java**。Phase4 采用 **LangGraph OSS Python** 作为 Agent 编排底座，Java 只负责产品后端职责：鉴权、租户隔离、读取 Incident/RCA/Alert、调用 Python Agent、保存诊断结果、写 Timeline。

LangGraph 官方 Python Quickstart 使用 `StateGraph`、`START`、`END` 来定义图节点与边，并通过 `compile()` 后 `invoke()` 执行图；这正适合 AIOps 这种固定诊断流程。([LangChain 文档][1]) FastAPI 测试使用 `fastapi.testclient.TestClient`，可以直接配合 pytest 写接口测试。([FastAPI][2])

---

# 1. Phase4 目标

```txt id="c6s3g7"
Phase4 = Python LangGraph Diagnosis Agent

Java aiops-server:
  - 鉴权 / 租户 / API
  - 查询 Incident / Alerts / RCA
  - 调用 Python aiops-agent
  - 保存 ai_diagnosis
  - 写 incident_timeline

Python aiops-agent:
  - FastAPI 服务
  - LangGraph OSS 图工作流
  - load_context
  - analyze_alerts
  - analyze_rca
  - query_metrics_stub
  - query_logs_stub
  - search_runbooks
  - safety_check
  - generate_diagnosis
```

---

# 2. 安全边界

Phase4 只能做：

```txt id="r2kyse"
1. 诊断说明
2. 根因候选
3. 影响范围
4. 排障步骤草稿
5. Runbook 推荐方向
6. 风险提示
```

Phase4 不能做：

```txt id="a6d7ci"
1. 自动执行 SSH
2. 自动执行 Ansible
3. 自动改配置
4. 自动重启服务
5. 自动关闭 Incident
6. 自动回滚
```

---

# 3. 目录结构

```txt id="gttnrt"
apps/
  aiops-agent/
    pyproject.toml
    Dockerfile
    README.md
    src/
      aiops_agent/
        __init__.py
        main.py
        settings.py
        schemas.py
        graph.py
        tools.py
        service.py
    tests/
      test_api.py
      test_graph.py
      test_schemas.py
      test_tools.py

modules/
  aiops-ai-client/
    pom.xml
    src/main/java/io/aegisops/ai/client/
      AgentClientProperties.java
      AiAgentClient.java
      HttpAiAgentClient.java
      AiDiagnosisController.java
      AiDiagnosisService.java
      AiRepository.java
      AiRows.java
      JdbcAiRepository.java
      dto/
        AgentAlertContext.java
        AgentDiagnosisRequest.java
        AgentDiagnosisResponse.java
        AgentIncidentContext.java
        AgentRcaContext.java
        AiAlertRecord.java
        AiDiagnoseRequest.java
        AiDiagnosisRecord.java
        AiDiagnosisResponse.java
        AiIncidentRecord.java
        AiRcaRecord.java
    src/test/java/io/aegisops/ai/client/
      AiDiagnosisServiceTest.java
      HttpAiAgentClientTest.java

apps/aiops-server/src/main/resources/db/migration/
  V5__phase4_ai_diagnosis.sql
```

---

# 4. Maven 修改

## 4.1 根 `pom.xml`

在 `<modules>` 里新增：

```xml id="yb3i6e"
<module>modules/aiops-ai-client</module>
```

建议模块顺序：

```xml id="8wrfme"
<modules>
    <module>modules/aiops-common</module>
    <module>modules/aiops-web</module>
    <module>modules/aiops-audit</module>
    <module>modules/aiops-tenant</module>
    <module>modules/aiops-user</module>
    <module>modules/aiops-security</module>
    <module>modules/aiops-zabbix-adapter</module>
    <module>modules/aiops-datasource</module>
    <module>modules/aiops-asset</module>
    <module>modules/aiops-alert</module>
    <module>modules/aiops-incident</module>
    <module>modules/aiops-rca</module>
    <module>modules/aiops-ai-client</module>
    <module>apps/aiops-server</module>
    <module>apps/aiops-worker</module>
    <module>apps/aiops-runner</module>
</modules>
```

---

## 4.2 `apps/aiops-server/pom.xml`

新增依赖：

```xml id="lci82m"
<dependency>
    <groupId>io.aegisops</groupId>
    <artifactId>aiops-ai-client</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

# 5. 数据库迁移

## `apps/aiops-server/src/main/resources/db/migration/V5__phase4_ai_diagnosis.sql`

```sql id="v91z6m"
create table if not exists ai_diagnosis (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  incident_id varchar(64) not null references incident(id) on delete cascade,
  status varchar(32) not null default 'completed',
  provider varchar(64) not null,
  model varchar(128) not null,
  agent_name varchar(128) not null default 'aegisops_diagnosis_graph',
  request_payload jsonb not null default '{}'::jsonb,
  response_raw jsonb not null default '{}'::jsonb,
  summary text not null,
  root_cause text not null,
  impact text not null,
  next_steps jsonb not null default '[]'::jsonb,
  runbook_suggestions jsonb not null default '[]'::jsonb,
  risks jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists idx_ai_diagnosis_tenant_incident_created
  on ai_diagnosis(tenant_id, incident_id, created_at desc);

create index if not exists idx_ai_diagnosis_incident
  on ai_diagnosis(incident_id);
```

---

# 6. Java 模块完整代码

## `modules/aiops-ai-client/pom.xml`

```xml id="61o2w7"
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.aegisops</groupId>
        <artifactId>aegisops</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>aiops-ai-client</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>io.aegisops</groupId>
            <artifactId>aiops-common</artifactId>
            <version>${project.version}</version>
        </dependency>

        <dependency>
            <groupId>io.aegisops</groupId>
            <artifactId>aiops-web</artifactId>
            <version>${project.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>
</project>
```

---

## DTO

### `dto/AgentIncidentContext.java`

```java id="fg9e4u"
package io.aegisops.ai.client.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AgentIncidentContext(
        String id,
        String title,
        String summary,
        String severity,
        String status,
        String source,
        String primaryAssetId,
        String aggregationKey,
        int alertCount,
        String suspectedRootCause,
        BigDecimal confidence,
        OffsetDateTime startedAt,
        OffsetDateTime detectedAt,
        OffsetDateTime lastSeenAt
) {}
```

### `dto/AgentAlertContext.java`

```java id="njg7is"
package io.aegisops.ai.client.dto;

import java.time.OffsetDateTime;

public record AgentAlertContext(
        String id,
        String source,
        String sourceEventId,
        String severity,
        String title,
        String description,
        String assetId,
        String entityType,
        String entityName,
        String fingerprint,
        String labelsJson,
        OffsetDateTime startsAt
) {}
```

### `dto/AgentRcaContext.java`

```java id="l2jrlu"
package io.aegisops.ai.client.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AgentRcaContext(
        String id,
        String suspectedRootCause,
        BigDecimal confidence,
        String summary,
        String evidenceJson,
        String modelVersion,
        OffsetDateTime createdAt
) {}
```

### `dto/AgentDiagnosisRequest.java`

```java id="n5ogc3"
package io.aegisops.ai.client.dto;

import java.util.List;

public record AgentDiagnosisRequest(
        String tenantId,
        String incidentId,
        AgentIncidentContext incident,
        List<AgentAlertContext> alerts,
        AgentRcaContext rca,
        String locale,
        String traceId
) {}
```

### `dto/AgentDiagnosisResponse.java`

```java id="0g3m70"
package io.aegisops.ai.client.dto;

import java.util.List;
import java.util.Map;

public record AgentDiagnosisResponse(
        String provider,
        String model,
        String agentName,
        String summary,
        String rootCause,
        String impact,
        List<String> nextSteps,
        List<String> runbookSuggestions,
        List<String> risks,
        Map<String, Object> raw
) {}
```

### `dto/AiDiagnoseRequest.java`

```java id="nn9meb"
package io.aegisops.ai.client.dto;

public record AiDiagnoseRequest(
        Boolean force,
        String locale
) {
    public boolean forceEnabled() {
        return Boolean.TRUE.equals(force);
    }

    public String normalizedLocale() {
        return locale == null || locale.isBlank() ? "zh-CN" : locale.trim();
    }
}
```

### `dto/AiDiagnosisResponse.java`

```java id="qeuv7x"
package io.aegisops.ai.client.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AiDiagnosisResponse(
        String id,
        String incidentId,
        String status,
        String provider,
        String model,
        String agentName,
        String summary,
        String rootCause,
        String impact,
        List<String> nextSteps,
        List<String> runbookSuggestions,
        List<String> risks,
        OffsetDateTime createdAt
) {}
```

### `dto/AiIncidentRecord.java`

```java id="e37zgw"
package io.aegisops.ai.client.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AiIncidentRecord(
        String id,
        String tenantId,
        String title,
        String summary,
        String severity,
        String status,
        String source,
        String primaryAssetId,
        String aggregationKey,
        int alertCount,
        String suspectedRootCause,
        BigDecimal confidence,
        OffsetDateTime startedAt,
        OffsetDateTime detectedAt,
        OffsetDateTime lastSeenAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
```

### `dto/AiAlertRecord.java`

```java id="vf4lgl"
package io.aegisops.ai.client.dto;

import java.time.OffsetDateTime;

public record AiAlertRecord(
        String id,
        String source,
        String sourceEventId,
        String severity,
        String title,
        String description,
        String assetId,
        String entityType,
        String entityName,
        String fingerprint,
        String labelsJson,
        OffsetDateTime startsAt
) {}
```

### `dto/AiRcaRecord.java`

```java id="h0sby3"
package io.aegisops.ai.client.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AiRcaRecord(
        String id,
        String suspectedRootCause,
        BigDecimal confidence,
        String summary,
        String evidenceJson,
        String modelVersion,
        OffsetDateTime createdAt
) {}
```

### `dto/AiDiagnosisRecord.java`

```java id="2ptf4c"
package io.aegisops.ai.client.dto;

import java.time.OffsetDateTime;

public record AiDiagnosisRecord(
        String id,
        String tenantId,
        String incidentId,
        String status,
        String provider,
        String model,
        String agentName,
        String summary,
        String rootCause,
        String impact,
        String nextStepsJson,
        String runbookSuggestionsJson,
        String risksJson,
        OffsetDateTime createdAt
) {}
```

---

## Client

### `AgentClientProperties.java`

```java id="37eisr"
package io.aegisops.ai.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.agent")
public record AgentClientProperties(
        String baseUrl,
        String internalToken,
        Integer connectTimeoutMillis,
        Integer readTimeoutMillis
) {
    public String normalizedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:9008";
        }

        String value = baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public String normalizedInternalToken() {
        return internalToken == null || internalToken.isBlank() ? "dev-internal-token" : internalToken.trim();
    }

    public int normalizedConnectTimeoutMillis() {
        return connectTimeoutMillis == null || connectTimeoutMillis <= 0 ? 3000 : connectTimeoutMillis;
    }

    public int normalizedReadTimeoutMillis() {
        return readTimeoutMillis == null || readTimeoutMillis <= 0 ? 30000 : readTimeoutMillis;
    }
}
```

### `AiAgentClient.java`

```java id="jql4li"
package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;

public interface AiAgentClient {
    AgentDiagnosisResponse diagnose(AgentDiagnosisRequest request);
}
```

### `HttpAiAgentClient.java`

```java id="ksrg1b"
package io.aegisops.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.common.exception.AppException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@EnableConfigurationProperties(AgentClientProperties.class)
public class HttpAiAgentClient implements AiAgentClient {
    private final AgentClientProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public HttpAiAgentClient(AgentClientProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, createRestTemplate(properties));
    }

    HttpAiAgentClient(AgentClientProperties properties, ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    @Override
    public AgentDiagnosisResponse diagnose(AgentDiagnosisRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-AegisOps-Internal-Token", properties.normalizedInternalToken());

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(request), headers);

            ResponseEntity<AgentDiagnosisResponse> response = restTemplate.exchange(
                    properties.normalizedBaseUrl() + "/v1/diagnose",
                    HttpMethod.POST,
                    entity,
                    AgentDiagnosisResponse.class
            );

            AgentDiagnosisResponse body = response.getBody();
            if (body == null) {
                throw new AppException("AI_AGENT_EMPTY_RESPONSE", "AI agent returned empty response");
            }

            return body;
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException("AI_AGENT_CALL_FAILED", "Failed to call AI diagnosis agent");
        }
    }

    private static RestTemplate createRestTemplate(AgentClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.normalizedConnectTimeoutMillis());
        factory.setReadTimeout(properties.normalizedReadTimeoutMillis());
        return new RestTemplate(factory);
    }
}
```

---

## Repository

### `AiRepository.java`

```java id="e6ksyl"
package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface AiRepository {
    Optional<AiIncidentRecord> findIncident(String tenantId, String incidentId);

    List<AiAlertRecord> listIncidentAlerts(String tenantId, String incidentId);

    Optional<AiRcaRecord> findLatestRca(String tenantId, String incidentId);

    Optional<AiDiagnosisRecord> findLatestDiagnosis(String tenantId, String incidentId);

    Optional<AiDiagnosisRecord> findDiagnosis(String tenantId, String diagnosisId);

    void saveDiagnosis(
            String id,
            String tenantId,
            String incidentId,
            AgentDiagnosisResponse response,
            String requestJson,
            String rawJson,
            String nextStepsJson,
            String runbookSuggestionsJson,
            String risksJson
    );

    void addIncidentTimeline(
            String id,
            String incidentId,
            OffsetDateTime eventTime,
            String title,
            String description,
            String payloadJson
    );
}
```

### `AiRows.java`

```java id="oym4il"
package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.*;

import java.sql.ResultSet;
import java.sql.SQLException;

final class AiRows {
    private AiRows() {}

    static AiIncidentRecord incident(ResultSet rs) throws SQLException {
        return new AiIncidentRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("severity"),
                rs.getString("status"),
                rs.getString("source"),
                rs.getString("primary_asset_id"),
                rs.getString("aggregation_key"),
                rs.getInt("alert_count"),
                rs.getString("suspected_root_cause"),
                rs.getBigDecimal("confidence"),
                rs.getObject("started_at", java.time.OffsetDateTime.class),
                rs.getObject("detected_at", java.time.OffsetDateTime.class),
                rs.getObject("last_seen_at", java.time.OffsetDateTime.class),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }

    static AiDiagnosisRecord diagnosis(ResultSet rs) throws SQLException {
        return new AiDiagnosisRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("incident_id"),
                rs.getString("status"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("agent_name"),
                rs.getString("summary"),
                rs.getString("root_cause"),
                rs.getString("impact"),
                rs.getString("next_steps_json"),
                rs.getString("runbook_suggestions_json"),
                rs.getString("risks_json"),
                rs.getObject("created_at", java.time.OffsetDateTime.class)
        );
    }
}
```

### `JdbcAiRepository.java`

```java id="ur1bnm"
package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcAiRepository implements AiRepository {
    private final JdbcTemplate jdbc;

    public JdbcAiRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AiIncidentRecord> findIncident(String tenantId, String incidentId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select id, tenant_id, title, summary, severity, status, source, primary_asset_id,
                           aggregation_key, alert_count, suspected_root_cause, confidence,
                           started_at, detected_at, last_seen_at, created_at, updated_at
                    from incident
                    where tenant_id = ? and id = ?
                    """, (rs, rowNum) -> AiRows.incident(rs), tenantId, incidentId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<AiAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
        return jdbc.query("""
                select a.id, a.source, a.source_event_id, a.severity, a.title, a.description,
                       a.asset_id, a.entity_type, a.entity_name, a.fingerprint,
                       a.labels::text as labels_json, a.starts_at
                from incident_event ie
                join incident i on i.id = ie.incident_id
                join alert_event a on a.id = ie.event_id
                where i.tenant_id = ?
                  and i.id = ?
                  and ie.event_type = 'alert'
                  and a.tenant_id = ?
                order by a.starts_at asc
                """, (rs, rowNum) -> new AiAlertRecord(
                rs.getString("id"),
                rs.getString("source"),
                rs.getString("source_event_id"),
                rs.getString("severity"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("asset_id"),
                rs.getString("entity_type"),
                rs.getString("entity_name"),
                rs.getString("fingerprint"),
                rs.getString("labels_json"),
                rs.getObject("starts_at", OffsetDateTime.class)
        ), tenantId, incidentId, tenantId);
    }

    @Override
    public Optional<AiRcaRecord> findLatestRca(String tenantId, String incidentId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select id, suspected_root_cause, confidence, summary,
                           evidence::text as evidence_json, model_version, created_at
                    from rca_analysis
                    where tenant_id = ? and incident_id = ?
                    order by created_at desc
                    limit 1
                    """, (rs, rowNum) -> new AiRcaRecord(
                    rs.getString("id"),
                    rs.getString("suspected_root_cause"),
                    rs.getBigDecimal("confidence"),
                    rs.getString("summary"),
                    rs.getString("evidence_json"),
                    rs.getString("model_version"),
                    rs.getObject("created_at", OffsetDateTime.class)
            ), tenantId, incidentId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<AiDiagnosisRecord> findLatestDiagnosis(String tenantId, String incidentId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select id, tenant_id, incident_id, status, provider, model, agent_name,
                           summary, root_cause, impact,
                           next_steps::text as next_steps_json,
                           runbook_suggestions::text as runbook_suggestions_json,
                           risks::text as risks_json,
                           created_at
                    from ai_diagnosis
                    where tenant_id = ? and incident_id = ?
                    order by created_at desc
                    limit 1
                    """, (rs, rowNum) -> AiRows.diagnosis(rs), tenantId, incidentId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<AiDiagnosisRecord> findDiagnosis(String tenantId, String diagnosisId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select id, tenant_id, incident_id, status, provider, model, agent_name,
                           summary, root_cause, impact,
                           next_steps::text as next_steps_json,
                           runbook_suggestions::text as runbook_suggestions_json,
                           risks::text as risks_json,
                           created_at
                    from ai_diagnosis
                    where tenant_id = ? and id = ?
                    """, (rs, rowNum) -> AiRows.diagnosis(rs), tenantId, diagnosisId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void saveDiagnosis(
            String id,
            String tenantId,
            String incidentId,
            AgentDiagnosisResponse response,
            String requestJson,
            String rawJson,
            String nextStepsJson,
            String runbookSuggestionsJson,
            String risksJson
    ) {
        jdbc.update("""
                insert into ai_diagnosis(
                  id, tenant_id, incident_id, status, provider, model, agent_name,
                  request_payload, response_raw, summary, root_cause, impact,
                  next_steps, runbook_suggestions, risks, created_at
                )
                values (?, ?, ?, 'completed', ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, now())
                """,
                id,
                tenantId,
                incidentId,
                response.provider(),
                response.model(),
                response.agentName(),
                requestJson,
                rawJson,
                response.summary(),
                response.rootCause(),
                response.impact(),
                nextStepsJson,
                runbookSuggestionsJson,
                risksJson
        );
    }

    @Override
    public void addIncidentTimeline(
            String id,
            String incidentId,
            OffsetDateTime eventTime,
            String title,
            String description,
            String payloadJson
    ) {
        jdbc.update("""
                insert into incident_timeline(id, incident_id, event_time, event_type, title, description, source, payload)
                values (?, ?, ?, 'ai_diagnosed', ?, ?, 'system', ?::jsonb)
                """, id, incidentId, eventTime, title, description, payloadJson);
    }
}
```

---

## Service + Controller

### `AiDiagnosisService.java`

```java id="993p9l"
package io.aegisops.ai.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.*;
import io.aegisops.common.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiDiagnosisService {
    private final AiRepository repository;
    private final AiAgentClient agentClient;
    private final ObjectMapper objectMapper;

    public AiDiagnosisService(AiRepository repository, AiAgentClient agentClient, ObjectMapper objectMapper) {
        this.repository = repository;
        this.agentClient = agentClient;
        this.objectMapper = objectMapper;
    }

    public AiDiagnosisResponse latest(String tenantId, String incidentId) {
        ensureIncidentExists(tenantId, incidentId);
        return repository.findLatestDiagnosis(tenantId, incidentId)
                .map(this::toResponse)
                .orElseThrow(() -> new AppException("AI_DIAGNOSIS_NOT_FOUND", "AI diagnosis not found"));
    }

    @Transactional
    public AiDiagnosisResponse diagnose(String tenantId, String incidentId, AiDiagnoseRequest request) {
        AiDiagnoseRequest normalized = request == null ? new AiDiagnoseRequest(false, "zh-CN") : request;

        AiIncidentRecord incident = repository.findIncident(tenantId, incidentId)
                .orElseThrow(() -> new AppException("INCIDENT_NOT_FOUND", "Incident not found"));

        if (!normalized.forceEnabled()) {
            var latest = repository.findLatestDiagnosis(tenantId, incidentId);
            if (latest.isPresent() && isReusableLatest(incident, latest.get())) {
                return toResponse(latest.get());
            }
        }

        List<AiAlertRecord> alerts = repository.listIncidentAlerts(tenantId, incidentId);
        AiRcaRecord rca = repository.findLatestRca(tenantId, incidentId).orElse(null);

        AgentDiagnosisRequest agentRequest = new AgentDiagnosisRequest(
                tenantId,
                incidentId,
                toAgentIncident(incident),
                alerts.stream().map(this::toAgentAlert).toList(),
                rca == null ? null : toAgentRca(rca),
                normalized.normalizedLocale(),
                UUID.randomUUID().toString()
        );

        AgentDiagnosisResponse agentResponse = sanitizeAgentResponse(agentClient.diagnose(agentRequest));

        String diagnosisId = newId("diag");
        String requestJson = writeJson(agentRequest);
        String rawJson = writeJson(agentResponse.raw() == null ? Map.of() : agentResponse.raw());
        String nextStepsJson = writeJson(agentResponse.nextSteps());
        String runbookSuggestionsJson = writeJson(agentResponse.runbookSuggestions());
        String risksJson = writeJson(agentResponse.risks());

        repository.saveDiagnosis(
                diagnosisId,
                tenantId,
                incidentId,
                agentResponse,
                requestJson,
                rawJson,
                nextStepsJson,
                runbookSuggestionsJson,
                risksJson
        );

        repository.addIncidentTimeline(
                newId("tl"),
                incidentId,
                OffsetDateTime.now(),
                "AI diagnosis completed",
                agentResponse.summary(),
                writeJson(Map.of(
                        "aiDiagnosisId", diagnosisId,
                        "provider", agentResponse.provider(),
                        "model", agentResponse.model(),
                        "agentName", agentResponse.agentName(),
                        "rootCause", agentResponse.rootCause()
                ))
        );

        return repository.findDiagnosis(tenantId, diagnosisId)
                .map(this::toResponse)
                .orElseThrow(() -> new AppException("AI_DIAGNOSIS_NOT_FOUND", "AI diagnosis not found after save"));
    }

    private AgentDiagnosisResponse sanitizeAgentResponse(AgentDiagnosisResponse response) {
        if (response == null) {
            throw new AppException("AI_AGENT_EMPTY_RESPONSE", "AI agent returned empty response");
        }

        return new AgentDiagnosisResponse(
                blankToDefault(response.provider(), "aiops-agent"),
                blankToDefault(response.model(), "langgraph-deterministic"),
                blankToDefault(response.agentName(), "aegisops_diagnosis_graph"),
                blankToDefault(response.summary(), "No summary generated."),
                blankToDefault(response.rootCause(), "No root cause generated."),
                blankToDefault(response.impact(), "Impact is unknown."),
                response.nextSteps() == null ? List.of() : response.nextSteps(),
                response.runbookSuggestions() == null ? List.of() : response.runbookSuggestions(),
                response.risks() == null ? List.of() : response.risks(),
                response.raw() == null ? Map.of() : response.raw()
        );
    }

    private boolean isReusableLatest(AiIncidentRecord incident, AiDiagnosisRecord latest) {
        if (latest.createdAt() == null) {
            return false;
        }

        OffsetDateTime baseline = incident.lastSeenAt();
        if (baseline == null) {
            baseline = incident.updatedAt();
        }
        if (baseline == null) {
            baseline = incident.createdAt();
        }

        return baseline != null && !latest.createdAt().isBefore(baseline);
    }

    private AgentIncidentContext toAgentIncident(AiIncidentRecord incident) {
        return new AgentIncidentContext(
                incident.id(),
                incident.title(),
                incident.summary(),
                incident.severity(),
                incident.status(),
                incident.source(),
                incident.primaryAssetId(),
                incident.aggregationKey(),
                incident.alertCount(),
                incident.suspectedRootCause(),
                incident.confidence(),
                incident.startedAt(),
                incident.detectedAt(),
                incident.lastSeenAt()
        );
    }

    private AgentAlertContext toAgentAlert(AiAlertRecord alert) {
        return new AgentAlertContext(
                alert.id(),
                alert.source(),
                alert.sourceEventId(),
                alert.severity(),
                alert.title(),
                alert.description(),
                alert.assetId(),
                alert.entityType(),
                alert.entityName(),
                alert.fingerprint(),
                alert.labelsJson(),
                alert.startsAt()
        );
    }

    private AgentRcaContext toAgentRca(AiRcaRecord rca) {
        return new AgentRcaContext(
                rca.id(),
                rca.suspectedRootCause(),
                rca.confidence(),
                rca.summary(),
                rca.evidenceJson(),
                rca.modelVersion(),
                rca.createdAt()
        );
    }

    private AiDiagnosisResponse toResponse(AiDiagnosisRecord record) {
        return new AiDiagnosisResponse(
                record.id(),
                record.incidentId(),
                record.status(),
                record.provider(),
                record.model(),
                record.agentName(),
                record.summary(),
                record.rootCause(),
                record.impact(),
                readStringList(record.nextStepsJson()),
                readStringList(record.runbookSuggestionsJson()),
                readStringList(record.risksJson()),
                record.createdAt()
        );
    }

    private void ensureIncidentExists(String tenantId, String incidentId) {
        if (repository.findIncident(tenantId, incidentId).isEmpty()) {
            throw new AppException("INCIDENT_NOT_FOUND", "Incident not found");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new AppException("AI_DIAGNOSIS_INVALID", "Failed to serialize AI diagnosis payload");
        }
    }

    private List<String> readStringList(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            throw new AppException("AI_DIAGNOSIS_INVALID", "AI diagnosis JSON is invalid");
        }
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
```

### `AiDiagnosisController.java`

```java id="55rtqs"
package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.AiDiagnoseRequest;
import io.aegisops.ai.client.dto.AiDiagnosisResponse;
import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/incidents/{incidentId}/ai")
public class AiDiagnosisController {
    private final AiDiagnosisService diagnosisService;

    public AiDiagnosisController(AiDiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    @GetMapping("/latest")
    public ApiResponse<AiDiagnosisResponse> latest(@PathVariable String incidentId) {
        return ApiResponse.ok(diagnosisService.latest(TenantContext.requireTenantId(), incidentId));
    }

    @PostMapping("/diagnose")
    public ApiResponse<AiDiagnosisResponse> diagnose(
            @PathVariable String incidentId,
            @RequestBody(required = false) AiDiagnoseRequest request
    ) {
        return ApiResponse.ok(diagnosisService.diagnose(TenantContext.requireTenantId(), incidentId, request));
    }
}
```

---

# 7. Java 单元测试

## `AiDiagnosisServiceTest.java`

```java id="m5kmj2"
package io.aegisops.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.aegisops.ai.client.dto.*;
import io.aegisops.common.exception.AppException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AiDiagnosisServiceTest {
    @Test
    void returnsFreshLatestWhenForceDisabled() {
        FakeAiRepository repository = new FakeAiRepository();
        repository.incident = incident();
        repository.latest = diagnosis(repository.incident.lastSeenAt().plusSeconds(1));

        AiDiagnosisService service = new AiDiagnosisService(repository, new FakeAgentClient(), objectMapper());

        AiDiagnosisResponse response = service.diagnose("tenant_1", "inc_1", new AiDiagnoseRequest(false, "zh-CN"));

        assertEquals("diag_cached", response.id());
        assertEquals(0, repository.savedCount);
        assertEquals(0, repository.timelineCount);
    }

    @Test
    void recomputesWhenLatestIsStale() {
        FakeAiRepository repository = new FakeAiRepository();
        repository.incident = incident();
        repository.alerts = List.of(alert());
        repository.rca = rca();
        repository.latest = diagnosis(repository.incident.lastSeenAt().minusMinutes(5));

        AiDiagnosisService service = new AiDiagnosisService(repository, new FakeAgentClient(), objectMapper());

        AiDiagnosisResponse response = service.diagnose("tenant_1", "inc_1", new AiDiagnoseRequest(false, "zh-CN"));

        assertNotEquals("diag_cached", response.id());
        assertEquals(1, repository.savedCount);
        assertEquals(1, repository.timelineCount);
        assertEquals("aegisops_diagnosis_graph", response.agentName());
    }

    @Test
    void forceDiagnoseAlwaysCallsAgent() {
        FakeAiRepository repository = new FakeAiRepository();
        repository.incident = incident();
        repository.alerts = List.of(alert());
        repository.latest = diagnosis(repository.incident.lastSeenAt().plusSeconds(1));

        AiDiagnosisService service = new AiDiagnosisService(repository, new FakeAgentClient(), objectMapper());

        AiDiagnosisResponse response = service.diagnose("tenant_1", "inc_1", new AiDiagnoseRequest(true, "zh-CN"));

        assertNotEquals("diag_cached", response.id());
        assertEquals(1, repository.savedCount);
        assertEquals(1, repository.timelineCount);
        assertFalse(response.nextSteps().isEmpty());
    }

    @Test
    void latestThrowsWhenMissing() {
        FakeAiRepository repository = new FakeAiRepository();
        repository.incident = incident();

        AiDiagnosisService service = new AiDiagnosisService(repository, new FakeAgentClient(), objectMapper());

        AppException ex = assertThrows(AppException.class, () -> service.latest("tenant_1", "inc_1"));

        assertEquals("AI_DIAGNOSIS_NOT_FOUND", ex.errorCode());
    }

    @Test
    void diagnoseThrowsWhenIncidentMissing() {
        AiDiagnosisService service = new AiDiagnosisService(new FakeAiRepository(), new FakeAgentClient(), objectMapper());

        AppException ex = assertThrows(AppException.class, () ->
                service.diagnose("tenant_1", "missing", new AiDiagnoseRequest(true, "zh-CN"))
        );

        assertEquals("INCIDENT_NOT_FOUND", ex.errorCode());
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private AiIncidentRecord incident() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-14T10:00:00+09:00");
        return new AiIncidentRecord(
                "inc_1",
                "tenant_1",
                "CPU high",
                "summary",
                "critical",
                "open",
                "system",
                "asset_1",
                "zabbix:fp_cpu",
                2,
                "CPU saturation",
                new BigDecimal("0.8000"),
                now.minusMinutes(5),
                now,
                now,
                now,
                now
        );
    }

    private AiAlertRecord alert() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-14T10:00:00+09:00");
        return new AiAlertRecord(
                "alert_1",
                "zabbix",
                "event_1",
                "critical",
                "CPU high",
                "CPU is high",
                "asset_1",
                "host",
                "host-1",
                "fp_cpu",
                "{}",
                now
        );
    }

    private AiRcaRecord rca() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-14T10:00:00+09:00");
        return new AiRcaRecord(
                "rca_1",
                "CPU saturation",
                new BigDecimal("0.8000"),
                "RCA summary",
                "[]",
                "rules-v1",
                now
        );
    }

    private AiDiagnosisRecord diagnosis(OffsetDateTime createdAt) {
        return new AiDiagnosisRecord(
                "diag_cached",
                "tenant_1",
                "inc_1",
                "completed",
                "aiops-agent",
                "langgraph-deterministic",
                "aegisops_diagnosis_graph",
                "cached summary",
                "cached root",
                "cached impact",
                "[\"step\"]",
                "[]",
                "[]",
                createdAt
        );
    }

    private static final class FakeAgentClient implements AiAgentClient {
        @Override
        public AgentDiagnosisResponse diagnose(AgentDiagnosisRequest request) {
            return new AgentDiagnosisResponse(
                    "aiops-agent",
                    "langgraph-deterministic",
                    "aegisops_diagnosis_graph",
                    "AI summary",
                    "CPU saturation",
                    "Service latency may increase.",
                    List.of("Check CPU usage", "Check top process"),
                    List.of("Host resource saturation runbook"),
                    List.of("Do not restart blindly"),
                    Map.of("runtime", "test")
            );
        }
    }

    private static final class FakeAiRepository implements AiRepository {
        AiIncidentRecord incident;
        List<AiAlertRecord> alerts = List.of();
        AiRcaRecord rca;
        AiDiagnosisRecord latest;
        AiDiagnosisRecord saved;
        int savedCount;
        int timelineCount;

        @Override
        public Optional<AiIncidentRecord> findIncident(String tenantId, String incidentId) {
            if (incident == null) {
                return Optional.empty();
            }
            if (!incident.tenantId().equals(tenantId) || !incident.id().equals(incidentId)) {
                return Optional.empty();
            }
            return Optional.of(incident);
        }

        @Override
        public List<AiAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
            return alerts;
        }

        @Override
        public Optional<AiRcaRecord> findLatestRca(String tenantId, String incidentId) {
            return Optional.ofNullable(rca);
        }

        @Override
        public Optional<AiDiagnosisRecord> findLatestDiagnosis(String tenantId, String incidentId) {
            return Optional.ofNullable(latest);
        }

        @Override
        public Optional<AiDiagnosisRecord> findDiagnosis(String tenantId, String diagnosisId) {
            if (saved == null || !saved.tenantId().equals(tenantId) || !saved.id().equals(diagnosisId)) {
                return Optional.empty();
            }
            return Optional.of(saved);
        }

        @Override
        public void saveDiagnosis(
                String id,
                String tenantId,
                String incidentId,
                AgentDiagnosisResponse response,
                String requestJson,
                String rawJson,
                String nextStepsJson,
                String runbookSuggestionsJson,
                String risksJson
        ) {
            savedCount++;
            saved = new AiDiagnosisRecord(
                    id,
                    tenantId,
                    incidentId,
                    "completed",
                    response.provider(),
                    response.model(),
                    response.agentName(),
                    response.summary(),
                    response.rootCause(),
                    response.impact(),
                    nextStepsJson,
                    runbookSuggestionsJson,
                    risksJson,
                    OffsetDateTime.parse("2026-06-14T10:00:00+09:00")
            );
        }

        @Override
        public void addIncidentTimeline(String id, String incidentId, OffsetDateTime eventTime, String title, String description, String payloadJson) {
            timelineCount++;
        }
    }
}
```

## `HttpAiAgentClientTest.java`

```java id="ty2dzi"
package io.aegisops.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpAiAgentClientTest {
    @Test
    void postsDiagnosisRequestToAgent() {
        ObjectMapper objectMapper = new ObjectMapper();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        AgentClientProperties properties = new AgentClientProperties(
                "http://agent:9008",
                "test-token",
                1000,
                1000
        );

        HttpAiAgentClient client = new HttpAiAgentClient(properties, objectMapper, restTemplate);

        server.expect(requestTo("http://agent:9008/v1/diagnose"))
                .andExpect(header("X-AegisOps-Internal-Token", "test-token"))
                .andRespond(withSuccess("""
                        {
                          "provider": "aiops-agent",
                          "model": "langgraph-deterministic",
                          "agentName": "aegisops_diagnosis_graph",
                          "summary": "summary",
                          "rootCause": "root",
                          "impact": "impact",
                          "nextSteps": ["step"],
                          "runbookSuggestions": ["runbook"],
                          "risks": ["risk"],
                          "raw": {"ok": true}
                        }
                        """, MediaType.APPLICATION_JSON));

        AgentDiagnosisResponse response = client.diagnose(new AgentDiagnosisRequest(
                "tenant_1",
                "inc_1",
                null,
                List.of(),
                null,
                "zh-CN",
                "trace_1"
        ));

        assertEquals("aiops-agent", response.provider());
        assertEquals("aegisops_diagnosis_graph", response.agentName());
        assertEquals("root", response.rootCause());

        server.verify();
    }
}
```

---

# 8. Python LangGraph Agent 完整代码

## `apps/aiops-agent/pyproject.toml`

```toml id="id2l3r"
[project]
name = "aiops-agent"
version = "0.1.0"
description = "AegisOps Python LangGraph diagnosis agent runtime"
requires-python = ">=3.11"
dependencies = [
  "fastapi>=0.115.0",
  "uvicorn[standard]>=0.30.0",
  "pydantic>=2.8.0",
  "pydantic-settings>=2.4.0",
  "langgraph>=0.2.0"
]

[project.optional-dependencies]
test = [
  "pytest>=8.2.0",
  "httpx>=0.27.0"
]

[tool.pytest.ini_options]
pythonpath = ["src"]
testpaths = ["tests"]
```

---

## `apps/aiops-agent/Dockerfile`

```dockerfile id="pqz69c"
FROM python:3.12-slim

WORKDIR /app

COPY pyproject.toml README.md ./
COPY src ./src

RUN pip install --no-cache-dir .

EXPOSE 9008

CMD ["uvicorn", "aiops_agent.main:app", "--host", "0.0.0.0", "--port", "9008"]
```

---

## `apps/aiops-agent/README.md`

````md id="09zf2k"
# aiops-agent

AegisOps Python LangGraph diagnosis agent runtime.

## Local dev

```bash
cd apps/aiops-agent
python -m venv .venv
. .venv/Scripts/activate
pip install -e ".[test]"
uvicorn aiops_agent.main:app --reload --port 9008
```
````

## Test

```bash
pytest
```

## Env

```bash
AIOPS_AGENT_INTERNAL_TOKEN=dev-internal-token
AIOPS_AGENT_MODEL=langgraph-deterministic
AIOPS_AGENT_PROVIDER=aiops-agent
AIOPS_AGENT_NAME=aegisops_diagnosis_graph
```

````

---

## `apps/aiops-agent/src/aiops_agent/__init__.py`

```python id="05ce3g"
__version__ = "0.1.0"
````

---

## `settings.py`

```python id="i59w5j"
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="AIOPS_AGENT_", env_file=".env", extra="ignore")

    internal_token: str = "dev-internal-token"
    provider: str = "aiops-agent"
    model: str = "langgraph-deterministic"
    agent_name: str = "aegisops_diagnosis_graph"
    default_locale: str = "zh-CN"


settings = Settings()
```

---

## `schemas.py`

```python id="snf1oi"
from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, Field


class IncidentContext(BaseModel):
    id: str
    title: str | None = None
    summary: str | None = None
    severity: str | None = None
    status: str | None = None
    source: str | None = None
    primaryAssetId: str | None = None
    aggregationKey: str | None = None
    alertCount: int = 0
    suspectedRootCause: str | None = None
    confidence: float | None = None
    startedAt: datetime | None = None
    detectedAt: datetime | None = None
    lastSeenAt: datetime | None = None


class AlertContext(BaseModel):
    id: str
    source: str | None = None
    sourceEventId: str | None = None
    severity: str | None = None
    title: str | None = None
    description: str | None = None
    assetId: str | None = None
    entityType: str | None = None
    entityName: str | None = None
    fingerprint: str | None = None
    labelsJson: str | None = None
    startsAt: datetime | None = None


class RcaContext(BaseModel):
    id: str
    suspectedRootCause: str | None = None
    confidence: float | None = None
    summary: str | None = None
    evidenceJson: str | None = None
    modelVersion: str | None = None
    createdAt: datetime | None = None


class DiagnoseRequest(BaseModel):
    tenantId: str
    incidentId: str
    incident: IncidentContext
    alerts: list[AlertContext] = Field(default_factory=list)
    rca: RcaContext | None = None
    locale: str = "zh-CN"
    traceId: str | None = None


class DiagnoseResponse(BaseModel):
    provider: str = "aiops-agent"
    model: str = "langgraph-deterministic"
    agentName: str = "aegisops_diagnosis_graph"
    summary: str
    rootCause: str
    impact: str
    nextSteps: list[str] = Field(default_factory=list)
    runbookSuggestions: list[str] = Field(default_factory=list)
    risks: list[str] = Field(default_factory=list)
    raw: dict[str, Any] = Field(default_factory=dict)


class HealthResponse(BaseModel):
    ok: bool
    provider: str
    model: str
    agentName: str
```

---

## `tools.py`

```python id="tw5hpa"
from __future__ import annotations

from collections import Counter
from typing import Any

from aiops_agent.schemas import AlertContext, DiagnoseRequest


SEVERITY_WEIGHT = {
    "info": 10,
    "low": 20,
    "warning": 30,
    "critical": 40,
    "disaster": 50,
}


def severity_weight(severity: str | None) -> int:
    if not severity:
        return 10
    return SEVERITY_WEIGHT.get(severity.lower(), 10)


def summarize_incident_context(request: DiagnoseRequest) -> dict[str, Any]:
    incident = request.incident
    return {
        "incidentId": incident.id,
        "title": incident.title or "",
        "severity": incident.severity or "info",
        "status": incident.status or "unknown",
        "alertCount": incident.alertCount,
        "primaryAssetId": incident.primaryAssetId or "",
        "aggregationKey": incident.aggregationKey or "",
        "suspectedRootCause": incident.suspectedRootCause or "",
        "confidence": incident.confidence or 0,
    }


def inspect_alerts(alerts: list[AlertContext]) -> dict[str, Any]:
    if not alerts:
        return {
            "count": 0,
            "topSeverity": "info",
            "topAlertTitle": "",
            "dominantFingerprint": "",
            "dominantFingerprintCount": 0,
            "dominantAssetId": "",
            "dominantAssetCount": 0,
        }

    top = max(alerts, key=lambda item: severity_weight(item.severity))
    fingerprint_counts = Counter(a.fingerprint for a in alerts if a.fingerprint)
    asset_counts = Counter(a.assetId for a in alerts if a.assetId)

    return {
        "count": len(alerts),
        "topSeverity": top.severity or "info",
        "topAlertTitle": top.title or "",
        "dominantFingerprint": fingerprint_counts.most_common(1)[0][0] if fingerprint_counts else "",
        "dominantFingerprintCount": fingerprint_counts.most_common(1)[0][1] if fingerprint_counts else 0,
        "dominantAssetId": asset_counts.most_common(1)[0][0] if asset_counts else "",
        "dominantAssetCount": asset_counts.most_common(1)[0][1] if asset_counts else 0,
    }


def inspect_rca_evidence(request: DiagnoseRequest) -> dict[str, Any]:
    if request.rca is None:
        return {
            "hasRca": False,
            "rootCause": "",
            "confidence": 0,
            "summary": "",
            "evidenceJson": "[]",
        }

    return {
        "hasRca": True,
        "rootCause": request.rca.suspectedRootCause or "",
        "confidence": request.rca.confidence or 0,
        "summary": request.rca.summary or "",
        "evidenceJson": request.rca.evidenceJson or "[]",
    }


def query_metrics_stub(request: DiagnoseRequest) -> dict[str, Any]:
    return {
        "available": False,
        "reason": "Phase4 does not query real metrics yet.",
        "suggestedMetrics": [
            "cpu_usage",
            "memory_usage",
            "disk_io",
            "network_error_rate",
            "service_latency",
        ],
    }


def query_logs_stub(request: DiagnoseRequest) -> dict[str, Any]:
    return {
        "available": False,
        "reason": "Phase4 does not query real logs yet.",
        "suggestedQueries": [
            "error logs around incident start time",
            "deployment logs around incident start time",
            "restart/crash logs for primary asset",
        ],
    }


def search_runbooks_stub(request: DiagnoseRequest, alert_analysis: dict[str, Any], rca_analysis: dict[str, Any]) -> list[str]:
    root_cause = str(rca_analysis.get("rootCause") or "").lower()
    top_title = str(alert_analysis.get("topAlertTitle") or "").lower()

    suggestions: list[str] = []

    if "cpu" in root_cause or "cpu" in top_title:
        suggestions.append("Host resource saturation investigation")

    if "memory" in root_cause or "memory" in top_title:
        suggestions.append("Memory pressure troubleshooting")

    if "disk" in root_cause or "disk" in top_title:
        suggestions.append("Disk usage and IO troubleshooting")

    if not suggestions:
        suggestions.append("Generic incident triage checklist")

    suggestions.append("Recent change and deployment verification")

    return suggestions


def safety_guard() -> list[str]:
    return [
        "Do not execute remediation automatically in Phase4.",
        "Verify AI diagnosis against metrics, logs, and RCA evidence.",
        "High-risk actions require human approval in Phase5.",
    ]
```

---

## `graph.py`

```python id="ec45xl"
from __future__ import annotations

from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph

from aiops_agent.schemas import DiagnoseRequest, DiagnoseResponse
from aiops_agent.settings import Settings
from aiops_agent.tools import (
    inspect_alerts,
    inspect_rca_evidence,
    query_logs_stub,
    query_metrics_stub,
    safety_guard,
    search_runbooks_stub,
    summarize_incident_context,
)


class DiagnosisState(TypedDict, total=False):
    request: DiagnoseRequest
    incident_summary: dict[str, Any]
    alert_analysis: dict[str, Any]
    rca_analysis: dict[str, Any]
    metrics: dict[str, Any]
    logs: dict[str, Any]
    runbook_suggestions: list[str]
    risks: list[str]
    diagnosis: DiagnoseResponse


def load_context(state: DiagnosisState) -> DiagnosisState:
    request = state["request"]
    return {
        "incident_summary": summarize_incident_context(request),
    }


def analyze_alerts(state: DiagnosisState) -> DiagnosisState:
    request = state["request"]
    return {
        "alert_analysis": inspect_alerts(request.alerts),
    }


def analyze_rca(state: DiagnosisState) -> DiagnosisState:
    request = state["request"]
    return {
        "rca_analysis": inspect_rca_evidence(request),
    }


def query_metrics(state: DiagnosisState) -> DiagnosisState:
    return {
        "metrics": query_metrics_stub(state["request"]),
    }


def query_logs(state: DiagnosisState) -> DiagnosisState:
    return {
        "logs": query_logs_stub(state["request"]),
    }


def search_runbooks(state: DiagnosisState) -> DiagnosisState:
    return {
        "runbook_suggestions": search_runbooks_stub(
            state["request"],
            state.get("alert_analysis", {}),
            state.get("rca_analysis", {}),
        )
    }


def safety_check(state: DiagnosisState) -> DiagnosisState:
    return {
        "risks": safety_guard(),
    }


def generate_diagnosis(settings: Settings):
    def _node(state: DiagnosisState) -> DiagnosisState:
        request = state["request"]
        incident = state.get("incident_summary", {})
        alerts = state.get("alert_analysis", {})
        rca = state.get("rca_analysis", {})
        runbooks = state.get("runbook_suggestions", [])
        risks = state.get("risks", [])

        root_cause = (
            rca.get("rootCause")
            or incident.get("suspectedRootCause")
            or "No strong root cause has been confirmed. Start from the dominant alert and primary asset."
        )

        dominant_asset = (
            alerts.get("dominantAssetId")
            or incident.get("primaryAssetId")
            or "unknown asset"
        )

        dominant_fingerprint = (
            alerts.get("dominantFingerprint")
            or incident.get("aggregationKey")
            or "unknown fingerprint"
        )

        summary = (
            f"Incident {request.incidentId} is {incident.get('status', 'unknown')} "
            f"with severity {incident.get('severity', alerts.get('topSeverity', 'info'))}. "
            f"{alerts.get('count', 0)} linked alert(s) were analyzed."
        )

        impact = (
            f"The primary impact may be concentrated on {dominant_asset}. "
            "Downstream services may be affected if this asset is part of a dependency path."
        )

        next_steps = [
            f"Confirm whether the dominant fingerprint `{dominant_fingerprint}` is still firing.",
            f"Check the primary asset `{dominant_asset}` around the incident start time.",
            "Compare metrics before and after incident detection.",
            "Review recent deployments, restarts, configuration changes, and dependency health.",
            "Validate RCA evidence before taking remediation action.",
        ]

        raw = {
            "graph": "aegisops_diagnosis_graph",
            "incident": incident,
            "alerts": alerts,
            "rca": rca,
            "metrics": state.get("metrics", {}),
            "logs": state.get("logs", {}),
        }

        return {
            "diagnosis": DiagnoseResponse(
                provider=settings.provider,
                model=settings.model,
                agentName=settings.agent_name,
                summary=summary,
                rootCause=str(root_cause),
                impact=impact,
                nextSteps=next_steps,
                runbookSuggestions=runbooks,
                risks=risks,
                raw=raw,
            )
        }

    return _node


def build_diagnosis_graph(settings: Settings):
    graph = StateGraph(DiagnosisState)

    graph.add_node("load_context", load_context)
    graph.add_node("analyze_alerts", analyze_alerts)
    graph.add_node("analyze_rca", analyze_rca)
    graph.add_node("query_metrics", query_metrics)
    graph.add_node("query_logs", query_logs)
    graph.add_node("search_runbooks", search_runbooks)
    graph.add_node("safety_check", safety_check)
    graph.add_node("generate_diagnosis", generate_diagnosis(settings))

    graph.add_edge(START, "load_context")
    graph.add_edge("load_context", "analyze_alerts")
    graph.add_edge("analyze_alerts", "analyze_rca")
    graph.add_edge("analyze_rca", "query_metrics")
    graph.add_edge("query_metrics", "query_logs")
    graph.add_edge("query_logs", "search_runbooks")
    graph.add_edge("search_runbooks", "safety_check")
    graph.add_edge("safety_check", "generate_diagnosis")
    graph.add_edge("generate_diagnosis", END)

    return graph.compile()


def run_diagnosis_graph(request: DiagnoseRequest, settings: Settings) -> DiagnoseResponse:
    compiled = build_diagnosis_graph(settings)
    result = compiled.invoke({"request": request})
    diagnosis = result.get("diagnosis")
    if not isinstance(diagnosis, DiagnoseResponse):
        raise RuntimeError("diagnosis graph did not return DiagnoseResponse")
    return diagnosis
```

---

## `service.py`

```python id="a6f6vd"
from __future__ import annotations

from aiops_agent.graph import run_diagnosis_graph
from aiops_agent.schemas import DiagnoseRequest, DiagnoseResponse
from aiops_agent.settings import Settings


class DiagnosisService:
    def __init__(self, settings: Settings):
        self.settings = settings

    def diagnose(self, request: DiagnoseRequest) -> DiagnoseResponse:
        return run_diagnosis_graph(request, self.settings)
```

---

## `main.py`

```python id="ohk4xd"
from __future__ import annotations

from fastapi import Depends, FastAPI, Header, HTTPException, status

from aiops_agent.schemas import DiagnoseRequest, DiagnoseResponse, HealthResponse
from aiops_agent.service import DiagnosisService
from aiops_agent.settings import settings

app = FastAPI(title="AegisOps LangGraph Agent Runtime", version="0.1.0")


def verify_internal_token(x_aegisops_internal_token: str | None = Header(default=None)) -> None:
    if x_aegisops_internal_token != settings.internal_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="invalid internal token",
        )


def diagnosis_service() -> DiagnosisService:
    return DiagnosisService(settings)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        ok=True,
        provider=settings.provider,
        model=settings.model,
        agentName=settings.agent_name,
    )


@app.post(
    "/v1/diagnose",
    response_model=DiagnoseResponse,
    dependencies=[Depends(verify_internal_token)],
)
def diagnose(
    request: DiagnoseRequest,
    service: DiagnosisService = Depends(diagnosis_service),
) -> DiagnoseResponse:
    return service.diagnose(request)
```

---

# 9. Python 单元测试

## `tests/test_schemas.py`

```python id="by5s48"
from aiops_agent.schemas import DiagnoseRequest, IncidentContext


def test_diagnose_request_defaults():
    request = DiagnoseRequest(
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1"),
    )

    assert request.locale == "zh-CN"
    assert request.alerts == []
    assert request.rca is None
```

---

## `tests/test_tools.py`

```python id="juu5a4"
from aiops_agent.schemas import AlertContext, DiagnoseRequest, IncidentContext, RcaContext
from aiops_agent.tools import inspect_alerts, inspect_rca_evidence, search_runbooks_stub


def test_inspect_alerts_returns_dominant_fingerprint_and_asset():
    alerts = [
        AlertContext(id="a1", severity="critical", title="CPU high", assetId="asset_1", fingerprint="fp_cpu"),
        AlertContext(id="a2", severity="warning", title="CPU high", assetId="asset_1", fingerprint="fp_cpu"),
        AlertContext(id="a3", severity="info", title="Other", assetId="asset_2", fingerprint="fp_other"),
    ]

    result = inspect_alerts(alerts)

    assert result["count"] == 3
    assert result["topSeverity"] == "critical"
    assert result["dominantFingerprint"] == "fp_cpu"
    assert result["dominantAssetId"] == "asset_1"


def test_inspect_rca_evidence_handles_missing_rca():
    request = DiagnoseRequest(
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1"),
    )

    result = inspect_rca_evidence(request)

    assert result["hasRca"] is False
    assert result["confidence"] == 0


def test_runbook_stub_recommends_cpu_runbook():
    request = DiagnoseRequest(
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1"),
        alerts=[AlertContext(id="a1", title="CPU high", severity="critical")],
        rca=RcaContext(id="rca_1", suspectedRootCause="CPU saturation"),
    )

    alert_analysis = inspect_alerts(request.alerts)
    rca_analysis = inspect_rca_evidence(request)
    result = search_runbooks_stub(request, alert_analysis, rca_analysis)

    assert "Host resource saturation investigation" in result
```

---

## `tests/test_graph.py`

```python id="7fdrk6"
from aiops_agent.graph import run_diagnosis_graph
from aiops_agent.schemas import AlertContext, DiagnoseRequest, IncidentContext, RcaContext
from aiops_agent.settings import Settings


def test_diagnosis_graph_returns_structured_result():
    settings = Settings(
        internal_token="token",
        provider="aiops-agent",
        model="langgraph-deterministic",
        agent_name="aegisops_diagnosis_graph",
    )

    request = DiagnoseRequest(
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(
            id="inc_1",
            title="CPU high",
            severity="critical",
            status="open",
            primaryAssetId="asset_1",
            alertCount=2,
        ),
        alerts=[
            AlertContext(id="a1", title="CPU high", severity="critical", assetId="asset_1", fingerprint="fp_cpu"),
            AlertContext(id="a2", title="CPU high", severity="warning", assetId="asset_1", fingerprint="fp_cpu"),
        ],
        rca=RcaContext(
            id="rca_1",
            suspectedRootCause="CPU saturation",
            confidence=0.8,
            summary="RCA summary",
        ),
    )

    response = run_diagnosis_graph(request, settings)

    assert response.provider == "aiops-agent"
    assert response.model == "langgraph-deterministic"
    assert response.agentName == "aegisops_diagnosis_graph"
    assert "CPU saturation" in response.rootCause
    assert response.nextSteps
    assert response.runbookSuggestions
    assert response.risks
    assert response.raw["graph"] == "aegisops_diagnosis_graph"
```

---

## `tests/test_api.py`

```python id="whzpb8"
from fastapi.testclient import TestClient

from aiops_agent.main import app
from aiops_agent.settings import settings


client = TestClient(app)


def test_health():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["ok"] is True
    assert response.json()["agentName"] == settings.agent_name


def test_diagnose_rejects_missing_token():
    response = client.post(
        "/v1/diagnose",
        json={
            "tenantId": "tenant_1",
            "incidentId": "inc_1",
            "incident": {"id": "inc_1"},
            "alerts": [],
        },
    )

    assert response.status_code == 401


def test_diagnose_returns_structured_response():
    response = client.post(
        "/v1/diagnose",
        headers={"X-AegisOps-Internal-Token": settings.internal_token},
        json={
            "tenantId": "tenant_1",
            "incidentId": "inc_1",
            "incident": {
                "id": "inc_1",
                "title": "CPU high",
                "severity": "critical",
                "status": "open",
                "primaryAssetId": "asset_1",
                "alertCount": 1,
            },
            "alerts": [
                {
                    "id": "a1",
                    "title": "CPU high",
                    "severity": "critical",
                    "assetId": "asset_1",
                    "fingerprint": "fp_cpu",
                }
            ],
            "rca": {
                "id": "rca_1",
                "suspectedRootCause": "CPU saturation",
                "confidence": 0.8,
                "summary": "RCA summary",
            },
            "locale": "zh-CN",
            "traceId": "trace_1",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["agentName"] == "aegisops_diagnosis_graph"
    assert body["summary"]
    assert body["rootCause"]
    assert body["nextSteps"]
    assert body["risks"]
```

---

# 10. Docker Compose

在 `infra/docker-compose.yml` 的 `services:` 下新增：

```yaml id="zx9cd0"
aiops-agent:
  build:
    context: ../apps/aiops-agent
    dockerfile: Dockerfile
  container_name: aegisops-agent
  environment:
    AIOPS_AGENT_INTERNAL_TOKEN: dev-internal-token
    AIOPS_AGENT_PROVIDER: aiops-agent
    AIOPS_AGENT_MODEL: langgraph-deterministic
    AIOPS_AGENT_NAME: aegisops_diagnosis_graph
  ports:
    - '9008:9008'
  restart: unless-stopped
```

Java 配置增加：

```properties id="uouujt"
aiops.agent.base-url=http://localhost:9008
aiops.agent.internal-token=dev-internal-token
aiops.agent.connect-timeout-millis=3000
aiops.agent.read-timeout-millis=30000
```

如果 `aiops-server` 也在 Docker 内运行，则改为：

```properties id="rrtdev"
aiops.agent.base-url=http://aiops-agent:9008
```

---

# 11. 前端接入

## `web/console/src/api/client.ts` 增加

```ts id="ek7cl4"
export type AiDiagnosisResponse = {
  id: string
  incidentId: string
  status: string
  provider: string
  model: string
  agentName: string
  summary: string
  rootCause: string
  impact: string
  nextSteps: string[]
  runbookSuggestions: string[]
  risks: string[]
  createdAt: string
}

export function diagnoseIncidentAi(id: string, force = true) {
  return apiRequest<AiDiagnosisResponse>(`/api/incidents/${id}/ai/diagnose`, {
    method: 'POST',
    body: JSON.stringify({ force, locale: 'zh-CN' }),
  })
}

export function getLatestIncidentAiDiagnosis(id: string) {
  return apiRequest<AiDiagnosisResponse>(`/api/incidents/${id}/ai/latest`)
}
```

## `DashboardPage.tsx` 增加 import

```tsx id="m0krai"
import {
  aggregateIncidents,
  analyzeIncidentRca,
  createZabbixDataSource,
  diagnoseIncidentAi,
  getIncident,
  resolveIncident,
  syncDataSource,
  testDataSource,
} from '../api/client'
```

## 增加 state

```tsx id="5o0v8h"
const [aiDiagnosis, setAiDiagnosis] = useState<Awaited<
  ReturnType<typeof diagnoseIncidentAi>
> | null>(null)
```

## 选择 Incident 时清理

```tsx id="2f8nnw"
function selectIncident(id: string) {
  setSelectedIncidentId(id)
  setRcaResult(null)
  setAiDiagnosis(null)
}
```

把原来的：

```tsx id="x59v0g"
onClick={() => setSelectedIncidentId(incident.id)}
```

改为：

```tsx id="5cda0l"
onClick={() => selectIncident(incident.id)}
```

## 增加 mutation

```tsx id="m4644o"
const aiDiagnosisMutation = useMutation({
  mutationFn: (incidentId: string) => diagnoseIncidentAi(incidentId, true),
  onSuccess: async (result) => {
    setAiDiagnosis(result)
    setMessage(`AI diagnosis completed: ${result.summary}`)
    await queryClient.invalidateQueries({
      queryKey: ['incident', selectedIncidentId],
    })
  },
  onError: (error) => setMessage(String(error)),
})
```

## 增加按钮

放在 `Analyze RCA` 按钮旁边：

```tsx id="k9h90g"
<button
  className="mt-3 ml-2 rounded-lg bg-purple-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-60"
  disabled={aiDiagnosisMutation.isPending}
  onClick={() => aiDiagnosisMutation.mutate(incidentDetailQuery.data!.incident.id)}
>
  {aiDiagnosisMutation.isPending ? 'Diagnosing...' : 'AI Diagnose'}
</button>
```

## 增加展示区

放在 RCA Result 后面：

```tsx id="cjggiw"
{
  aiDiagnosis && (
    <div>
      <h3 className="text-sm font-semibold">AI Diagnosis Agent</h3>
      <div className="mt-2 rounded-lg border border-purple-200 bg-purple-50 p-3 text-sm">
        <div className="font-medium">{aiDiagnosis.summary}</div>
        <div className="mt-2 text-slate-700">
          <span className="font-medium">Root cause: </span>
          {aiDiagnosis.rootCause}
        </div>
        <div className="mt-2 text-slate-700">
          <span className="font-medium">Impact: </span>
          {aiDiagnosis.impact}
        </div>
        <div className="mt-2 text-slate-500">
          {aiDiagnosis.provider} · {aiDiagnosis.model} · {aiDiagnosis.agentName} ·{' '}
          {aiDiagnosis.createdAt}
        </div>
      </div>

      <div className="mt-3 space-y-2">
        <h4 className="text-xs font-semibold text-slate-500">Next Steps</h4>
        {aiDiagnosis.nextSteps.map((step, index) => (
          <div className="rounded-lg border border-slate-200 p-3 text-sm" key={`step-${index}`}>
            {index + 1}. {step}
          </div>
        ))}
      </div>

      {aiDiagnosis.runbookSuggestions.length > 0 && (
        <div className="mt-3 space-y-2">
          <h4 className="text-xs font-semibold text-slate-500">Runbook Suggestions</h4>
          {aiDiagnosis.runbookSuggestions.map((item, index) => (
            <div
              className="rounded-lg border border-slate-200 p-3 text-sm"
              key={`runbook-${index}`}
            >
              {item}
            </div>
          ))}
        </div>
      )}

      {aiDiagnosis.risks.length > 0 && (
        <div className="mt-3 space-y-2">
          <h4 className="text-xs font-semibold text-slate-500">Risks</h4>
          {aiDiagnosis.risks.map((item, index) => (
            <div
              className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm"
              key={`risk-${index}`}
            >
              {item}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
```

---

# 12. 验证命令

## Python Agent

```powershell id="n3h3n4"
cd apps/aiops-agent
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[test]"
pytest
uvicorn aiops_agent.main:app --reload --port 9008
```

## Java

```powershell id="7dg3xb"
mvn -pl modules/aiops-ai-client -am test
mvn -pl apps/aiops-server -am test
```

## Frontend

```powershell id="thikby"
cd web/console
pnpm build
```

## Docker

```powershell id="0nv9ka"
docker compose -f infra/docker-compose.yml up -d aiops-agent
```

---

# 13. Phase4 验收流程

```txt id="rx0j39"
1. 启动 aiops-agent
2. 启动 aiops-server
3. 登录前端
4. Sync Zabbix
5. Aggregate Incidents
6. Analyze RCA
7. 点击 AI Diagnose
8. 前端展示 AI Diagnosis Agent
9. ai_diagnosis 表写入记录
10. incident_timeline 写入 ai_diagnosed 事件
11. 不触发任何自动化执行动作
```

---

# 14. 完成后的路线状态

```txt id="hi73jy"
Phase0：工程地基，完成
Phase1：Zabbix 接入与采集同步，完成
Phase2：Incident 聚合与事故中心，完成
Phase3：规则 RCA 与证据链，完成
Phase4：Python LangGraph OSS Diagnosis Agent，完成
Phase5：Runbook / Ansible 执行审批，下一阶段
Phase6：复盘与知识沉淀
```

[1]: https://docs.langchain.com/oss/python/langgraph/quickstart 'Quickstart - Docs by LangChain'
[2]: https://fastapi.tiangolo.com/tutorial/testing/ 'Testing - FastAPI'
