---
title: Phase2：Incident 聚合与事故中心
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---

# Phase2：Incident 聚合与事故中心

下面按你要求：**不提供压缩包、不提供 patch 文件，直接用 Markdown 给出 Phase2 的详细设计与完整代码**。

基于当前 `mvp` 分支，`incident / incident_event / incident_timeline` 表已经在 Phase0 建好，`alert_event` 也已经具备 `fingerprint / asset_id / starts_at / severity / status` 等字段，Phase1 又把 Zabbix Problem 同步成了 `alert_event`，所以 Phase2 可以直接围绕 **AlertEvent → Incident 聚合 → 时间线 → 详情页** 实现，不需要推翻现有结构。 当前 `aiops-incident` 模块还只有一个简单列表接口，因此 Phase2 主要补聚合服务、详情接口、状态流转、前端展示和测试。

# Phase2：Incident 聚合与事故中心

## 1. Phase2 目标

Phase2 的目标是把 Phase1 同步进来的 `alert_event` 自动聚合成 `incident`。

本阶段完成：

```txt
AlertEvent 聚合为 Incident
Incident aggregation_key
同 fingerprint 告警合并
IncidentEvent 关联
IncidentTimeline 写入
Incident 列表增强
Incident 详情接口
Incident 关联告警接口
Incident 时间线接口
Incident 状态流转
前端 Incident 聚合按钮
前端 Incident 列表与详情
完整单元测试
```

本阶段不做：

```txt
RCA 规则引擎
AI 诊断
Runbook 推荐
Ansible 执行
复杂拓扑关联
告警风暴高级降噪
```

这些留到 Phase3+。

---

## 2. 设计原则

### 2.1 聚合键

Phase1 已经把 Zabbix Problem 写成 `alert_event`，并且 fingerprint 使用 Zabbix objectId 优先，这正好适合作为 Phase2 聚合键。

Phase2 聚合规则：

```txt
aggregation_key = source + ":" + fingerprint
```

如果 fingerprint 缺失，则 fallback：

```txt
aggregation_key = source + ":" + asset_id + ":" + normalized_title
```

### 2.2 Active Incident

只有下面状态算活跃事故：

```txt
open
investigating
mitigating
```

如果同一个 `aggregation_key` 已经存在活跃 Incident，新告警追加进去。

如果之前的 Incident 已经：

```txt
resolved
closed
ignored
```

则同一个 `aggregation_key` 再次出现时，允许创建新的 Incident。

### 2.3 告警只属于一个 Incident

单条 `alert_event` 只应该被一个 Incident 关联。

通过数据库唯一索引保证：

```txt
incident_event(event_type, event_id) where event_type = 'alert'
```

### 2.4 Incident 时间线

聚合时为每条告警写入一条时间线：

```txt
event_type = alert_linked
source = system
```

状态变更时写入：

```txt
event_type = status_changed
source = user
```

---

# 3. 文件变更清单

## 新增文件

```txt
apps/aiops-server/src/main/resources/db/migration/V3__phase2_incident_aggregation.sql

modules/aiops-incident/src/main/java/io/aegisops/incident/AlertCandidate.java
modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentAggregateRequest.java
modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentAggregationPolicy.java
modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentAggregationResponse.java
modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentAlertRecord.java
modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentCreateCommand.java
modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentDetailRecord.java
modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentRepository.java
modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentService.java
modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentSeverity.java
modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentStatusRequest.java
modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentSummaryRecord.java
modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentTimelineRecord.java
modules/aiops-incident/src/main/java/io/aegisops/incident/JdbcIncidentRepository.java
modules/aiops-incident/src/main/java/io/aegisops/incident/TimelineCreateCommand.java

modules/aiops-incident/src/test/java/io/aegisops/incident/IncidentAggregationPolicyTest.java
modules/aiops-incident/src/test/java/io/aegisops/incident/IncidentServiceTest.java
modules/aiops-incident/src/test/java/io/aegisops/incident/IncidentSeverityTest.java
```

## 替换文件

```txt
modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentController.java
modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentRecord.java

web/console/src/api/client.ts
web/console/src/hooks/usePhase1Queries.ts
web/console/src/pages/DashboardPage.tsx
```

---

# 4. 数据库迁移

## `apps/aiops-server/src/main/resources/db/migration/V3__phase2_incident_aggregation.sql`

```sql
alter table incident
  add column if not exists aggregation_key varchar(512);

alter table incident
  add column if not exists last_seen_at timestamptz;

alter table incident
  add column if not exists alert_count integer not null default 0;

create index if not exists idx_incident_tenant_status_started
  on incident(tenant_id, status, started_at desc);

create index if not exists idx_incident_tenant_aggregation_key
  on incident(tenant_id, aggregation_key);

create unique index if not exists uq_incident_active_aggregation_key
  on incident(tenant_id, aggregation_key)
  where aggregation_key is not null
    and status in ('open', 'investigating', 'mitigating');

create index if not exists idx_incident_event_incident
  on incident_event(incident_id);

create unique index if not exists uq_incident_event_incident_event
  on incident_event(incident_id, event_type, event_id);

create unique index if not exists uq_incident_event_alert_once
  on incident_event(event_type, event_id)
  where event_type = 'alert';

create index if not exists idx_incident_timeline_incident_time
  on incident_timeline(incident_id, event_time desc);

create index if not exists idx_alert_event_tenant_status_fingerprint
  on alert_event(tenant_id, status, fingerprint, starts_at desc);
```

---

# 5. 后端代码

## `modules/aiops-incident/src/main/java/io/aegisops/incident/AlertCandidate.java`

```java
package io.aegisops.incident;

import java.time.OffsetDateTime;

public record AlertCandidate(
        String id,
        String tenantId,
        String source,
        String sourceEventId,
        String severity,
        String title,
        String description,
        String assetId,
        String entityType,
        String entityName,
        String fingerprint,
        OffsetDateTime startsAt,
        OffsetDateTime createdAt
) {}
```

---

## `modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentAggregateRequest.java`

```java
package io.aegisops.incident;

public record IncidentAggregateRequest(
        Integer windowMinutes,
        Integer limit
) {
    public int normalizedWindowMinutes() {
        if (windowMinutes == null || windowMinutes <= 0) {
            return 60 * 24;
        }

        return Math.min(windowMinutes, 60 * 24 * 7);
    }

    public int normalizedLimit() {
        if (limit == null || limit <= 0) {
            return 500;
        }

        return Math.min(limit, 5000);
    }
}
```

---

## `modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentAggregationResponse.java`

```java
package io.aegisops.incident;

public record IncidentAggregationResponse(
        int scannedAlerts,
        int groups,
        int incidentsCreated,
        int incidentsUpdated,
        int alertsLinked
) {}
```

---

## `modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentSeverity.java`

```java
package io.aegisops.incident;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

public final class IncidentSeverity {
    private static final Map<String, Integer> WEIGHTS = Map.of(
            "info", 10,
            "low", 20,
            "warning", 30,
            "critical", 40,
            "disaster", 50
    );

    private IncidentSeverity() {}

    public static String normalize(String severity) {
        if (severity == null || severity.isBlank()) {
            return "info";
        }

        String normalized = severity.trim().toLowerCase(Locale.ROOT);
        return WEIGHTS.containsKey(normalized) ? normalized : "info";
    }

    public static int weight(String severity) {
        return WEIGHTS.getOrDefault(normalize(severity), 10);
    }

    public static String max(String left, String right) {
        return weight(left) >= weight(right) ? normalize(left) : normalize(right);
    }

    public static String max(Collection<String> severities) {
        return severities.stream()
                .filter(severity -> severity != null && !severity.isBlank())
                .map(IncidentSeverity::normalize)
                .max(Comparator.comparingInt(IncidentSeverity::weight))
                .orElse("info");
    }
}
```

---

## `modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentAggregationPolicy.java`

```java
package io.aegisops.incident;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class IncidentAggregationPolicy {
    public String aggregationKey(AlertCandidate alert) {
        String source = nonBlank(alert.source(), "unknown");

        if (alert.fingerprint() != null && !alert.fingerprint().isBlank()) {
            return source + ":" + alert.fingerprint().trim();
        }

        String assetPart = nonBlank(alert.assetId(), "no-asset");
        String titlePart = normalizeTitle(alert.title());
        return source + ":" + assetPart + ":" + titlePart;
    }

    public String title(String aggregationKey, List<AlertCandidate> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return "Incident " + aggregationKey;
        }

        String firstTitle = alerts.get(0).title();
        boolean sameTitle = alerts.stream().allMatch(alert -> Objects.equals(firstTitle, alert.title()));

        if (sameTitle && firstTitle != null && !firstTitle.isBlank()) {
            return firstTitle;
        }

        String entity = alerts.stream()
                .map(AlertCandidate::entityName)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("related assets");

        return alerts.size() + " related alerts on " + entity;
    }

    public String summary(String aggregationKey, List<AlertCandidate> alerts) {
        String severity = highestSeverity(alerts);
        return "Aggregated " + alerts.size()
                + " alert(s), severity=" + severity
                + ", aggregationKey=" + aggregationKey;
    }

    public String highestSeverity(List<AlertCandidate> alerts) {
        return IncidentSeverity.max(alerts.stream().map(AlertCandidate::severity).toList());
    }

    public String primaryAssetId(List<AlertCandidate> alerts) {
        return alerts.stream()
                .map(AlertCandidate::assetId)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    public OffsetDateTime firstStartedAt(List<AlertCandidate> alerts) {
        return alerts.stream()
                .map(AlertCandidate::startsAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(OffsetDateTime.now());
    }

    public OffsetDateTime lastSeenAt(List<AlertCandidate> alerts) {
        return alerts.stream()
                .map(AlertCandidate::startsAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(OffsetDateTime.now());
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "untitled";
        }

        return title.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
```

---

## `modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentCreateCommand.java`

```java
package io.aegisops.incident;

import java.time.OffsetDateTime;

public record IncidentCreateCommand(
        String id,
        String tenantId,
        String title,
        String summary,
        String severity,
        String source,
        String primaryAssetId,
        String aggregationKey,
        int alertCount,
        OffsetDateTime startedAt,
        OffsetDateTime detectedAt,
        OffsetDateTime lastSeenAt
) {}
```

---

## `modules/aiops-incident/src/main/java/io/aegisops/incident/TimelineCreateCommand.java`

```java
package io.aegisops.incident;

import java.time.OffsetDateTime;

public record TimelineCreateCommand(
        String id,
        String incidentId,
        OffsetDateTime eventTime,
        String eventType,
        String title,
        String description,
        String source,
        String payloadJson
) {}
```

---

## `modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentSummaryRecord.java`

```java
package io.aegisops.incident;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record IncidentSummaryRecord(
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
        BigDecimal impactScore,
        OffsetDateTime startedAt,
        OffsetDateTime detectedAt,
        OffsetDateTime lastSeenAt,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
```

---

## `modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentRecord.java`

替换原文件。

```java
package io.aegisops.incident;

import java.time.OffsetDateTime;

public record IncidentRecord(
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
        OffsetDateTime startedAt,
        OffsetDateTime detectedAt,
        OffsetDateTime lastSeenAt,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static IncidentRecord from(IncidentSummaryRecord record) {
        return new IncidentRecord(
                record.id(),
                record.tenantId(),
                record.title(),
                record.summary(),
                record.severity(),
                record.status(),
                record.source(),
                record.primaryAssetId(),
                record.aggregationKey(),
                record.alertCount(),
                record.startedAt(),
                record.detectedAt(),
                record.lastSeenAt(),
                record.resolvedAt(),
                record.createdAt(),
                record.updatedAt()
        );
    }
}
```

---

## `modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentDetailRecord.java`

```java
package io.aegisops.incident;

import java.util.List;

public record IncidentDetailRecord(
        IncidentRecord incident,
        List<IncidentAlertRecord> alerts,
        List<IncidentTimelineRecord> timeline
) {}
```

---

## `modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentAlertRecord.java`

```java
package io.aegisops.incident;

import java.time.OffsetDateTime;

public record IncidentAlertRecord(
        String id,
        String source,
        String sourceEventId,
        String severity,
        String title,
        String status,
        String assetId,
        String entityName,
        String fingerprint,
        OffsetDateTime startsAt,
        String relationType
) {}
```

---

## `modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentTimelineRecord.java`

```java
package io.aegisops.incident;

import java.time.OffsetDateTime;

public record IncidentTimelineRecord(
        String id,
        OffsetDateTime eventTime,
        String eventType,
        String title,
        String description,
        String source,
        String payloadJson
) {}
```

---

## `modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentStatusRequest.java`

```java
package io.aegisops.incident;

public record IncidentStatusRequest(
        String status,
        String note
) {}
```

---

## `modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentRepository.java`

```java
package io.aegisops.incident;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface IncidentRepository {
    List<AlertCandidate> findOpenAlertCandidates(String tenantId, OffsetDateTime since, int limit);

    List<IncidentSummaryRecord> listIncidents(String tenantId, int limit);

    Optional<IncidentSummaryRecord> findIncident(String tenantId, String incidentId);

    Optional<IncidentSummaryRecord> findActiveIncidentByAggregationKey(String tenantId, String aggregationKey);

    void insertIncident(IncidentCreateCommand command);

    void updateIncidentAggregation(
            String tenantId,
            String incidentId,
            String title,
            String summary,
            String severity,
            int alertCount,
            OffsetDateTime lastSeenAt
    );

    void linkAlert(
            String id,
            String incidentId,
            String alertId,
            String relationType,
            OffsetDateTime occurredAt
    );

    void addTimeline(TimelineCreateCommand command);

    List<IncidentAlertRecord> listIncidentAlerts(String tenantId, String incidentId);

    List<IncidentTimelineRecord> listTimeline(String tenantId, String incidentId);

    int countLinkedAlerts(String incidentId);

    void updateStatus(String tenantId, String incidentId, String status, boolean terminal);
}
```

---

## `modules/aiops-incident/src/main/java/io/aegisops/incident/JdbcIncidentRepository.java`

```java
package io.aegisops.incident;

import io.aegisops.common.exception.AppException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcIncidentRepository implements IncidentRepository {
    private final JdbcTemplate jdbc;

    public JdbcIncidentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AlertCandidate> findOpenAlertCandidates(String tenantId, OffsetDateTime since, int limit) {
        return jdbc.query("""
                select id, tenant_id, source, source_event_id, severity, title, description,
                       asset_id, entity_type, entity_name, fingerprint, starts_at, created_at
                from alert_event a
                where tenant_id = ?
                  and status = 'open'
                  and starts_at >= ?
                  and not exists (
                    select 1 from incident_event ie
                    where ie.event_type = 'alert'
                      and ie.event_id = a.id
                  )
                order by starts_at asc
                limit ?
                """, (rs, rowNum) -> new AlertCandidate(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("source"),
                rs.getString("source_event_id"),
                rs.getString("severity"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("asset_id"),
                rs.getString("entity_type"),
                rs.getString("entity_name"),
                rs.getString("fingerprint"),
                rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class)
        ), tenantId, since, limit);
    }

    @Override
    public List<IncidentSummaryRecord> listIncidents(String tenantId, int limit) {
        return jdbc.query("""
                select id, tenant_id, title, summary, severity, status, source, primary_asset_id,
                       aggregation_key, alert_count, impact_score, started_at, detected_at,
                       last_seen_at, resolved_at, created_at, updated_at
                from incident
                where tenant_id = ?
                order by started_at desc
                limit ?
                """, (rs, rowNum) -> IncidentRows.summary(rs), tenantId, limit);
    }

    @Override
    public Optional<IncidentSummaryRecord> findIncident(String tenantId, String incidentId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select id, tenant_id, title, summary, severity, status, source, primary_asset_id,
                           aggregation_key, alert_count, impact_score, started_at, detected_at,
                           last_seen_at, resolved_at, created_at, updated_at
                    from incident
                    where tenant_id = ? and id = ?
                    """, (rs, rowNum) -> IncidentRows.summary(rs), tenantId, incidentId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<IncidentSummaryRecord> findActiveIncidentByAggregationKey(String tenantId, String aggregationKey) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select id, tenant_id, title, summary, severity, status, source, primary_asset_id,
                           aggregation_key, alert_count, impact_score, started_at, detected_at,
                           last_seen_at, resolved_at, created_at, updated_at
                    from incident
                    where tenant_id = ?
                      and aggregation_key = ?
                      and status in ('open', 'investigating', 'mitigating')
                    order by started_at desc
                    limit 1
                    """, (rs, rowNum) -> IncidentRows.summary(rs), tenantId, aggregationKey));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void insertIncident(IncidentCreateCommand command) {
        jdbc.update("""
                insert into incident(id, tenant_id, title, summary, severity, status, source, primary_asset_id,
                                     aggregation_key, alert_count, impact_score, started_at, detected_at,
                                     last_seen_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'open', ?, ?, ?, ?, 0, ?, ?, ?, now(), now())
                """,
                command.id(),
                command.tenantId(),
                command.title(),
                command.summary(),
                command.severity(),
                command.source(),
                command.primaryAssetId(),
                command.aggregationKey(),
                command.alertCount(),
                command.startedAt(),
                command.detectedAt(),
                command.lastSeenAt()
        );
    }

    @Override
    public void updateIncidentAggregation(
            String tenantId,
            String incidentId,
            String title,
            String summary,
            String severity,
            int alertCount,
            OffsetDateTime lastSeenAt
    ) {
        jdbc.update("""
                update incident
                set title = ?,
                    summary = ?,
                    severity = ?,
                    alert_count = ?,
                    last_seen_at = ?,
                    updated_at = now()
                where tenant_id = ? and id = ?
                """, title, summary, severity, alertCount, lastSeenAt, tenantId, incidentId);
    }

    @Override
    public void linkAlert(String id, String incidentId, String alertId, String relationType, OffsetDateTime occurredAt) {
        jdbc.update("""
                insert into incident_event(id, incident_id, event_type, event_id, relation_type, occurred_at)
                values (?, ?, 'alert', ?, ?, ?)
                on conflict do nothing
                """, id, incidentId, alertId, relationType, occurredAt);
    }

    @Override
    public void addTimeline(TimelineCreateCommand command) {
        jdbc.update("""
                insert into incident_timeline(id, incident_id, event_time, event_type, title, description, source, payload)
                values (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """,
                command.id(),
                command.incidentId(),
                command.eventTime(),
                command.eventType(),
                command.title(),
                command.description(),
                command.source(),
                command.payloadJson()
        );
    }

    @Override
    public List<IncidentAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
        ensureIncidentBelongsToTenant(tenantId, incidentId);

        return jdbc.query("""
                select a.id, a.source, a.source_event_id, a.severity, a.title, a.status,
                       a.asset_id, a.entity_name, a.fingerprint, a.starts_at, ie.relation_type
                from incident_event ie
                join alert_event a on a.id = ie.event_id
                where ie.incident_id = ?
                  and ie.event_type = 'alert'
                order by a.starts_at asc
                """, (rs, rowNum) -> new IncidentAlertRecord(
                rs.getString("id"),
                rs.getString("source"),
                rs.getString("source_event_id"),
                rs.getString("severity"),
                rs.getString("title"),
                rs.getString("status"),
                rs.getString("asset_id"),
                rs.getString("entity_name"),
                rs.getString("fingerprint"),
                rs.getObject("starts_at", OffsetDateTime.class),
                rs.getString("relation_type")
        ), incidentId);
    }

    @Override
    public List<IncidentTimelineRecord> listTimeline(String tenantId, String incidentId) {
        ensureIncidentBelongsToTenant(tenantId, incidentId);

        return jdbc.query("""
                select id, event_time, event_type, title, description, source, payload::text
                from incident_timeline
                where incident_id = ?
                order by event_time asc
                """, (rs, rowNum) -> new IncidentTimelineRecord(
                rs.getString("id"),
                rs.getObject("event_time", OffsetDateTime.class),
                rs.getString("event_type"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("source"),
                rs.getString("payload")
        ), incidentId);
    }

    @Override
    public int countLinkedAlerts(String incidentId) {
        Integer count = jdbc.queryForObject("""
                select count(*)
                from incident_event
                where incident_id = ?
                  and event_type = 'alert'
                """, Integer.class, incidentId);

        return count == null ? 0 : count;
    }

    @Override
    public void updateStatus(String tenantId, String incidentId, String status, boolean terminal) {
        int updated = jdbc.update("""
                update incident
                set status = ?,
                    resolved_at = case when ? then coalesce(resolved_at, now()) else null end,
                    updated_at = now()
                where tenant_id = ? and id = ?
                """, status, terminal, tenantId, incidentId);

        if (updated == 0) {
            throw new AppException("INCIDENT_NOT_FOUND", "Incident not found");
        }
    }

    private void ensureIncidentBelongsToTenant(String tenantId, String incidentId) {
        Boolean exists = jdbc.queryForObject("""
                select exists(select 1 from incident where tenant_id = ? and id = ?)
                """, Boolean.class, tenantId, incidentId);

        if (!Boolean.TRUE.equals(exists)) {
            throw new AppException("INCIDENT_NOT_FOUND", "Incident not found");
        }
    }
}
```

---

## `modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentRows.java`

```java
package io.aegisops.incident;

import java.sql.ResultSet;
import java.sql.SQLException;

final class IncidentRows {
    private IncidentRows() {}

    static IncidentSummaryRecord summary(ResultSet rs) throws SQLException {
        return new IncidentSummaryRecord(
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
                rs.getBigDecimal("impact_score"),
                rs.getObject("started_at", java.time.OffsetDateTime.class),
                rs.getObject("detected_at", java.time.OffsetDateTime.class),
                rs.getObject("last_seen_at", java.time.OffsetDateTime.class),
                rs.getObject("resolved_at", java.time.OffsetDateTime.class),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }
}
```

---

## `modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentService.java`

```java
package io.aegisops.incident;

import io.aegisops.common.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class IncidentService {
    private static final List<String> ALLOWED_STATUSES = List.of(
            "open",
            "investigating",
            "mitigating",
            "resolved",
            "closed",
            "ignored"
    );

    private static final List<String> TERMINAL_STATUSES = List.of(
            "resolved",
            "closed",
            "ignored"
    );

    private final IncidentRepository repository;
    private final IncidentAggregationPolicy policy;

    public IncidentService(IncidentRepository repository, IncidentAggregationPolicy policy) {
        this.repository = repository;
        this.policy = policy;
    }

    public List<IncidentRecord> list(String tenantId) {
        return repository.listIncidents(tenantId, 100)
                .stream()
                .map(IncidentRecord::from)
                .toList();
    }

    public IncidentDetailRecord detail(String tenantId, String incidentId) {
        IncidentRecord incident = repository.findIncident(tenantId, incidentId)
                .map(IncidentRecord::from)
                .orElseThrow(() -> new AppException("INCIDENT_NOT_FOUND", "Incident not found"));

        return new IncidentDetailRecord(
                incident,
                repository.listIncidentAlerts(tenantId, incidentId),
                repository.listTimeline(tenantId, incidentId)
        );
    }

    public List<IncidentAlertRecord> alerts(String tenantId, String incidentId) {
        ensureIncidentExists(tenantId, incidentId);
        return repository.listIncidentAlerts(tenantId, incidentId);
    }

    public List<IncidentTimelineRecord> timeline(String tenantId, String incidentId) {
        ensureIncidentExists(tenantId, incidentId);
        return repository.listTimeline(tenantId, incidentId);
    }

    @Transactional
    public IncidentAggregationResponse aggregateOpenAlerts(String tenantId, IncidentAggregateRequest request) {
        IncidentAggregateRequest normalizedRequest = request == null ? new IncidentAggregateRequest(null, null) : request;

        OffsetDateTime since = OffsetDateTime.now().minusMinutes(normalizedRequest.normalizedWindowMinutes());
        List<AlertCandidate> candidates = repository.findOpenAlertCandidates(
                tenantId,
                since,
                normalizedRequest.normalizedLimit()
        );

        Map<String, List<AlertCandidate>> groups = candidates.stream()
                .collect(Collectors.groupingBy(
                        policy::aggregationKey,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        int incidentsCreated = 0;
        int incidentsUpdated = 0;
        int alertsLinked = 0;

        for (Map.Entry<String, List<AlertCandidate>> entry : groups.entrySet()) {
            String aggregationKey = entry.getKey();
            List<AlertCandidate> alerts = entry.getValue();

            if (alerts.isEmpty()) {
                continue;
            }

            var existingIncident = repository.findActiveIncidentByAggregationKey(tenantId, aggregationKey);
            String groupSeverity = policy.highestSeverity(alerts);
            String incidentId;
            int baseAlertCount;

            if (existingIncident.isPresent()) {
                IncidentSummaryRecord existing = existingIncident.get();
                incidentId = existing.id();
                baseAlertCount = existing.alertCount();
                incidentsUpdated++;
            } else {
                incidentId = newId("inc");
                baseAlertCount = 0;

                repository.insertIncident(new IncidentCreateCommand(
                        incidentId,
                        tenantId,
                        policy.title(aggregationKey, alerts),
                        policy.summary(aggregationKey, alerts),
                        groupSeverity,
                        "system",
                        policy.primaryAssetId(alerts),
                        aggregationKey,
                        alerts.size(),
                        policy.firstStartedAt(alerts),
                        OffsetDateTime.now(),
                        policy.lastSeenAt(alerts)
                ));

                incidentsCreated++;
            }

            int index = 0;
            for (AlertCandidate alert : alerts) {
                repository.linkAlert(
                        newId("ie"),
                        incidentId,
                        alert.id(),
                        index == 0 ? "primary" : "related",
                        alert.startsAt()
                );

                repository.addTimeline(new TimelineCreateCommand(
                        newId("tl"),
                        incidentId,
                        alert.startsAt(),
                        "alert_linked",
                        alert.title(),
                        alert.description(),
                        "system",
                        alertPayload(alert, aggregationKey)
                ));

                alertsLinked++;
                index++;
            }

            int actualAlertCount = baseAlertCount + alerts.size();
            String mergedSeverity = existingIncident
                    .map(existing -> IncidentSeverity.max(existing.severity(), groupSeverity))
                    .orElse(groupSeverity);

            repository.updateIncidentAggregation(
                    tenantId,
                    incidentId,
                    policy.title(aggregationKey, alerts),
                    policy.summary(aggregationKey, alerts),
                    mergedSeverity,
                    actualAlertCount,
                    policy.lastSeenAt(alerts)
            );
        }

        return new IncidentAggregationResponse(
                candidates.size(),
                groups.size(),
                incidentsCreated,
                incidentsUpdated,
                alertsLinked
        );
    }

    @Transactional
    public IncidentRecord updateStatus(String tenantId, String incidentId, IncidentStatusRequest request) {
        ensureIncidentExists(tenantId, incidentId);

        String status = normalizeStatus(request == null ? null : request.status());
        boolean terminal = TERMINAL_STATUSES.contains(status);

        repository.updateStatus(tenantId, incidentId, status, terminal);
        repository.addTimeline(new TimelineCreateCommand(
                newId("tl"),
                incidentId,
                OffsetDateTime.now(),
                "status_changed",
                "Incident status changed to " + status,
                request == null ? null : request.note(),
                "user",
                "{\"status\":\"" + status + "\"}"
        ));

        return repository.findIncident(tenantId, incidentId)
                .map(IncidentRecord::from)
                .orElseThrow(() -> new AppException("INCIDENT_NOT_FOUND", "Incident not found"));
    }

    public IncidentRecord resolve(String tenantId, String incidentId) {
        return updateStatus(tenantId, incidentId, new IncidentStatusRequest("resolved", "Resolved by user"));
    }

    public IncidentRecord close(String tenantId, String incidentId) {
        return updateStatus(tenantId, incidentId, new IncidentStatusRequest("closed", "Closed by user"));
    }

    private void ensureIncidentExists(String tenantId, String incidentId) {
        if (repository.findIncident(tenantId, incidentId).isEmpty()) {
            throw new AppException("INCIDENT_NOT_FOUND", "Incident not found");
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new AppException("INCIDENT_STATUS_INVALID", "Incident status is required");
        }

        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new AppException("INCIDENT_STATUS_INVALID", "Unsupported incident status: " + status);
        }

        return normalized;
    }

    private String alertPayload(AlertCandidate alert, String aggregationKey) {
        return """
                {
                  "alertId": "%s",
                  "source": "%s",
                  "sourceEventId": "%s",
                  "fingerprint": "%s",
                  "aggregationKey": "%s"
                }
                """.formatted(
                escapeJson(alert.id()),
                escapeJson(alert.source()),
                escapeJson(alert.sourceEventId()),
                escapeJson(alert.fingerprint()),
                escapeJson(aggregationKey)
        );
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
```

---

## `modules/aiops-incident/src/main/java/io/aegisops/incident/IncidentController.java`

替换原文件。

```java
package io.aegisops.incident;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {
    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    public ApiResponse<List<IncidentRecord>> list() {
        return ApiResponse.ok(incidentService.list(TenantContext.requireTenantId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<IncidentDetailRecord> detail(@PathVariable String id) {
        return ApiResponse.ok(incidentService.detail(TenantContext.requireTenantId(), id));
    }

    @GetMapping("/{id}/alerts")
    public ApiResponse<List<IncidentAlertRecord>> alerts(@PathVariable String id) {
        return ApiResponse.ok(incidentService.alerts(TenantContext.requireTenantId(), id));
    }

    @GetMapping("/{id}/timeline")
    public ApiResponse<List<IncidentTimelineRecord>> timeline(@PathVariable String id) {
        return ApiResponse.ok(incidentService.timeline(TenantContext.requireTenantId(), id));
    }

    @PostMapping("/aggregate")
    public ApiResponse<IncidentAggregationResponse> aggregate(@RequestBody(required = false) IncidentAggregateRequest request) {
        return ApiResponse.ok(incidentService.aggregateOpenAlerts(TenantContext.requireTenantId(), request));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<IncidentRecord> updateStatus(@PathVariable String id, @RequestBody IncidentStatusRequest request) {
        return ApiResponse.ok(incidentService.updateStatus(TenantContext.requireTenantId(), id, request));
    }

    @PostMapping("/{id}/resolve")
    public ApiResponse<IncidentRecord> resolve(@PathVariable String id) {
        return ApiResponse.ok(incidentService.resolve(TenantContext.requireTenantId(), id));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<IncidentRecord> close(@PathVariable String id) {
        return ApiResponse.ok(incidentService.close(TenantContext.requireTenantId(), id));
    }
}
```

---

# 6. 单元测试

## `modules/aiops-incident/src/test/java/io/aegisops/incident/IncidentSeverityTest.java`

```java
package io.aegisops.incident;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IncidentSeverityTest {
    @Test
    void normalizeUnknownSeverityToInfo() {
        assertEquals("info", IncidentSeverity.normalize(null));
        assertEquals("info", IncidentSeverity.normalize(""));
        assertEquals("info", IncidentSeverity.normalize("unknown"));
    }

    @Test
    void maxReturnsHigherSeverity() {
        assertEquals("critical", IncidentSeverity.max("warning", "critical"));
        assertEquals("disaster", IncidentSeverity.max("disaster", "critical"));
        assertEquals("warning", IncidentSeverity.max("info", "warning"));
    }

    @Test
    void maxCollectionReturnsHighestSeverity() {
        assertEquals("disaster", IncidentSeverity.max(List.of("info", "warning", "disaster", "critical")));
    }
}
```

---

## `modules/aiops-incident/src/test/java/io/aegisops/incident/IncidentAggregationPolicyTest.java`

```java
package io.aegisops.incident;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentAggregationPolicyTest {
    private final IncidentAggregationPolicy policy = new IncidentAggregationPolicy();

    @Test
    void usesFingerprintAsAggregationKey() {
        AlertCandidate alert = alert("a1", "zabbix", "critical", "CPU high", "asset_1", "zabbix:ds_1:trigger_1");

        assertEquals("zabbix:zabbix:ds_1:trigger_1", policy.aggregationKey(alert));
    }

    @Test
    void fallsBackToAssetAndNormalizedTitleWhenFingerprintMissing() {
        AlertCandidate alert = alert("a1", "webhook", "warning", "Disk Usage > 90%", "asset_1", "");

        assertEquals("webhook:asset_1:disk-usage-90", policy.aggregationKey(alert));
    }

    @Test
    void selectsHighestSeverity() {
        List<AlertCandidate> alerts = List.of(
                alert("a1", "zabbix", "warning", "CPU high", "asset_1", "fp"),
                alert("a2", "zabbix", "critical", "CPU high", "asset_1", "fp"),
                alert("a3", "zabbix", "info", "CPU high", "asset_1", "fp")
        );

        assertEquals("critical", policy.highestSeverity(alerts));
    }

    @Test
    void titleUsesSameAlertTitleWhenAllSame() {
        List<AlertCandidate> alerts = List.of(
                alert("a1", "zabbix", "warning", "CPU high", "asset_1", "fp"),
                alert("a2", "zabbix", "critical", "CPU high", "asset_1", "fp")
        );

        assertEquals("CPU high", policy.title("zabbix:fp", alerts));
    }

    @Test
    void summaryContainsAlertCountAndAggregationKey() {
        List<AlertCandidate> alerts = List.of(
                alert("a1", "zabbix", "warning", "CPU high", "asset_1", "fp"),
                alert("a2", "zabbix", "critical", "CPU high", "asset_1", "fp")
        );

        String summary = policy.summary("zabbix:fp", alerts);

        assertTrue(summary.contains("2 alert"));
        assertTrue(summary.contains("zabbix:fp"));
        assertTrue(summary.contains("critical"));
    }

    private AlertCandidate alert(String id, String source, String severity, String title, String assetId, String fingerprint) {
        return new AlertCandidate(
                id,
                "tenant_1",
                source,
                "source_" + id,
                severity,
                title,
                "desc " + id,
                assetId,
                "host",
                "host-1",
                fingerprint,
                OffsetDateTime.parse("2026-06-14T10:00:00+09:00"),
                OffsetDateTime.parse("2026-06-14T10:00:00+09:00")
        );
    }
}
```

---

## `modules/aiops-incident/src/test/java/io/aegisops/incident/IncidentServiceTest.java`

```java
package io.aegisops.incident;

import io.aegisops.common.exception.AppException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class IncidentServiceTest {
    @Test
    void aggregatesAlertsWithSameFingerprintIntoOneIncident() {
        FakeIncidentRepository repository = new FakeIncidentRepository();
        IncidentService service = new IncidentService(repository, new IncidentAggregationPolicy());

        repository.candidates.add(alert("alert_1", "critical", "CPU high", "fp_cpu"));
        repository.candidates.add(alert("alert_2", "warning", "CPU high", "fp_cpu"));

        IncidentAggregationResponse response = service.aggregateOpenAlerts(
                "tenant_1",
                new IncidentAggregateRequest(60, 100)
        );

        assertEquals(2, response.scannedAlerts());
        assertEquals(1, response.groups());
        assertEquals(1, response.incidentsCreated());
        assertEquals(0, response.incidentsUpdated());
        assertEquals(2, response.alertsLinked());

        assertEquals(1, repository.incidents.size());

        IncidentSummaryRecord incident = repository.incidents.values().iterator().next();
        assertEquals("critical", incident.severity());
        assertEquals(2, incident.alertCount());
        assertEquals("open", incident.status());
        assertEquals(2, repository.linkedAlerts.size());
        assertEquals(2, repository.timeline.size());
    }

    @Test
    void aggregatesDifferentFingerprintsIntoDifferentIncidents() {
        FakeIncidentRepository repository = new FakeIncidentRepository();
        IncidentService service = new IncidentService(repository, new IncidentAggregationPolicy());

        repository.candidates.add(alert("alert_1", "critical", "CPU high", "fp_cpu"));
        repository.candidates.add(alert("alert_2", "warning", "Disk high", "fp_disk"));

        IncidentAggregationResponse response = service.aggregateOpenAlerts(
                "tenant_1",
                new IncidentAggregateRequest(60, 100)
        );

        assertEquals(2, response.groups());
        assertEquals(2, response.incidentsCreated());
        assertEquals(2, repository.incidents.size());
    }

    @Test
    void reusesActiveIncidentByAggregationKey() {
        FakeIncidentRepository repository = new FakeIncidentRepository();
        IncidentService service = new IncidentService(repository, new IncidentAggregationPolicy());

        AlertCandidate existingAlert = alert("alert_old", "warning", "CPU high", "fp_cpu");
        String aggregationKey = new IncidentAggregationPolicy().aggregationKey(existingAlert);

        IncidentSummaryRecord existing = summary(
                "inc_existing",
                "tenant_1",
                "CPU high",
                "warning",
                "open",
                aggregationKey,
                1
        );

        repository.incidents.put(existing.id(), existing);
        repository.activeByAggregationKey.put(aggregationKey, existing);
        repository.candidates.add(alert("alert_new", "critical", "CPU high", "fp_cpu"));

        IncidentAggregationResponse response = service.aggregateOpenAlerts(
                "tenant_1",
                new IncidentAggregateRequest(60, 100)
        );

        assertEquals(0, response.incidentsCreated());
        assertEquals(1, response.incidentsUpdated());
        assertEquals(1, repository.incidents.size());

        IncidentSummaryRecord updated = repository.incidents.get("inc_existing");
        assertEquals("critical", updated.severity());
        assertEquals(2, updated.alertCount());
    }

    @Test
    void updateStatusWritesTimeline() {
        FakeIncidentRepository repository = new FakeIncidentRepository();
        IncidentService service = new IncidentService(repository, new IncidentAggregationPolicy());

        IncidentSummaryRecord incident = summary(
                "inc_1",
                "tenant_1",
                "CPU high",
                "critical",
                "open",
                "zabbix:fp_cpu",
                2
        );
        repository.incidents.put(incident.id(), incident);

        IncidentRecord updated = service.updateStatus(
                "tenant_1",
                "inc_1",
                new IncidentStatusRequest("resolved", "fixed")
        );

        assertEquals("resolved", updated.status());
        assertNotNull(updated.resolvedAt());
        assertEquals(1, repository.timeline.size());
        assertEquals("status_changed", repository.timeline.get(0).eventType());
    }

    @Test
    void updateStatusRejectsInvalidStatus() {
        FakeIncidentRepository repository = new FakeIncidentRepository();
        IncidentService service = new IncidentService(repository, new IncidentAggregationPolicy());

        repository.incidents.put("inc_1", summary(
                "inc_1",
                "tenant_1",
                "CPU high",
                "critical",
                "open",
                "zabbix:fp_cpu",
                1
        ));

        AppException ex = assertThrows(AppException.class, () ->
                service.updateStatus("tenant_1", "inc_1", new IncidentStatusRequest("bad", null))
        );

        assertEquals("INCIDENT_STATUS_INVALID", ex.errorCode());
    }

    private AlertCandidate alert(String id, String severity, String title, String fingerprint) {
        return new AlertCandidate(
                id,
                "tenant_1",
                "zabbix",
                "source_" + id,
                severity,
                title,
                "desc " + id,
                "asset_1",
                "host",
                "host-1",
                fingerprint,
                OffsetDateTime.parse("2026-06-14T10:00:00+09:00"),
                OffsetDateTime.parse("2026-06-14T10:00:00+09:00")
        );
    }

    private IncidentSummaryRecord summary(
            String id,
            String tenantId,
            String title,
            String severity,
            String status,
            String aggregationKey,
            int alertCount
    ) {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-14T10:00:00+09:00");
        return new IncidentSummaryRecord(
                id,
                tenantId,
                title,
                "summary",
                severity,
                status,
                "system",
                "asset_1",
                aggregationKey,
                alertCount,
                BigDecimal.ZERO,
                now,
                now,
                now,
                null,
                now,
                now
        );
    }

    private static final class FakeIncidentRepository implements IncidentRepository {
        final List<AlertCandidate> candidates = new ArrayList<>();
        final Map<String, IncidentSummaryRecord> incidents = new LinkedHashMap<>();
        final Map<String, IncidentSummaryRecord> activeByAggregationKey = new LinkedHashMap<>();
        final List<IncidentAlertRecord> linkedAlerts = new ArrayList<>();
        final List<IncidentTimelineRecord> timeline = new ArrayList<>();

        @Override
        public List<AlertCandidate> findOpenAlertCandidates(String tenantId, OffsetDateTime since, int limit) {
            return candidates.stream()
                    .filter(alert -> alert.tenantId().equals(tenantId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<IncidentSummaryRecord> listIncidents(String tenantId, int limit) {
            return incidents.values().stream()
                    .filter(incident -> incident.tenantId().equals(tenantId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<IncidentSummaryRecord> findIncident(String tenantId, String incidentId) {
            IncidentSummaryRecord incident = incidents.get(incidentId);
            if (incident == null || !incident.tenantId().equals(tenantId)) {
                return Optional.empty();
            }

            return Optional.of(incident);
        }

        @Override
        public Optional<IncidentSummaryRecord> findActiveIncidentByAggregationKey(String tenantId, String aggregationKey) {
            IncidentSummaryRecord incident = activeByAggregationKey.get(aggregationKey);
            if (incident == null || !incident.tenantId().equals(tenantId)) {
                return Optional.empty();
            }

            return Optional.of(incident);
        }

        @Override
        public void insertIncident(IncidentCreateCommand command) {
            IncidentSummaryRecord record = new IncidentSummaryRecord(
                    command.id(),
                    command.tenantId(),
                    command.title(),
                    command.summary(),
                    command.severity(),
                    "open",
                    command.source(),
                    command.primaryAssetId(),
                    command.aggregationKey(),
                    command.alertCount(),
                    BigDecimal.ZERO,
                    command.startedAt(),
                    command.detectedAt(),
                    command.lastSeenAt(),
                    null,
                    command.detectedAt(),
                    command.detectedAt()
            );

            incidents.put(record.id(), record);
            activeByAggregationKey.put(record.aggregationKey(), record);
        }

        @Override
        public void updateIncidentAggregation(
                String tenantId,
                String incidentId,
                String title,
                String summary,
                String severity,
                int alertCount,
                OffsetDateTime lastSeenAt
        ) {
            IncidentSummaryRecord old = incidents.get(incidentId);
            IncidentSummaryRecord updated = new IncidentSummaryRecord(
                    old.id(),
                    old.tenantId(),
                    title,
                    summary,
                    severity,
                    old.status(),
                    old.source(),
                    old.primaryAssetId(),
                    old.aggregationKey(),
                    alertCount,
                    old.impactScore(),
                    old.startedAt(),
                    old.detectedAt(),
                    lastSeenAt,
                    old.resolvedAt(),
                    old.createdAt(),
                    OffsetDateTime.now()
            );

            incidents.put(incidentId, updated);
            activeByAggregationKey.put(updated.aggregationKey(), updated);
        }

        @Override
        public void linkAlert(String id, String incidentId, String alertId, String relationType, OffsetDateTime occurredAt) {
            linkedAlerts.add(new IncidentAlertRecord(
                    alertId,
                    "zabbix",
                    "source_" + alertId,
                    "warning",
                    "alert " + alertId,
                    "open",
                    "asset_1",
                    "host-1",
                    "fp",
                    occurredAt,
                    relationType
            ));
        }

        @Override
        public void addTimeline(TimelineCreateCommand command) {
            timeline.add(new IncidentTimelineRecord(
                    command.id(),
                    command.eventTime(),
                    command.eventType(),
                    command.title(),
                    command.description(),
                    command.source(),
                    command.payloadJson()
            ));
        }

        @Override
        public List<IncidentAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
            return linkedAlerts;
        }

        @Override
        public List<IncidentTimelineRecord> listTimeline(String tenantId, String incidentId) {
            return timeline;
        }

        @Override
        public int countLinkedAlerts(String incidentId) {
            return linkedAlerts.size();
        }

        @Override
        public void updateStatus(String tenantId, String incidentId, String status, boolean terminal) {
            IncidentSummaryRecord old = incidents.get(incidentId);
            IncidentSummaryRecord updated = new IncidentSummaryRecord(
                    old.id(),
                    old.tenantId(),
                    old.title(),
                    old.summary(),
                    old.severity(),
                    status,
                    old.source(),
                    old.primaryAssetId(),
                    old.aggregationKey(),
                    old.alertCount(),
                    old.impactScore(),
                    old.startedAt(),
                    old.detectedAt(),
                    old.lastSeenAt(),
                    terminal ? OffsetDateTime.now() : null,
                    old.createdAt(),
                    OffsetDateTime.now()
            );

            incidents.put(incidentId, updated);
            if (List.of("open", "investigating", "mitigating").contains(status)) {
                activeByAggregationKey.put(updated.aggregationKey(), updated);
            } else {
                activeByAggregationKey.remove(updated.aggregationKey());
            }
        }
    }
}
```

---

# 7. 前端代码

## `web/console/src/api/client.ts`

替换原文件。

```ts
export type ApiResponse<T> = {
  success: boolean;
  data: T;
  errorCode?: string;
  message?: string;
  timestamp: string;
};

export type LoginResponse = {
  token: string;
  user: Me;
};

export type Me = {
  id: string;
  tenantId: string;
  username: string;
  displayName: string;
  roles: string[];
};

export type DataSourceRecord = {
  id: string;
  tenantId: string;
  type: string;
  name: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  lastSyncAt?: string;
};

export type CreateZabbixDataSourcePayload = {
  type: "zabbix";
  name: string;
  zabbix: {
    endpoint: string;
    username?: string;
    password?: string;
    apiToken?: string;
    connectTimeoutSeconds?: number;
    readTimeoutSeconds?: number;
  };
};

export type TestDataSourceResponse = {
  ok: boolean;
  message: string;
  version?: string;
};

export type SyncDataSourceResponse = {
  runId: string;
  status: string;
  hostsCreated: number;
  hostsUpdated: number;
  alertsCreated: number;
  alertsUpdated: number;
  message: string;
};

export type AssetRecord = {
  id: string;
  tenantId: string;
  assetType: string;
  name: string;
  displayName?: string;
  source: string;
  status: string;
  createdAt: string;
};

export type AlertEventRecord = {
  id: string;
  tenantId: string;
  source: string;
  severity: string;
  title: string;
  status: string;
  startsAt: string;
  createdAt: string;
};

export type IncidentRecord = {
  id: string;
  tenantId: string;
  title: string;
  summary?: string;
  severity: string;
  status: string;
  source: string;
  primaryAssetId?: string;
  aggregationKey?: string;
  alertCount: number;
  startedAt: string;
  detectedAt: string;
  lastSeenAt?: string;
  resolvedAt?: string;
  createdAt: string;
  updatedAt: string;
};

export type IncidentAlertRecord = {
  id: string;
  source: string;
  sourceEventId?: string;
  severity: string;
  title: string;
  status: string;
  assetId?: string;
  entityName?: string;
  fingerprint: string;
  startsAt: string;
  relationType: string;
};

export type IncidentTimelineRecord = {
  id: string;
  eventTime: string;
  eventType: string;
  title: string;
  description?: string;
  source: string;
  payloadJson: string;
};

export type IncidentDetailRecord = {
  incident: IncidentRecord;
  alerts: IncidentAlertRecord[];
  timeline: IncidentTimelineRecord[];
};

export type IncidentAggregationResponse = {
  scannedAlerts: number;
  groups: number;
  incidentsCreated: number;
  incidentsUpdated: number;
  alertsLinked: number;
};

const TOKEN_KEY = "aegisops_token";

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const token = getToken();
  const resp = await fetch(path, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init.headers || {}),
    },
  });

  const payload = await parseApiResponse<T>(resp);
  if (!resp.ok || !payload.success) {
    throw new Error(
      payload.message ||
        payload.errorCode ||
        `Request failed with status ${resp.status}`,
    );
  }
  return payload.data;
}

async function parseApiResponse<T>(resp: Response): Promise<ApiResponse<T>> {
  const contentType = resp.headers.get("content-type") || "";
  if (!contentType.includes("application/json")) {
    const text = await resp.text();
    return {
      success: false,
      data: undefined as T,
      errorCode: `HTTP_${resp.status}`,
      message: text || resp.statusText || "Non-JSON response",
      timestamp: new Date().toISOString(),
    };
  }
  return (await resp.json()) as ApiResponse<T>;
}

export function login(username: string, password: string) {
  return apiRequest<LoginResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
}

export function me() {
  return apiRequest<Me>("/api/auth/me");
}

export function overview() {
  return apiRequest<Record<string, number | string>>("/api/system/overview");
}

export function listDataSources() {
  return apiRequest<DataSourceRecord[]>("/api/datasources");
}

export function createZabbixDataSource(payload: CreateZabbixDataSourcePayload) {
  return apiRequest<DataSourceRecord>("/api/datasources", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function testDataSource(id: string) {
  return apiRequest<TestDataSourceResponse>(`/api/datasources/${id}/test`, {
    method: "POST",
  });
}

export function syncDataSource(id: string) {
  return apiRequest<SyncDataSourceResponse>(`/api/datasources/${id}/sync`, {
    method: "POST",
  });
}

export function listAssets() {
  return apiRequest<AssetRecord[]>("/api/assets");
}

export function listAlerts() {
  return apiRequest<AlertEventRecord[]>("/api/alerts");
}

export function listIncidents() {
  return apiRequest<IncidentRecord[]>("/api/incidents");
}

export function aggregateIncidents() {
  return apiRequest<IncidentAggregationResponse>("/api/incidents/aggregate", {
    method: "POST",
    body: JSON.stringify({
      windowMinutes: 1440,
      limit: 1000,
    }),
  });
}

export function getIncident(id: string) {
  return apiRequest<IncidentDetailRecord>(`/api/incidents/${id}`);
}

export function resolveIncident(id: string) {
  return apiRequest<IncidentRecord>(`/api/incidents/${id}/resolve`, {
    method: "POST",
  });
}

export function closeIncident(id: string) {
  return apiRequest<IncidentRecord>(`/api/incidents/${id}/close`, {
    method: "POST",
  });
}
```

---

## `web/console/src/hooks/usePhase1Queries.ts`

替换原文件。名字暂时不改，避免影响现有引用；Phase3 时可以统一改成 `useConsoleQueries`。

```ts
import { useCallback } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AlertEventRecord,
  AssetRecord,
  DataSourceRecord,
  IncidentRecord,
  listAlerts,
  listAssets,
  listDataSources,
  listIncidents,
  overview,
} from "../api/client";

type Overview = Record<string, number | string>;

const CONSOLE_QUERY_KEYS = [
  "overview",
  "datasources",
  "assets",
  "alerts",
  "incidents",
] as const;

export function usePhase1Queries() {
  const queryClient = useQueryClient();

  const overviewQuery = useQuery<Overview>({
    queryKey: ["overview"],
    queryFn: overview,
  });

  const datasourceQuery = useQuery<DataSourceRecord[]>({
    queryKey: ["datasources"],
    queryFn: listDataSources,
  });

  const assetQuery = useQuery<AssetRecord[]>({
    queryKey: ["assets"],
    queryFn: listAssets,
  });

  const alertQuery = useQuery<AlertEventRecord[]>({
    queryKey: ["alerts"],
    queryFn: listAlerts,
  });

  const incidentQuery = useQuery<IncidentRecord[]>({
    queryKey: ["incidents"],
    queryFn: listIncidents,
  });

  const invalidateAll = useCallback(async () => {
    await Promise.all(
      CONSOLE_QUERY_KEYS.map((key) =>
        queryClient.invalidateQueries({ queryKey: [key] }),
      ),
    );
  }, [queryClient]);

  return {
    overviewQuery,
    datasourceQuery,
    assetQuery,
    alertQuery,
    incidentQuery,
    invalidateAll,
  } as const;
}
```

---

## `web/console/src/pages/DashboardPage.tsx`

替换原文件。

```tsx
import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  aggregateIncidents,
  createZabbixDataSource,
  getIncident,
  resolveIncident,
  syncDataSource,
  testDataSource,
} from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { usePhase1Queries } from "../hooks/usePhase1Queries";

const cards = [
  ["tenants", "Tenants"],
  ["users", "Users"],
  ["assets", "Assets"],
  ["alerts", "Alerts"],
  ["incidents", "Incidents"],
];

export function DashboardPage() {
  const auth = useAuth();
  const queryClient = useQueryClient();
  const [message, setMessage] = useState("");
  const [selectedIncidentId, setSelectedIncidentId] = useState<string | null>(
    null,
  );
  const [form, setForm] = useState({
    name: "Local Zabbix",
    endpoint: "http://localhost:8081/api_jsonrpc.php",
    username: "Admin",
    password: "",
    apiToken: "",
  });

  const {
    overviewQuery,
    datasourceQuery,
    assetQuery,
    alertQuery,
    incidentQuery,
    invalidateAll,
  } = usePhase1Queries();

  const incidentDetailQuery = useQuery({
    queryKey: ["incident", selectedIncidentId],
    queryFn: () => getIncident(selectedIncidentId!),
    enabled: Boolean(selectedIncidentId),
  });

  const createMutation = useMutation({
    mutationFn: createZabbixDataSource,
    onSuccess: async (created) => {
      setMessage(`Datasource created: ${created.name}`);
      await invalidateAll();
    },
    onError: (error) => setMessage(String(error)),
  });

  const testMutation = useMutation({
    mutationFn: testDataSource,
    onSuccess: (result) => {
      setMessage(
        result.ok
          ? `Zabbix connected, version ${result.version}`
          : result.message,
      );
      invalidateAll();
    },
    onError: (error) => setMessage(String(error)),
  });

  const syncMutation = useMutation({
    mutationFn: syncDataSource,
    onSuccess: async (result) => {
      setMessage(
        `Sync ${result.status}: +${result.hostsCreated} hosts, +${result.alertsCreated} alerts`,
      );
      await invalidateAll();
    },
    onError: (error) => setMessage(String(error)),
  });

  const aggregateMutation = useMutation({
    mutationFn: aggregateIncidents,
    onSuccess: async (result) => {
      setMessage(
        `Aggregated ${result.scannedAlerts} alerts, created ${result.incidentsCreated}, updated ${result.incidentsUpdated}, linked ${result.alertsLinked}`,
      );
      await invalidateAll();
    },
    onError: (error) => setMessage(String(error)),
  });

  const resolveMutation = useMutation({
    mutationFn: resolveIncident,
    onSuccess: async () => {
      setMessage("Incident resolved");
      await invalidateAll();
      await queryClient.invalidateQueries({
        queryKey: ["incident", selectedIncidentId],
      });
    },
    onError: (error) => setMessage(String(error)),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    createMutation.mutate({
      type: "zabbix",
      name: form.name,
      zabbix: {
        endpoint: form.endpoint,
        username: form.username || undefined,
        password: form.password || undefined,
        apiToken: form.apiToken || undefined,
        connectTimeoutSeconds: 5,
        readTimeoutSeconds: 20,
      },
    });
  }

  return (
    <main className="min-h-screen bg-slate-100">
      <header className="border-b bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <div>
            <h1 className="text-xl font-bold">AegisOps Console</h1>
            <p className="text-sm text-slate-500">
              Phase2 Incident aggregation center
            </p>
          </div>
          <div className="flex items-center gap-4">
            <span className="text-sm text-slate-600">
              {auth.user?.displayName || "Admin"}
            </span>
            <button
              className="rounded-lg border px-3 py-1.5 text-sm"
              onClick={auth.logout}
            >
              Logout
            </button>
          </div>
        </div>
      </header>

      <section className="mx-auto max-w-6xl px-6 py-8">
        <div className="mb-6 rounded-2xl bg-white p-6 shadow-sm">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <h2 className="text-lg font-semibold">System Overview</h2>
              <p className="mt-1 text-sm text-slate-500">
                Phase2 已支持 Zabbix 告警同步、AlertEvent 聚合、Incident
                详情与时间线。
              </p>
            </div>
            <button
              className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
              disabled={aggregateMutation.isPending}
              onClick={() => aggregateMutation.mutate()}
            >
              {aggregateMutation.isPending
                ? "Aggregating..."
                : "Aggregate Incidents"}
            </button>
          </div>

          {message && (
            <div className="mt-4 rounded-lg bg-slate-100 px-4 py-3 text-sm text-slate-700">
              {message}
            </div>
          )}
        </div>

        {overviewQuery.isLoading && (
          <div className="rounded-xl bg-white p-6">Loading...</div>
        )}
        {overviewQuery.error && (
          <div className="rounded-xl bg-red-50 p-6 text-red-700">
            {String(overviewQuery.error)}
          </div>
        )}
        {overviewQuery.data && (
          <div className="grid gap-4 md:grid-cols-5">
            {cards.map(([key, label]) => (
              <div className="rounded-2xl bg-white p-5 shadow-sm" key={key}>
                <div className="text-sm text-slate-500">{label}</div>
                <div className="mt-3 text-3xl font-bold">
                  {String(overviewQuery.data[key] ?? 0)}
                </div>
              </div>
            ))}
          </div>
        )}

        <div className="mt-6 grid gap-6 lg:grid-cols-[420px_1fr]">
          <section className="rounded-2xl bg-white p-6 shadow-sm">
            <h2 className="text-lg font-semibold">Add Zabbix Datasource</h2>
            <form className="mt-4 space-y-3" onSubmit={submit}>
              <Field
                label="Name"
                value={form.name}
                onChange={(name) => setForm({ ...form, name })}
              />
              <Field
                label="API Endpoint"
                value={form.endpoint}
                onChange={(endpoint) => setForm({ ...form, endpoint })}
              />
              <Field
                label="Username"
                value={form.username}
                onChange={(username) => setForm({ ...form, username })}
              />
              <Field
                label="Password"
                type="password"
                value={form.password}
                onChange={(password) => setForm({ ...form, password })}
              />
              <Field
                label="API Token"
                type="password"
                value={form.apiToken}
                onChange={(apiToken) => setForm({ ...form, apiToken })}
              />
              <button
                className="w-full rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
                disabled={createMutation.isPending}
              >
                {createMutation.isPending ? "Creating..." : "Create Datasource"}
              </button>
            </form>
          </section>

          <section className="rounded-2xl bg-white p-6 shadow-sm">
            <h2 className="text-lg font-semibold">Datasources</h2>
            <div className="mt-4 space-y-3">
              {datasourceQuery.data?.map((ds) => (
                <div
                  className="rounded-xl border border-slate-200 p-4"
                  key={ds.id}
                >
                  <div className="flex items-center justify-between gap-4">
                    <div>
                      <div className="flex items-center gap-2 font-medium">
                        <span>{ds.name}</span>
                        <StatusChip status={ds.status} />
                      </div>
                      <div className="text-sm text-slate-500">
                        {ds.type} · last sync {ds.lastSyncAt || "-"}
                      </div>
                    </div>
                    <div className="flex gap-2">
                      <button
                        className="rounded-lg border px-3 py-1.5 text-sm"
                        onClick={() => testMutation.mutate(ds.id)}
                      >
                        Test
                      </button>
                      <button
                        className="rounded-lg bg-slate-900 px-3 py-1.5 text-sm text-white"
                        onClick={() => syncMutation.mutate(ds.id)}
                      >
                        Sync
                      </button>
                    </div>
                  </div>
                </div>
              ))}
              {!datasourceQuery.data?.length && (
                <div className="text-sm text-slate-500">No datasource yet.</div>
              )}
            </div>
          </section>
        </div>

        <div className="mt-6 grid gap-6 lg:grid-cols-2">
          <section className="rounded-2xl bg-white p-6 shadow-sm">
            <h2 className="text-lg font-semibold">Assets</h2>
            <div className="mt-4 overflow-hidden rounded-xl border border-slate-200">
              {assetQuery.data?.slice(0, 8).map((asset) => (
                <div
                  className="border-b border-slate-100 px-4 py-3 text-sm last:border-0"
                  key={asset.id}
                >
                  <div className="font-medium">
                    {asset.displayName || asset.name}
                  </div>
                  <div className="text-slate-500">
                    {asset.assetType} · {asset.source} · {asset.status}
                  </div>
                </div>
              ))}
              {!assetQuery.data?.length && (
                <div className="px-4 py-6 text-sm text-slate-500">
                  No assets synced.
                </div>
              )}
            </div>
          </section>

          <section className="rounded-2xl bg-white p-6 shadow-sm">
            <h2 className="text-lg font-semibold">Alerts</h2>
            <div className="mt-4 overflow-hidden rounded-xl border border-slate-200">
              {alertQuery.data?.slice(0, 8).map((alert) => (
                <div
                  className="border-b border-slate-100 px-4 py-3 text-sm last:border-0"
                  key={alert.id}
                >
                  <div className="font-medium">{alert.title}</div>
                  <div className="text-slate-500">
                    {alert.severity} · {alert.status} · {alert.startsAt}
                  </div>
                </div>
              ))}
              {!alertQuery.data?.length && (
                <div className="px-4 py-6 text-sm text-slate-500">
                  No alerts synced.
                </div>
              )}
            </div>
          </section>
        </div>

        <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_420px]">
          <section className="rounded-2xl bg-white p-6 shadow-sm">
            <h2 className="text-lg font-semibold">Incidents</h2>
            <div className="mt-4 overflow-hidden rounded-xl border border-slate-200">
              {incidentQuery.data?.map((incident) => (
                <button
                  className={`block w-full border-b border-slate-100 px-4 py-3 text-left text-sm last:border-0 ${
                    selectedIncidentId === incident.id
                      ? "bg-indigo-50"
                      : "bg-white hover:bg-slate-50"
                  }`}
                  key={incident.id}
                  onClick={() => setSelectedIncidentId(incident.id)}
                >
                  <div className="flex items-center justify-between gap-3">
                    <div className="font-medium">{incident.title}</div>
                    <StatusChip status={incident.status} />
                  </div>
                  <div className="mt-1 text-slate-500">
                    {incident.severity} · alerts {incident.alertCount} ·{" "}
                    {incident.startedAt}
                  </div>
                </button>
              ))}
              {!incidentQuery.data?.length && (
                <div className="px-4 py-6 text-sm text-slate-500">
                  No incidents yet. Click Aggregate Incidents after syncing
                  alerts.
                </div>
              )}
            </div>
          </section>

          <section className="rounded-2xl bg-white p-6 shadow-sm">
            <h2 className="text-lg font-semibold">Incident Detail</h2>
            {!selectedIncidentId && (
              <div className="mt-4 text-sm text-slate-500">
                Select an incident.
              </div>
            )}

            {incidentDetailQuery.isLoading && (
              <div className="mt-4 text-sm text-slate-500">
                Loading incident...
              </div>
            )}
            {incidentDetailQuery.error && (
              <div className="mt-4 text-sm text-red-600">
                {String(incidentDetailQuery.error)}
              </div>
            )}

            {incidentDetailQuery.data && (
              <div className="mt-4 space-y-5">
                <div>
                  <div className="text-base font-semibold">
                    {incidentDetailQuery.data.incident.title}
                  </div>
                  <div className="mt-1 text-sm text-slate-500">
                    {incidentDetailQuery.data.incident.severity} ·{" "}
                    {incidentDetailQuery.data.incident.status}
                  </div>
                  <p className="mt-3 text-sm text-slate-600">
                    {incidentDetailQuery.data.incident.summary}
                  </p>
                  <button
                    className="mt-3 rounded-lg bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-60"
                    disabled={resolveMutation.isPending}
                    onClick={() =>
                      resolveMutation.mutate(
                        incidentDetailQuery.data!.incident.id,
                      )
                    }
                  >
                    Resolve
                  </button>
                </div>

                <div>
                  <h3 className="text-sm font-semibold">Linked Alerts</h3>
                  <div className="mt-2 space-y-2">
                    {incidentDetailQuery.data.alerts.map((alert) => (
                      <div
                        className="rounded-lg border border-slate-200 p-3 text-sm"
                        key={alert.id}
                      >
                        <div className="font-medium">{alert.title}</div>
                        <div className="text-slate-500">
                          {alert.relationType} · {alert.severity} ·{" "}
                          {alert.startsAt}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                <div>
                  <h3 className="text-sm font-semibold">Timeline</h3>
                  <div className="mt-2 space-y-2">
                    {incidentDetailQuery.data.timeline.map((item) => (
                      <div
                        className="rounded-lg border border-slate-200 p-3 text-sm"
                        key={item.id}
                      >
                        <div className="font-medium">{item.title}</div>
                        <div className="text-slate-500">
                          {item.eventType} · {item.eventTime}
                        </div>
                        {item.description && (
                          <div className="mt-1 text-slate-600">
                            {item.description}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </section>
        </div>
      </section>
    </main>
  );
}

function Field({
  label,
  value,
  onChange,
  type = "text",
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
}) {
  return (
    <label className="block">
      <span className="text-sm text-slate-600">{label}</span>
      <input
        className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500"
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

const STATUS_STYLES: Record<string, string> = {
  active: "bg-emerald-100 text-emerald-800 border-emerald-200",
  error: "bg-rose-100 text-rose-800 border-rose-200",
  inactive: "bg-slate-100 text-slate-700 border-slate-200",
  open: "bg-rose-100 text-rose-800 border-rose-200",
  investigating: "bg-amber-100 text-amber-800 border-amber-200",
  mitigating: "bg-blue-100 text-blue-800 border-blue-200",
  resolved: "bg-emerald-100 text-emerald-800 border-emerald-200",
  closed: "bg-slate-100 text-slate-700 border-slate-200",
  ignored: "bg-slate-100 text-slate-700 border-slate-200",
};

function StatusChip({ status }: { status: string }) {
  const tone = STATUS_STYLES[status] ?? STATUS_STYLES.inactive;
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium ${tone}`}
    >
      {status}
    </span>
  );
}
```

---

# 8. 验证命令

后端：

```powershell
mvn -pl modules/aiops-incident -am test
mvn -pl apps/aiops-server -am test
```

前端：

```powershell
cd web/console
pnpm build
```

启动：

```powershell
docker compose -f infra/docker-compose.yml up -d
mvn -pl apps/aiops-server -am spring-boot:run
```

前端：

```powershell
cd web/console
pnpm dev
```

---

# 9. Phase2 验收流程

```txt
1. 启动 docker compose
2. 启动 aiops-server
3. 启动前端
4. 登录 admin / admin123
5. 创建 Zabbix 数据源
6. Test 成功
7. Sync 成功
8. Alerts 列表出现 Zabbix 告警
9. 点击 Aggregate Incidents
10. Incidents 列表出现事故
11. 点击某个 Incident
12. 右侧能看到 Linked Alerts
13. 右侧能看到 Timeline
14. 点击 Resolve
15. Incident 状态变为 resolved
16. 再次同 fingerprint 告警出现时，可以创建新的 active incident
```

---

# 10. 当前路线状态

完成这批代码后，项目路线变为：

```txt
Phase0：完成
Phase1：完成
Phase2：完成
Phase3：RCA 规则引擎
Phase4：AI 诊断助手
Phase5：Runbook / Ansible 执行
Phase6：复盘与知识沉淀
```

下一阶段建议做 **Phase3：RCA 规则引擎**，重点是：

```txt
Incident 详情页自动查询事故前后指标
基于时间窗口生成证据链
规则 R1-R8 打分
输出 suspected_root_cause / confidence / evidence_json
```

以后我会按你说的格式，**只用 Markdown 输出代码，不再给压缩包**。
