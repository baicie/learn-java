# Phase4.3：Evidence Tools 接入 Metrics / Logs / Changes

基于当前 `mvp` 最新实现，Phase4.3 不改 Java → Python 的 `AgentDiagnosisRequest v1` 协议，而是在 **Python LangGraph Agent 内部新增 evidence tool 调用**。

核心原则：

```txt
Python Agent 不直连 DB
Python Agent 不直连生产系统
Python Agent 只调用 Java 暴露的 internal evidence API
Java 负责从 VictoriaMetrics / log_event / change_event 取证据
Agent 只消费证据，不执行动作
```

---

# 1. Phase4.3 目标

```txt
Phase4.0：FastAPI + LangGraph deterministic Agent
Phase4.1：Contract + Safety Boundary
Phase4.2：OpenAI-compatible LLM Provider + fallback
Phase4.3：Metrics / Logs / Changes Evidence Tools
```

Phase4.3 完成后，AI 诊断结果的 `raw` 里会包含：

```json
{
  "metrics": {
    "available": true,
    "series": []
  },
  "logs": {
    "available": true,
    "patterns": []
  },
  "changes": {
    "available": true,
    "events": []
  }
}
```

---

# 2. 总体架构

```txt
aiops-server Java
  ├─ /api/incidents/{id}/ai/diagnose
  │    └─ 调 Python /v1/diagnose
  │
  └─ /internal/agent/evidence/query
       ├─ token 校验
       ├─ metrics evidence
       │    └─ VictoriaMetrics / Prometheus-compatible API
       ├─ logs evidence
       │    └─ log_event 表，后续可替换 ClickHouse
       └─ changes evidence
            └─ change_event 表

aiops-agent Python
  ├─ /v1/diagnose
  └─ LangGraph
       ├─ load_context
       ├─ analyze_alerts
       ├─ analyze_rca
       ├─ query_evidence
       ├─ search_runbooks
       ├─ safety_check
       └─ generate_diagnosis
```

---

# 3. 修改文件清单

```txt
apps/aiops-agent/
  pyproject.toml
  README.md
  src/aiops_agent/settings.py
  src/aiops_agent/evidence.py              # 新增
  src/aiops_agent/prompt.py
  src/aiops_agent/graph.py
  src/aiops_agent/service.py
  tests/test_evidence.py                   # 新增
  tests/test_graph.py
  tests/test_prompt.py

modules/
  aiops-evidence/                          # 新增 Java 模块
    pom.xml
    src/main/java/io/aegisops/evidence/
      AgentEvidenceProperties.java
      AgentEvidenceInternalController.java
      AgentEvidenceService.java
      EvidenceRepository.java
      JdbcEvidenceRepository.java
      MetricsEvidenceClient.java
      NoopMetricsEvidenceClient.java
      VictoriaMetricsEvidenceClient.java
      VictoriaMetricsProperties.java
      dto/
        ChangeEvidence.java
        ChangeEvidenceEvent.java
        EvidenceQueryRequest.java
        EvidenceQueryResponse.java
        LogEvidence.java
        LogPattern.java
        MetricEvidence.java
        MetricSeriesSummary.java
    src/test/java/io/aegisops/evidence/
      AgentEvidenceServiceTest.java
      AgentEvidenceInternalControllerTest.java
      VictoriaMetricsEvidenceClientTest.java

apps/aiops-server/
  pom.xml
  src/main/resources/db/migration/V7__phase4_3_evidence_events.sql

pom.xml
infra/docker-compose.yml
```

---

# 4. Java：新增 `aiops-evidence` 模块

## 4.1 根 `pom.xml`

在 `<modules>` 中新增：

```xml
<module>modules/aiops-evidence</module>
```

建议放在 `aiops-ai-client` 前：

```xml
<module>modules/aiops-rca</module>
<module>modules/aiops-evidence</module>
<module>modules/aiops-ai-client</module>
```

---

## 4.2 `apps/aiops-server/pom.xml`

新增依赖：

```xml
<dependency>
    <groupId>io.aegisops</groupId>
    <artifactId>aiops-evidence</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## 4.3 `modules/aiops-evidence/pom.xml`

```xml
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

    <artifactId>aiops-evidence</artifactId>
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
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
    </dependencies>
</project>
```

---

# 5. 数据库迁移

## `apps/aiops-server/src/main/resources/db/migration/V7__phase4_3_evidence_events.sql`

> 如果你当前仓库已经存在 `V7`，把文件名顺延成 `V8__phase4_3_evidence_events.sql`。

```sql
create table if not exists log_event (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  asset_id varchar(64),
  service_name varchar(255),
  severity varchar(32) not null default 'info',
  message text not null,
  attributes jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now()
);

create index if not exists idx_log_event_tenant_asset_time
  on log_event(tenant_id, asset_id, occurred_at desc);

create index if not exists idx_log_event_tenant_service_time
  on log_event(tenant_id, service_name, occurred_at desc);

create index if not exists idx_log_event_tenant_severity_time
  on log_event(tenant_id, severity, occurred_at desc);

create table if not exists change_event (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  asset_id varchar(64),
  service_name varchar(255),
  change_type varchar(64) not null,
  title varchar(512) not null,
  description text,
  source varchar(64) not null default 'manual',
  operator varchar(128),
  risk_level varchar(32) not null default 'medium',
  attributes jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now()
);

create index if not exists idx_change_event_tenant_asset_time
  on change_event(tenant_id, asset_id, occurred_at desc);

create index if not exists idx_change_event_tenant_service_time
  on change_event(tenant_id, service_name, occurred_at desc);

create index if not exists idx_change_event_tenant_time
  on change_event(tenant_id, occurred_at desc);
```

---

# 6. Java DTO

## `dto/EvidenceQueryRequest.java`

```java
package io.aegisops.evidence.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record EvidenceQueryRequest(
        String contractVersion,
        String tenantId,
        String incidentId,
        String traceId,
        String primaryAssetId,
        OffsetDateTime startedAt,
        OffsetDateTime lastSeenAt,
        List<String> alertFingerprints,
        List<String> alertTitles
) {
    public List<String> normalizedAlertFingerprints() {
        return alertFingerprints == null ? List.of() : alertFingerprints;
    }

    public List<String> normalizedAlertTitles() {
        return alertTitles == null ? List.of() : alertTitles;
    }
}
```

## `dto/EvidenceQueryResponse.java`

```java
package io.aegisops.evidence.dto;

public record EvidenceQueryResponse(
        String contractVersion,
        String tenantId,
        String incidentId,
        String traceId,
        MetricEvidence metrics,
        LogEvidence logs,
        ChangeEvidence changes
) {}
```

## `dto/MetricEvidence.java`

```java
package io.aegisops.evidence.dto;

import java.util.List;

public record MetricEvidence(
        boolean available,
        String reason,
        List<MetricSeriesSummary> series
) {
    public static MetricEvidence unavailable(String reason) {
        return new MetricEvidence(false, reason, List.of());
    }
}
```

## `dto/MetricSeriesSummary.java`

```java
package io.aegisops.evidence.dto;

import java.math.BigDecimal;

public record MetricSeriesSummary(
        String name,
        String query,
        BigDecimal min,
        BigDecimal max,
        BigDecimal avg,
        BigDecimal latest,
        int points
) {}
```

## `dto/LogEvidence.java`

```java
package io.aegisops.evidence.dto;

import java.util.List;

public record LogEvidence(
        boolean available,
        String reason,
        List<LogPattern> patterns
) {
    public static LogEvidence unavailable(String reason) {
        return new LogEvidence(false, reason, List.of());
    }
}
```

## `dto/LogPattern.java`

```java
package io.aegisops.evidence.dto;

import java.time.OffsetDateTime;

public record LogPattern(
        String severity,
        String sample,
        long count,
        OffsetDateTime firstSeenAt,
        OffsetDateTime lastSeenAt
) {}
```

## `dto/ChangeEvidence.java`

```java
package io.aegisops.evidence.dto;

import java.util.List;

public record ChangeEvidence(
        boolean available,
        String reason,
        List<ChangeEvidenceEvent> events
) {
    public static ChangeEvidence unavailable(String reason) {
        return new ChangeEvidence(false, reason, List.of());
    }
}
```

## `dto/ChangeEvidenceEvent.java`

```java
package io.aegisops.evidence.dto;

import java.time.OffsetDateTime;

public record ChangeEvidenceEvent(
        String id,
        String changeType,
        String title,
        String description,
        String source,
        String operator,
        String riskLevel,
        OffsetDateTime occurredAt
) {}
```

---

# 7. Java 配置

## `AgentEvidenceProperties.java`

```java
package io.aegisops.evidence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.agent.evidence")
public record AgentEvidenceProperties(
        String internalToken,
        Integer defaultLookbackMinutes,
        Integer maxLogPatterns,
        Integer maxChanges
) {
    public String normalizedInternalToken() {
        return internalToken == null || internalToken.isBlank() ? "dev-internal-token" : internalToken.trim();
    }

    public int normalizedDefaultLookbackMinutes() {
        return defaultLookbackMinutes == null || defaultLookbackMinutes <= 0 ? 60 : defaultLookbackMinutes;
    }

    public int normalizedMaxLogPatterns() {
        return maxLogPatterns == null || maxLogPatterns <= 0 ? 20 : maxLogPatterns;
    }

    public int normalizedMaxChanges() {
        return maxChanges == null || maxChanges <= 0 ? 20 : maxChanges;
    }
}
```

## `VictoriaMetricsProperties.java`

```java
package io.aegisops.evidence;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "aiops.evidence.victoria")
public record VictoriaMetricsProperties(
        Boolean enabled,
        String baseUrl,
        Integer timeoutMillis,
        String step,
        Map<String, String> queries
) {
    public boolean enabledOrFalse() {
        return Boolean.TRUE.equals(enabled);
    }

    public String normalizedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }

        String value = baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public int normalizedTimeoutMillis() {
        return timeoutMillis == null || timeoutMillis <= 0 ? 3000 : timeoutMillis;
    }

    public String normalizedStep() {
        return step == null || step.isBlank() ? "60s" : step.trim();
    }

    public Map<String, String> normalizedQueries() {
        if (queries == null || queries.isEmpty()) {
            return Map.of(
                    "cpu_usage", "avg_over_time(node_cpu_seconds_total{asset_id=\"${assetId}\"}[5m])",
                    "memory_usage", "avg_over_time(node_memory_MemAvailable_bytes{asset_id=\"${assetId}\"}[5m])",
                    "service_latency", "avg_over_time(http_request_duration_seconds{asset_id=\"${assetId}\"}[5m])"
            );
        }

        return queries;
    }
}
```

---

# 8. Java Metrics Client

## `MetricsEvidenceClient.java`

```java
package io.aegisops.evidence;

import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.MetricEvidence;

public interface MetricsEvidenceClient {
    MetricEvidence queryMetrics(EvidenceQueryRequest request);
}
```

## `NoopMetricsEvidenceClient.java`

```java
package io.aegisops.evidence;

import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.MetricEvidence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(MetricsEvidenceClient.class)
public class NoopMetricsEvidenceClient implements MetricsEvidenceClient {
    @Override
    public MetricEvidence queryMetrics(EvidenceQueryRequest request) {
        return MetricEvidence.unavailable("VictoriaMetrics evidence client is not enabled.");
    }
}
```

## `VictoriaMetricsEvidenceClient.java`

```java
package io.aegisops.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.MetricEvidence;
import io.aegisops.evidence.dto.MetricSeriesSummary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "aiops.evidence.victoria", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(VictoriaMetricsProperties.class)
public class VictoriaMetricsEvidenceClient implements MetricsEvidenceClient {
    private final VictoriaMetricsProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public VictoriaMetricsEvidenceClient(VictoriaMetricsProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, createRestTemplate(properties));
    }

    VictoriaMetricsEvidenceClient(
            VictoriaMetricsProperties properties,
            ObjectMapper objectMapper,
            RestTemplate restTemplate
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    @Override
    public MetricEvidence queryMetrics(EvidenceQueryRequest request) {
        if (!properties.enabledOrFalse()) {
            return MetricEvidence.unavailable("VictoriaMetrics evidence client is disabled.");
        }

        if (properties.normalizedBaseUrl().isBlank()) {
            return MetricEvidence.unavailable("VictoriaMetrics base URL is empty.");
        }

        if (request.primaryAssetId() == null || request.primaryAssetId().isBlank()) {
            return MetricEvidence.unavailable("Primary asset id is empty.");
        }

        List<MetricSeriesSummary> summaries = new ArrayList<>();

        for (Map.Entry<String, String> entry : properties.normalizedQueries().entrySet()) {
            String metricName = entry.getKey();
            String query = entry.getValue().replace("${assetId}", request.primaryAssetId());

            try {
                MetricSeriesSummary summary = queryRange(metricName, query, request.startedAt(), request.lastSeenAt());
                if (summary != null) {
                    summaries.add(summary);
                }
            } catch (Exception ignored) {
                // Evidence must never break diagnosis. Individual query failures are ignored.
            }
        }

        if (summaries.isEmpty()) {
            return MetricEvidence.unavailable("No metric evidence returned.");
        }

        return new MetricEvidence(true, "", summaries);
    }

    private MetricSeriesSummary queryRange(
            String metricName,
            String query,
            OffsetDateTime startedAt,
            OffsetDateTime lastSeenAt
    ) throws Exception {
        String url = UriComponentsBuilder
                .fromHttpUrl(properties.normalizedBaseUrl() + "/api/v1/query_range")
                .queryParam("query", query)
                .queryParam("start", startedAt.toInstant().getEpochSecond())
                .queryParam("end", lastSeenAt.toInstant().getEpochSecond())
                .queryParam("step", properties.normalizedStep())
                .toUriString();

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        JsonNode root = objectMapper.readTree(response.getBody());

        JsonNode result = root.path("data").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return null;
        }

        List<BigDecimal> values = new ArrayList<>();

        for (JsonNode series : result) {
            JsonNode points = series.path("values");
            if (!points.isArray()) {
                continue;
            }

            for (JsonNode point : points) {
                if (point.isArray() && point.size() >= 2) {
                    String raw = point.get(1).asText();
                    try {
                        values.add(new BigDecimal(raw));
                    } catch (NumberFormatException ignored) {
                        // skip invalid data points
                    }
                }
            }
        }

        if (values.isEmpty()) {
            return null;
        }

        BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
        BigDecimal latest = values.get(values.size() - 1);

        return new MetricSeriesSummary(metricName, query, min, max, avg, latest, values.size());
    }

    private static RestTemplate createRestTemplate(VictoriaMetricsProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.normalizedTimeoutMillis());
        factory.setReadTimeout(properties.normalizedTimeoutMillis());
        return new RestTemplate(factory);
    }
}
```

---

# 9. Java Repository

## `EvidenceRepository.java`

```java
package io.aegisops.evidence;

import io.aegisops.evidence.dto.ChangeEvidence;
import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.LogEvidence;

public interface EvidenceRepository {
    LogEvidence queryLogs(EvidenceQueryRequest request, int maxPatterns);

    ChangeEvidence queryChanges(EvidenceQueryRequest request, int maxChanges);
}
```

## `JdbcEvidenceRepository.java`

```java
package io.aegisops.evidence;

import io.aegisops.evidence.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class JdbcEvidenceRepository implements EvidenceRepository {
    private final JdbcTemplate jdbc;

    public JdbcEvidenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public LogEvidence queryLogs(EvidenceQueryRequest request, int maxPatterns) {
        if (request.primaryAssetId() == null || request.primaryAssetId().isBlank()) {
            return LogEvidence.unavailable("Primary asset id is empty.");
        }

        List<LogPattern> patterns = jdbc.query("""
                select severity,
                       min(message) as sample,
                       count(*) as count,
                       min(occurred_at) as first_seen_at,
                       max(occurred_at) as last_seen_at
                from log_event
                where tenant_id = ?
                  and asset_id = ?
                  and occurred_at >= ?
                  and occurred_at <= ?
                  and severity in ('error', 'fatal', 'critical', 'warn', 'warning')
                group by severity, left(message, 160)
                order by count(*) desc, max(occurred_at) desc
                limit ?
                """, (rs, rowNum) -> new LogPattern(
                rs.getString("severity"),
                rs.getString("sample"),
                rs.getLong("count"),
                rs.getObject("first_seen_at", OffsetDateTime.class),
                rs.getObject("last_seen_at", OffsetDateTime.class)
        ), request.tenantId(), request.primaryAssetId(), request.startedAt(), request.lastSeenAt(), maxPatterns);

        if (patterns.isEmpty()) {
            return LogEvidence.unavailable("No error log evidence found.");
        }

        return new LogEvidence(true, "", patterns);
    }

    @Override
    public ChangeEvidence queryChanges(EvidenceQueryRequest request, int maxChanges) {
        List<ChangeEvidenceEvent> events = jdbc.query("""
                select id, change_type, title, description, source, operator, risk_level, occurred_at
                from change_event
                where tenant_id = ?
                  and (asset_id = ? or service_name in (?))
                  and occurred_at >= ?
                  and occurred_at <= ?
                order by occurred_at desc
                limit ?
                """, (rs, rowNum) -> new ChangeEvidenceEvent(
                rs.getString("id"),
                rs.getString("change_type"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("source"),
                rs.getString("operator"),
                rs.getString("risk_level"),
                rs.getObject("occurred_at", OffsetDateTime.class)
        ), request.tenantId(), request.primaryAssetId(), firstTitleOrEmpty(request), request.startedAt(), request.lastSeenAt(), maxChanges);

        if (events.isEmpty()) {
            return ChangeEvidence.unavailable("No change evidence found.");
        }

        return new ChangeEvidence(true, "", events);
    }

    private String firstTitleOrEmpty(EvidenceQueryRequest request) {
        if (request.normalizedAlertTitles().isEmpty()) {
            return "";
        }
        return request.normalizedAlertTitles().get(0);
    }
}
```

---

# 10. Java Service + Internal Controller

## `AgentEvidenceService.java`

```java
package io.aegisops.evidence;

import io.aegisops.evidence.dto.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@EnableConfigurationProperties({AgentEvidenceProperties.class, VictoriaMetricsProperties.class})
public class AgentEvidenceService {
    private final AgentEvidenceProperties properties;
    private final MetricsEvidenceClient metricsClient;
    private final EvidenceRepository repository;

    public AgentEvidenceService(
            AgentEvidenceProperties properties,
            MetricsEvidenceClient metricsClient,
            EvidenceRepository repository
    ) {
        this.properties = properties;
        this.metricsClient = metricsClient;
        this.repository = repository;
    }

    public EvidenceQueryResponse query(EvidenceQueryRequest request) {
        EvidenceQueryRequest normalized = normalizeWindow(request);

        MetricEvidence metrics = metricsClient.queryMetrics(normalized);
        LogEvidence logs = repository.queryLogs(normalized, properties.normalizedMaxLogPatterns());
        ChangeEvidence changes = repository.queryChanges(normalized, properties.normalizedMaxChanges());

        return new EvidenceQueryResponse(
                normalized.contractVersion(),
                normalized.tenantId(),
                normalized.incidentId(),
                normalized.traceId(),
                metrics,
                logs,
                changes
        );
    }

    private EvidenceQueryRequest normalizeWindow(EvidenceQueryRequest request) {
        OffsetDateTime end = request.lastSeenAt() == null ? OffsetDateTime.now() : request.lastSeenAt();
        OffsetDateTime start = request.startedAt() == null
                ? end.minusMinutes(properties.normalizedDefaultLookbackMinutes())
                : request.startedAt();

        return new EvidenceQueryRequest(
                request.contractVersion(),
                request.tenantId(),
                request.incidentId(),
                request.traceId(),
                request.primaryAssetId(),
                start,
                end,
                request.normalizedAlertFingerprints(),
                request.normalizedAlertTitles()
        );
    }
}
```

## `AgentEvidenceInternalController.java`

```java
package io.aegisops.evidence;

import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.EvidenceQueryResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/agent/evidence")
@EnableConfigurationProperties(AgentEvidenceProperties.class)
public class AgentEvidenceInternalController {
    private final AgentEvidenceProperties properties;
    private final AgentEvidenceService service;

    public AgentEvidenceInternalController(AgentEvidenceProperties properties, AgentEvidenceService service) {
        this.properties = properties;
        this.service = service;
    }

    @PostMapping("/query")
    public EvidenceQueryResponse query(
            @RequestHeader(value = "X-AegisOps-Internal-Token", required = false) String token,
            @RequestBody EvidenceQueryRequest request
    ) {
        if (!properties.normalizedInternalToken().equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid internal token");
        }

        return service.query(request);
    }
}
```

---

# 11. Python：Evidence Client

## `apps/aiops-agent/src/aiops_agent/settings.py`

追加字段：

```python
    evidence_enabled: bool = False
    evidence_base_url: str = ""
    evidence_internal_token: str = "dev-internal-token"
    evidence_timeout_seconds: float = 5.0

    def normalized_evidence_base_url(self) -> str:
        value = (self.evidence_base_url or "").strip()
        while value.endswith("/"):
            value = value[:-1]
        return value
```

完整 `settings.py` 应包含 Phase4.2 已有字段，这里只展示新增部分。

---

## `apps/aiops-agent/src/aiops_agent/evidence.py`

```python
from __future__ import annotations

from typing import Any, Protocol

import httpx
from pydantic import BaseModel, Field

from aiops_agent.schemas import DiagnoseRequest
from aiops_agent.settings import Settings


class EvidenceBundle(BaseModel):
    metrics: dict[str, Any] = Field(default_factory=dict)
    logs: dict[str, Any] = Field(default_factory=dict)
    changes: dict[str, Any] = Field(default_factory=dict)


class EvidenceClient(Protocol):
    def query(self, request: DiagnoseRequest) -> EvidenceBundle:
        ...


class DisabledEvidenceClient:
    def query(self, request: DiagnoseRequest) -> EvidenceBundle:
        return EvidenceBundle(
            metrics={
                "available": False,
                "reason": "Evidence client is disabled.",
                "series": [],
            },
            logs={
                "available": False,
                "reason": "Evidence client is disabled.",
                "patterns": [],
            },
            changes={
                "available": False,
                "reason": "Evidence client is disabled.",
                "events": [],
            },
        )


class HttpEvidenceClient:
    def __init__(self, settings: Settings):
        self.settings = settings

    def query(self, request: DiagnoseRequest) -> EvidenceBundle:
        if not self.settings.evidence_enabled:
            return DisabledEvidenceClient().query(request)

        base_url = self.settings.normalized_evidence_base_url()
        if not base_url:
            return DisabledEvidenceClient().query(request)

        payload = build_evidence_query_payload(request)

        try:
            response = httpx.post(
                f"{base_url}/query",
                headers={
                    "Content-Type": "application/json",
                    "X-AegisOps-Internal-Token": self.settings.evidence_internal_token,
                },
                json=payload,
                timeout=self.settings.evidence_timeout_seconds,
            )
            response.raise_for_status()
            data = response.json()

            return EvidenceBundle(
                metrics=data.get("metrics") or {},
                logs=data.get("logs") or {},
                changes=data.get("changes") or {},
            )
        except Exception as exc:
            return EvidenceBundle(
                metrics={
                    "available": False,
                    "reason": f"Evidence query failed: {type(exc).__name__}: {exc}",
                    "series": [],
                },
                logs={
                    "available": False,
                    "reason": f"Evidence query failed: {type(exc).__name__}: {exc}",
                    "patterns": [],
                },
                changes={
                    "available": False,
                    "reason": f"Evidence query failed: {type(exc).__name__}: {exc}",
                    "events": [],
                },
            )


def build_evidence_query_payload(request: DiagnoseRequest) -> dict[str, Any]:
    return {
        "contractVersion": request.contractVersion,
        "tenantId": request.tenantId,
        "incidentId": request.incidentId,
        "traceId": request.traceId,
        "primaryAssetId": request.incident.primaryAssetId,
        "startedAt": request.incident.startedAt.isoformat() if request.incident.startedAt else None,
        "lastSeenAt": request.incident.lastSeenAt.isoformat() if request.incident.lastSeenAt else None,
        "alertFingerprints": [alert.fingerprint for alert in request.alerts if alert.fingerprint],
        "alertTitles": [alert.title for alert in request.alerts if alert.title],
    }


def create_evidence_client(settings: Settings) -> EvidenceClient:
    if not settings.evidence_enabled:
        return DisabledEvidenceClient()
    return HttpEvidenceClient(settings)
```

---

# 12. Python：修改 Prompt

## `apps/aiops-agent/src/aiops_agent/prompt.py`

把 `changes` 加进去：

```python
from __future__ import annotations

import json
from typing import Any

from aiops_agent.schemas import DiagnoseRequest


def build_diagnosis_prompt(
    request: DiagnoseRequest,
    incident_summary: dict[str, Any],
    alert_analysis: dict[str, Any],
    rca_analysis: dict[str, Any],
    metrics: dict[str, Any],
    logs: dict[str, Any],
    changes: dict[str, Any],
    runbook_suggestions: list[str],
    risks: list[str],
) -> list[dict[str, str]]:
    system = (
        "You are AegisOps Diagnosis Agent. "
        "You diagnose AIOps incidents from structured evidence. "
        "You must not execute commands. "
        "You must not modify systems. "
        "You must not restart services, rollback deployments, delete resources, or close incidents. "
        "All remediation is suggestion-only in Phase4.3. "
        "Return strict JSON only. Do not wrap JSON in markdown."
    )

    user_payload = {
        "contractVersion": request.contractVersion,
        "locale": request.locale,
        "tenantId": request.tenantId,
        "incidentId": request.incidentId,
        "traceId": request.traceId,
        "evidence": {
            "incident": incident_summary,
            "alerts": alert_analysis,
            "rca": rca_analysis,
            "metrics": metrics,
            "logs": logs,
            "changes": changes,
            "runbookSuggestions": runbook_suggestions,
            "safetyRisks": risks,
        },
        "outputSchema": {
            "summary": "string, concise incident diagnosis summary",
            "rootCause": "string, most likely root cause based on evidence",
            "impact": "string, possible business or technical impact",
            "nextSteps": ["string, manual troubleshooting step"],
            "runbookSuggestions": ["string, runbook name or direction"],
            "risks": ["string, risk, caveat, or safety warning"],
        },
        "rules": [
            "Return JSON only.",
            "Do not include markdown fences.",
            "Do not invent metrics, logs, or changes.",
            "If metrics/logs/changes are unavailable, say so.",
            "Separate confirmed evidence from hypothesis.",
            "Do not suggest automatic execution.",
            "Do not suggest destructive commands.",
            "Prefer safe verification steps before remediation.",
        ],
    }

    return [
        {"role": "system", "content": system},
        {"role": "user", "content": json.dumps(user_payload, ensure_ascii=False, indent=2)},
    ]
```

---

# 13. Python：修改 Graph

## `apps/aiops-agent/src/aiops_agent/graph.py`

完整替换：

```python
from __future__ import annotations

from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph

from aiops_agent.evidence import EvidenceClient, create_evidence_client
from aiops_agent.llm import (
    LlmClient,
    OpenAiCompatibleLlmClient,
    diagnosis_response_from_draft,
    parse_diagnosis_json,
)
from aiops_agent.prompt import build_diagnosis_prompt
from aiops_agent.safety import apply_safety_boundary
from aiops_agent.schemas import DiagnoseRequest, DiagnoseResponse
from aiops_agent.settings import Settings
from aiops_agent.tools import (
    inspect_alerts,
    inspect_rca_evidence,
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
    changes: dict[str, Any]
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


def query_evidence(settings: Settings, evidence_client: EvidenceClient | None = None):
    def _node(state: DiagnosisState) -> DiagnosisState:
        client = evidence_client or create_evidence_client(settings)
        bundle = client.query(state["request"])

        return {
            "metrics": bundle.metrics,
            "logs": bundle.logs,
            "changes": bundle.changes,
        }

    return _node


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


def deterministic_diagnosis(
    state: DiagnosisState,
    settings: Settings,
    fallback_reason: str | None = None,
) -> DiagnoseResponse:
    request = state["request"]
    incident = state.get("incident_summary", {})
    alerts = state.get("alert_analysis", {})
    rca = state.get("rca_analysis", {})
    runbooks = state.get("runbook_suggestions", [])
    risks = state.get("risks", [])

    metrics = state.get("metrics", {})
    logs = state.get("logs", {})
    changes = state.get("changes", {})

    root_cause = (
        _root_cause_from_change_evidence(changes)
        or rca.get("rootCause")
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

    metric_hint = _metric_hint(metrics)
    log_hint = _log_hint(logs)
    change_hint = _change_hint(changes)

    summary = (
        f"Incident {request.incidentId} is {incident.get('status', 'unknown')} "
        f"with severity {incident.get('severity', alerts.get('topSeverity', 'info'))}. "
        f"{alerts.get('count', 0)} linked alert(s) were analyzed. "
        f"{metric_hint} {log_hint} {change_hint}"
    ).strip()

    impact = (
        f"The primary impact may be concentrated on {dominant_asset}. "
        "Downstream services may be affected if this asset is part of a dependency path."
    )

    next_steps = [
        f"Confirm whether the dominant fingerprint `{dominant_fingerprint}` is still firing.",
        f"Check the primary asset `{dominant_asset}` around the incident start time.",
        "Compare metrics before and after incident detection.",
        "Review error log patterns around the incident window.",
        "Review recent deployments, restarts, configuration changes, and dependency health.",
        "Validate RCA and evidence before taking remediation action.",
    ]

    raw = {
        "graph": "aegisops_diagnosis_graph",
        "contractVersion": settings.contract_version,
        "traceId": request.traceId,
        "generationMode": "deterministic",
        "fallbackReason": fallback_reason or "",
        "incident": incident,
        "alerts": alerts,
        "rca": rca,
        "metrics": metrics,
        "logs": logs,
        "changes": changes,
    }

    response = DiagnoseResponse(
        contractVersion=settings.contract_version,
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

    return apply_safety_boundary(response)


def generate_diagnosis(settings: Settings, llm_client: LlmClient | None = None):
    def _node(state: DiagnosisState) -> DiagnosisState:
        if settings.normalized_generation_mode() != "openai-compatible":
            return {
                "diagnosis": deterministic_diagnosis(state, settings),
            }

        client = llm_client or OpenAiCompatibleLlmClient(settings)

        messages = build_diagnosis_prompt(
            request=state["request"],
            incident_summary=state.get("incident_summary", {}),
            alert_analysis=state.get("alert_analysis", {}),
            rca_analysis=state.get("rca_analysis", {}),
            metrics=state.get("metrics", {}),
            logs=state.get("logs", {}),
            changes=state.get("changes", {}),
            runbook_suggestions=state.get("runbook_suggestions", []),
            risks=state.get("risks", []),
        )

        try:
            content = client.complete_json(messages)
            draft = parse_diagnosis_json(content)
            response = diagnosis_response_from_draft(
                draft,
                settings,
                raw={
                    "graph": "aegisops_diagnosis_graph",
                    "contractVersion": settings.contract_version,
                    "traceId": state["request"].traceId,
                    "generationMode": "openai-compatible",
                    "llmContent": content,
                    "metrics": state.get("metrics", {}),
                    "logs": state.get("logs", {}),
                    "changes": state.get("changes", {}),
                },
                provider="openai-compatible",
            )

            return {
                "diagnosis": apply_safety_boundary(response),
            }
        except Exception as exc:
            return {
                "diagnosis": deterministic_diagnosis(
                    state,
                    settings,
                    fallback_reason=f"{type(exc).__name__}: {exc}",
                )
            }

    return _node


def build_diagnosis_graph(
    settings: Settings,
    llm_client: LlmClient | None = None,
    evidence_client: EvidenceClient | None = None,
):
    graph = StateGraph(DiagnosisState)

    graph.add_node("load_context", load_context)
    graph.add_node("analyze_alerts", analyze_alerts)
    graph.add_node("analyze_rca", analyze_rca)
    graph.add_node("query_evidence", query_evidence(settings, evidence_client))
    graph.add_node("search_runbooks", search_runbooks)
    graph.add_node("safety_check", safety_check)
    graph.add_node("generate_diagnosis", generate_diagnosis(settings, llm_client))

    graph.add_edge(START, "load_context")
    graph.add_edge("load_context", "analyze_alerts")
    graph.add_edge("analyze_alerts", "analyze_rca")
    graph.add_edge("analyze_rca", "query_evidence")
    graph.add_edge("query_evidence", "search_runbooks")
    graph.add_edge("search_runbooks", "safety_check")
    graph.add_edge("safety_check", "generate_diagnosis")
    graph.add_edge("generate_diagnosis", END)

    return graph.compile()


def run_diagnosis_graph(
    request: DiagnoseRequest,
    settings: Settings,
    llm_client: LlmClient | None = None,
    evidence_client: EvidenceClient | None = None,
) -> DiagnoseResponse:
    compiled = build_diagnosis_graph(settings, llm_client, evidence_client)
    result = compiled.invoke({"request": request})
    diagnosis = result.get("diagnosis")
    if not isinstance(diagnosis, DiagnoseResponse):
        raise RuntimeError("diagnosis graph did not return DiagnoseResponse")
    return diagnosis


def _metric_hint(metrics: dict[str, Any]) -> str:
    if metrics.get("available"):
        count = len(metrics.get("series") or [])
        return f"{count} metric series were available."
    return "Metric evidence is unavailable."


def _log_hint(logs: dict[str, Any]) -> str:
    if logs.get("available"):
        count = len(logs.get("patterns") or [])
        return f"{count} log pattern(s) were found."
    return "Log evidence is unavailable."


def _change_hint(changes: dict[str, Any]) -> str:
    if changes.get("available"):
        count = len(changes.get("events") or [])
        return f"{count} recent change event(s) were found."
    return "Change evidence is unavailable."


def _root_cause_from_change_evidence(changes: dict[str, Any]) -> str | None:
    if not changes.get("available"):
        return None

    events = changes.get("events") or []
    if not events:
        return None

    first = events[0]
    title = first.get("title") if isinstance(first, dict) else None
    change_type = first.get("changeType") if isinstance(first, dict) else None

    if title:
        return f"Recent {change_type or 'change'} may be related: {title}"

    return None
```

---

# 14. Python：修改 Service

## `apps/aiops-agent/src/aiops_agent/service.py`

```python
from __future__ import annotations

from aiops_agent.evidence import EvidenceClient
from aiops_agent.graph import run_diagnosis_graph
from aiops_agent.llm import LlmClient
from aiops_agent.schemas import DiagnoseRequest, DiagnoseResponse
from aiops_agent.settings import Settings


class DiagnosisService:
    def __init__(
        self,
        settings: Settings,
        llm_client: LlmClient | None = None,
        evidence_client: EvidenceClient | None = None,
    ):
        self.settings = settings
        self.llm_client = llm_client
        self.evidence_client = evidence_client

    def diagnose(self, request: DiagnoseRequest) -> DiagnoseResponse:
        return run_diagnosis_graph(
            request,
            self.settings,
            self.llm_client,
            self.evidence_client,
        )
```

---

# 15. Python 单元测试

## `apps/aiops-agent/tests/test_evidence.py`

```python
from aiops_agent.evidence import (
    DisabledEvidenceClient,
    HttpEvidenceClient,
    build_evidence_query_payload,
)
from aiops_agent.schemas import AlertContext, DiagnoseRequest, IncidentContext
from aiops_agent.settings import Settings


class FakeHttpxResponse:
    def __init__(self, payload: dict):
        self.payload = payload

    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict:
        return self.payload


def request() -> DiagnoseRequest:
    return DiagnoseRequest(
        contractVersion="agent-diagnosis.v1",
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(
            id="inc_1",
            primaryAssetId="asset_1",
            startedAt="2026-06-16T10:00:00+09:00",
            lastSeenAt="2026-06-16T10:10:00+09:00",
        ),
        alerts=[
            AlertContext(id="a1", title="CPU high", fingerprint="fp_cpu"),
        ],
        traceId="trace_1",
    )


def test_disabled_evidence_client_returns_unavailable_bundle():
    bundle = DisabledEvidenceClient().query(request())

    assert bundle.metrics["available"] is False
    assert bundle.logs["available"] is False
    assert bundle.changes["available"] is False


def test_build_evidence_query_payload():
    payload = build_evidence_query_payload(request())

    assert payload["tenantId"] == "tenant_1"
    assert payload["incidentId"] == "inc_1"
    assert payload["primaryAssetId"] == "asset_1"
    assert payload["alertFingerprints"] == ["fp_cpu"]
    assert payload["alertTitles"] == ["CPU high"]


def test_http_evidence_client_posts_internal_request(monkeypatch):
    captured = {}

    def fake_post(url, headers, json, timeout):
        captured["url"] = url
        captured["headers"] = headers
        captured["json"] = json
        captured["timeout"] = timeout
        return FakeHttpxResponse({
            "metrics": {"available": True, "series": []},
            "logs": {"available": True, "patterns": []},
            "changes": {"available": True, "events": []},
        })

    monkeypatch.setattr("aiops_agent.evidence.httpx.post", fake_post)

    settings = Settings(
        evidence_enabled=True,
        evidence_base_url="http://server:8080/internal/agent/evidence/",
        evidence_internal_token="evidence-token",
        evidence_timeout_seconds=3,
    )

    bundle = HttpEvidenceClient(settings).query(request())

    assert captured["url"] == "http://server:8080/internal/agent/evidence/query"
    assert captured["headers"]["X-AegisOps-Internal-Token"] == "evidence-token"
    assert captured["json"]["traceId"] == "trace_1"
    assert captured["timeout"] == 3
    assert bundle.metrics["available"] is True


def test_http_evidence_client_falls_back_on_error(monkeypatch):
    def fake_post(url, headers, json, timeout):
        raise RuntimeError("server down")

    monkeypatch.setattr("aiops_agent.evidence.httpx.post", fake_post)

    settings = Settings(
        evidence_enabled=True,
        evidence_base_url="http://server:8080/internal/agent/evidence",
    )

    bundle = HttpEvidenceClient(settings).query(request())

    assert bundle.metrics["available"] is False
    assert "server down" in bundle.metrics["reason"]
```

---

## `apps/aiops-agent/tests/test_prompt.py`

```python
from aiops_agent.prompt import build_diagnosis_prompt
from aiops_agent.schemas import DiagnoseRequest, IncidentContext


def test_build_diagnosis_prompt_includes_evidence_sections():
    request = DiagnoseRequest(
        contractVersion="agent-diagnosis.v1",
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1", title="CPU high"),
        alerts=[],
        locale="zh-CN",
        traceId="trace_1",
    )

    messages = build_diagnosis_prompt(
        request=request,
        incident_summary={"title": "CPU high"},
        alert_analysis={"count": 1},
        rca_analysis={"hasRca": False},
        metrics={"available": True, "series": []},
        logs={"available": True, "patterns": []},
        changes={"available": True, "events": []},
        runbook_suggestions=["Generic incident triage checklist"],
        risks=["Do not execute remediation automatically."],
    )

    content = messages[1]["content"]

    assert "metrics" in content
    assert "logs" in content
    assert "changes" in content
    assert "Do not invent metrics, logs, or changes" in content
```

---

## `apps/aiops-agent/tests/test_graph.py`

在原测试基础上增加 fake evidence client。

```python
from aiops_agent.evidence import EvidenceBundle
from aiops_agent.graph import run_diagnosis_graph
from aiops_agent.schemas import AlertContext, DiagnoseRequest, IncidentContext, RcaContext
from aiops_agent.settings import Settings


class FakeEvidenceClient:
    def query(self, request):
        return EvidenceBundle(
            metrics={
                "available": True,
                "series": [
                    {"name": "cpu_usage", "max": "0.95", "latest": "0.92"}
                ],
            },
            logs={
                "available": True,
                "patterns": [
                    {"severity": "error", "sample": "timeout", "count": 3}
                ],
            },
            changes={
                "available": True,
                "events": [
                    {"changeType": "deploy", "title": "deploy checkout-service v2"}
                ],
            },
        )


class FakeLlmClient:
    def complete_json(self, messages):
        return """
        {
          "summary": "LLM summary with evidence",
          "rootCause": "deployment may be related",
          "impact": "latency increase",
          "nextSteps": ["check deployment diff"],
          "runbookSuggestions": ["deployment rollback checklist"],
          "risks": ["manual approval required"]
        }
        """


def request() -> DiagnoseRequest:
    return DiagnoseRequest(
        contractVersion="agent-diagnosis.v1",
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
        ],
        rca=RcaContext(
            id="rca_1",
            suspectedRootCause="CPU saturation",
            confidence=0.8,
            summary="RCA summary",
        ),
        traceId="trace_1",
    )


def test_deterministic_graph_includes_evidence_in_raw():
    settings = Settings(
        generation_mode="deterministic",
        provider="aiops-agent",
        model="langgraph-deterministic",
        agent_name="aegisops_diagnosis_graph",
    )

    response = run_diagnosis_graph(
        request(),
        settings,
        evidence_client=FakeEvidenceClient(),
    )

    assert response.raw["metrics"]["available"] is True
    assert response.raw["logs"]["available"] is True
    assert response.raw["changes"]["available"] is True
    assert "deploy checkout-service v2" in response.rootCause


def test_llm_graph_includes_evidence_in_raw():
    settings = Settings(
        generation_mode="openai-compatible",
        provider="aiops-agent",
        model="test-model",
        agent_name="aegisops_diagnosis_graph",
    )

    response = run_diagnosis_graph(
        request(),
        settings,
        llm_client=FakeLlmClient(),
        evidence_client=FakeEvidenceClient(),
    )

    assert response.provider == "openai-compatible"
    assert response.raw["metrics"]["available"] is True
    assert response.raw["logs"]["available"] is True
    assert response.raw["changes"]["available"] is True
    assert response.rootCause == "deployment may be related"
```

---

# 16. Java 单元测试

## `AgentEvidenceServiceTest.java`

```java
package io.aegisops.evidence;

import io.aegisops.evidence.dto.*;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentEvidenceServiceTest {
    @Test
    void queryNormalizesWindowAndReturnsEvidence() {
        AgentEvidenceProperties properties = new AgentEvidenceProperties(
                "token",
                60,
                10,
                10
        );

        AgentEvidenceService service = new AgentEvidenceService(
                properties,
                request -> new MetricEvidence(true, "", List.of(
                        new MetricSeriesSummary("cpu", "query", null, null, null, null, 0)
                )),
                new FakeEvidenceRepository()
        );

        EvidenceQueryResponse response = service.query(new EvidenceQueryRequest(
                "agent-diagnosis.v1",
                "tenant_1",
                "inc_1",
                "trace_1",
                "asset_1",
                null,
                OffsetDateTime.parse("2026-06-16T10:00:00+09:00"),
                List.of("fp_cpu"),
                List.of("CPU high")
        ));

        assertEquals("tenant_1", response.tenantId());
        assertTrue(response.metrics().available());
        assertTrue(response.logs().available());
        assertTrue(response.changes().available());
    }

    private static final class FakeEvidenceRepository implements EvidenceRepository {
        @Override
        public LogEvidence queryLogs(EvidenceQueryRequest request, int maxPatterns) {
            assertNotNull(request.startedAt());
            assertNotNull(request.lastSeenAt());
            return new LogEvidence(true, "", List.of(
                    new LogPattern("error", "timeout", 3, request.startedAt(), request.lastSeenAt())
            ));
        }

        @Override
        public ChangeEvidence queryChanges(EvidenceQueryRequest request, int maxChanges) {
            return new ChangeEvidence(true, "", List.of(
                    new ChangeEvidenceEvent(
                            "chg_1",
                            "deploy",
                            "deploy v2",
                            "desc",
                            "jenkins",
                            "alice",
                            "medium",
                            request.lastSeenAt()
                    )
            ));
        }
    }
}
```

---

## `AgentEvidenceInternalControllerTest.java`

```java
package io.aegisops.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.aegisops.evidence.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AgentEvidenceInternalControllerTest {
    @Test
    void rejectsInvalidToken() throws Exception {
        MockMvc mvc = standaloneSetup(controller()).build();

        mvc.perform(post("/internal/agent/evidence/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-AegisOps-Internal-Token", "bad")
                        .content(json(request())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsValidToken() throws Exception {
        MockMvc mvc = standaloneSetup(controller()).build();

        mvc.perform(post("/internal/agent/evidence/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-AegisOps-Internal-Token", "token")
                        .content(json(request())))
                .andExpect(status().isOk());
    }

    private AgentEvidenceInternalController controller() {
        AgentEvidenceProperties properties = new AgentEvidenceProperties("token", 60, 10, 10);
        AgentEvidenceService service = new AgentEvidenceService(
                properties,
                req -> MetricEvidence.unavailable("noop"),
                new EvidenceRepository() {
                    @Override
                    public LogEvidence queryLogs(EvidenceQueryRequest request, int maxPatterns) {
                        return LogEvidence.unavailable("noop");
                    }

                    @Override
                    public ChangeEvidence queryChanges(EvidenceQueryRequest request, int maxChanges) {
                        return ChangeEvidence.unavailable("noop");
                    }
                }
        );

        return new AgentEvidenceInternalController(properties, service);
    }

    private EvidenceQueryRequest request() {
        return new EvidenceQueryRequest(
                "agent-diagnosis.v1",
                "tenant_1",
                "inc_1",
                "trace_1",
                "asset_1",
                OffsetDateTime.parse("2026-06-16T10:00:00+09:00"),
                OffsetDateTime.parse("2026-06-16T10:10:00+09:00"),
                List.of("fp_cpu"),
                List.of("CPU high")
        );
    }

    private String json(Object value) throws Exception {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .writeValueAsString(value);
    }
}
```

---

## `VictoriaMetricsEvidenceClientTest.java`

```java
package io.aegisops.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.MetricEvidence;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VictoriaMetricsEvidenceClientTest {
    @Test
    void returnsMetricEvidenceFromQueryRange() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();

        server.expect(request -> assertTrue(request.getURI().toString().contains("/api/v1/query_range")))
                .andRespond(withSuccess("""
                        {
                          "status": "success",
                          "data": {
                            "result": [
                              {
                                "metric": {"instance": "asset_1"},
                                "values": [
                                  [1780000000, "0.1"],
                                  [1780000060, "0.5"],
                                  [1780000120, "0.9"]
                                ]
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        VictoriaMetricsProperties properties = new VictoriaMetricsProperties(
                true,
                "http://victoria:8428",
                1000,
                "60s",
                Map.of("cpu_usage", "cpu{asset_id=\"${assetId}\"}")
        );

        VictoriaMetricsEvidenceClient client = new VictoriaMetricsEvidenceClient(
                properties,
                new ObjectMapper(),
                restTemplate
        );

        MetricEvidence evidence = client.queryMetrics(new EvidenceQueryRequest(
                "agent-diagnosis.v1",
                "tenant_1",
                "inc_1",
                "trace_1",
                "asset_1",
                OffsetDateTime.parse("2026-06-16T10:00:00+09:00"),
                OffsetDateTime.parse("2026-06-16T10:10:00+09:00"),
                List.of(),
                List.of()
        ));

        assertTrue(evidence.available());
        assertEquals(1, evidence.series().size());
        assertEquals("cpu_usage", evidence.series().get(0).name());
        assertEquals(3, evidence.series().get(0).points());

        server.verify();
    }

    @Test
    void returnsUnavailableWhenDisabled() {
        VictoriaMetricsEvidenceClient client = new VictoriaMetricsEvidenceClient(
                new VictoriaMetricsProperties(false, "", 1000, "60s", Map.of()),
                new ObjectMapper(),
                new RestTemplate()
        );

        MetricEvidence evidence = client.queryMetrics(new EvidenceQueryRequest(
                "agent-diagnosis.v1",
                "tenant_1",
                "inc_1",
                "trace_1",
                "asset_1",
                OffsetDateTime.now().minusMinutes(10),
                OffsetDateTime.now(),
                List.of(),
                List.of()
        ));

        assertFalse(evidence.available());
    }
}
```

---

# 17. Docker Compose 配置

## `infra/docker-compose.yml`

`aiops-agent.environment` 追加：

```yaml
AIOPS_AGENT_EVIDENCE_ENABLED: "true"
AIOPS_AGENT_EVIDENCE_BASE_URL: http://aiops-server:8080/internal/agent/evidence
AIOPS_AGENT_EVIDENCE_INTERNAL_TOKEN: dev-internal-token
AIOPS_AGENT_EVIDENCE_TIMEOUT_SECONDS: 5
```

`aiops-server.environment` 或配置文件增加：

```yaml
AIOPS_AGENT_EVIDENCE_INTERNAL_TOKEN: dev-internal-token
AIOPS_EVIDENCE_VICTORIA_ENABLED: "true"
AIOPS_EVIDENCE_VICTORIA_BASE_URL: http://victoria-metrics:8428
AIOPS_EVIDENCE_VICTORIA_STEP: 60s
```

Spring properties 等价配置：

```properties
aiops.agent.evidence.internal-token=dev-internal-token
aiops.agent.evidence.default-lookback-minutes=60
aiops.agent.evidence.max-log-patterns=20
aiops.agent.evidence.max-changes=20

aiops.evidence.victoria.enabled=true
aiops.evidence.victoria.base-url=http://victoria-metrics:8428
aiops.evidence.victoria.timeout-millis=3000
aiops.evidence.victoria.step=60s
```

---

# 18. README 追加

## `apps/aiops-agent/README.md`

追加：

````md
## Phase4.3 Evidence Tools

Python Agent does not connect to databases or production systems directly.

It queries Java internal evidence API:

```bash
AIOPS_AGENT_EVIDENCE_ENABLED=true
AIOPS_AGENT_EVIDENCE_BASE_URL=http://localhost:8080/internal/agent/evidence
AIOPS_AGENT_EVIDENCE_INTERNAL_TOKEN=dev-internal-token
```
````

Evidence sections:

```json
{
  "metrics": {
    "available": true,
    "series": []
  },
  "logs": {
    "available": true,
    "patterns": []
  },
  "changes": {
    "available": true,
    "events": []
  }
}
```

If evidence query fails, diagnosis still succeeds with evidence unavailable markers.

````

---

# 19. 验证命令

## Python

```powershell
cd apps/aiops-agent
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[test]"
pytest
````

## Java

```powershell
mvn -pl modules/aiops-evidence -am test
mvn -pl apps/aiops-server -am test
```

## 全量

```powershell
mvn test
```

## Docker

```powershell
docker compose -f infra/docker-compose.yml up -d
```

---

# 20. Phase4.3 验收标准

```txt
1. Python pytest 全部通过。
2. Java aiops-evidence 单测全部通过。
3. /v1/diagnose 在 evidence disabled 时仍可正常诊断。
4. /v1/diagnose 在 evidence enabled 时会调用 Java internal evidence API。
5. Java /internal/agent/evidence/query 校验 internal token。
6. metrics/logs/changes 任一查询失败不影响 AI 诊断主流程。
7. DiagnoseResponse.raw 中包含 metrics/logs/changes。
8. LLM prompt 中包含 metrics/logs/changes。
9. LLM 不允许编造不存在的证据。
10. Safety Boundary 仍然生效。
```

---

# 21. 完成后路线

```txt
Phase4.0  LangGraph deterministic Agent
Phase4.1  Contract + Safety Boundary
Phase4.2  OpenAI-compatible LLM Provider
Phase4.3  Metrics / Logs / Changes Evidence Tools
Phase4.4  Agent Run Trace + Eval
Phase4.5  jOOQ Persistence Refactor
Phase5.0  Runbook Recommendation
Phase5.1  AutomationPlan + Approval
Phase5.2  aiops-runner
```

Phase4.3 完成后，AegisOps 的 AI 诊断就不再只是 “Incident + Alert + RCA 文本总结”，而是开始具备真实证据链：

```txt
Incident
  + Alert
  + RCA
  + Metrics
  + Logs
  + Changes
  = Evidence-backed AI Diagnosis
```
