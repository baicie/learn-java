---
title: Phase 20.4 统计报表、工作量分析、缺失提醒与值班交接
type: phase
status: draft
phase: work-record-20
owner: ai
created: 2026-07-14
updated: 2026-07-14
related: []
---

# Phase 20.4：统计报表、工作量分析、缺失提醒与值班交接

> 基线：`baicie/ai-ops`，分支 `feat/record-doc-portal`，提交 `43d16315cc4b68cc705177d2c8faba1d8e42644e`。
>
> 本文件由 Phase 20 总方案按主题拆分。Phase 20 开发前必须先完成 Phase 19 P0 修复。

## 数据库迁移

### 4.3 V0032：提醒、通知、值班交接

```sql
-- V0032__phase20_reminder_handover.sql

create table if not exists work_record.wr_reminder_rule (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    name varchar(160) not null,
    template_id varchar(64) not null
        references work_record.wr_template(id),
    target_type varchar(24) not null,
    target_json jsonb not null default '{}'::jsonb,
    cutoff_time time not null,
    time_zone varchar(64) not null,
    enabled boolean not null default true,
    created_by varchar(64) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint ck_wr_reminder_target_type
        check (target_type in ('users', 'role')),

    constraint ck_wr_reminder_target_json
        check (jsonb_typeof(target_json) = 'object')
);

create index if not exists
idx_wr_reminder_rule_due
on work_record.wr_reminder_rule(enabled, time_zone, cutoff_time);

create table if not exists work_record.wr_notification (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    user_id varchar(64) not null,
    notification_type varchar(48) not null,
    title varchar(255) not null,
    content text not null,
    resource_type varchar(64),
    resource_id varchar(64),
    dedupe_key varchar(192) not null,
    created_at timestamptz not null default now(),
    read_at timestamptz,

    constraint uk_wr_notification_dedupe
        unique (tenant_id, user_id, dedupe_key)
);

create index if not exists
idx_wr_notification_user_unread
on work_record.wr_notification(tenant_id, user_id, read_at, created_at desc);

create table if not exists work_record.wr_handover (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    from_user_id varchar(64) not null,
    to_user_id varchar(64) not null,
    shift_start timestamptz not null,
    shift_end timestamptz not null,
    status varchar(24) not null default 'draft',
    summary text not null,
    record_ids_json jsonb not null default '[]'::jsonb,
    relation_ids_json jsonb not null default '[]'::jsonb,
    created_by varchar(64) not null,
    created_at timestamptz not null default now(),
    accepted_at timestamptz,
    completed_at timestamptz,
    row_version integer not null default 1,

    constraint ck_wr_handover_period
        check (shift_end > shift_start),

    constraint ck_wr_handover_status
        check (status in ('draft', 'submitted', 'accepted', 'completed', 'cancelled')),

    constraint ck_wr_handover_records
        check (jsonb_typeof(record_ids_json) = 'array'),

    constraint ck_wr_handover_relations
        check (jsonb_typeof(relation_ids_json) = 'array')
);

create index if not exists
idx_wr_handover_receiver
on work_record.wr_handover(tenant_id, to_user_id, status, created_at desc);
```

## 11. Phase 20.4：统计报表、工作量分析、缺失提醒与值班交接

### 11.1 统计原则

第一版统计只查询 PostgreSQL，不立即接 ClickHouse：

```text
记录量 < 100 万：PostgreSQL 聚合 + 合理索引
记录量 >= 100 万或跨年高频报表：异步同步 ClickHouse
```

动态字段只允许统计模板版本中 `statistical=true` 且类型为 `number/select/multi_select/boolean` 的字段。任何字段编码都必须由字段索引表解析，禁止把请求中的字段名直接拼入 SQL。

### 11.2 StatisticsQuery.java

```java
package io.aegisops.workrecord.extension.application.command;

import java.time.OffsetDateTime;

public record StatisticsQuery(
    String templateId,
    String templateVersionId,
    OffsetDateTime from,
    OffsetDateTime to,
    String groupBy,
    String statisticalFieldCode) {}
```

### 11.3 StatisticsResult.java

```java
package io.aegisops.workrecord.extension.application.model;

import java.math.BigDecimal;
import java.util.List;

public record StatisticsResult(
    long totalRecords,
    long completedRecords,
    long distinctOwners,
    List<SeriesPoint> series,
    FieldAggregate fieldAggregate) {

  public StatisticsResult {
    series = series == null ? List.of() : List.copyOf(series);
  }

  public record SeriesPoint(
      String key,
      String label,
      long count,
      BigDecimal value) {}

  public record FieldAggregate(
      String fieldCode,
      BigDecimal sum,
      BigDecimal average,
      BigDecimal minimum,
      BigDecimal maximum,
      long valueCount) {}
}
```

### 11.4 WorkloadSummary.java

```java
package io.aegisops.workrecord.extension.application.model;

import java.math.BigDecimal;
import java.util.List;

public record WorkloadSummary(
    int workdayCount,
    List<UserWorkload> users) {

  public WorkloadSummary {
    users = users == null ? List.of() : List.copyOf(users);
  }

  public record UserWorkload(
      String userId,
      String displayName,
      long recordCount,
      long completedCount,
      BigDecimal numericWorkload,
      BigDecimal recordsPerWorkday) {}
}
```

### 11.5 StatisticsRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.workrecord.extension.application.command.StatisticsQuery;
import io.aegisops.workrecord.extension.application.model.StatisticsResult;
import io.aegisops.workrecord.extension.application.model.WorkloadSummary;
import java.time.OffsetDateTime;

public interface StatisticsRepository {

  StatisticsResult aggregate(
      String tenantId,
      StatisticsQuery query,
      StatisticalField field);

  WorkloadSummary workload(
      String tenantId,
      String templateId,
      OffsetDateTime from,
      OffsetDateTime to,
      int workdayCount,
      StatisticalField field);

  record StatisticalField(
      String fieldCode,
      String fieldType) {

    public static StatisticalField none() {
      return new StatisticalField(null, null);
    }

    public boolean present() {
      return fieldCode != null;
    }
  }
}
```

### 11.6 JdbcStatisticsRepository.java

```java
package io.aegisops.workrecord.extension.infrastructure.jdbc;

import io.aegisops.workrecord.extension.application.command.StatisticsQuery;
import io.aegisops.workrecord.extension.application.model.StatisticsResult;
import io.aegisops.workrecord.extension.application.model.WorkloadSummary;
import io.aegisops.workrecord.extension.application.port.StatisticsRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcStatisticsRepository implements StatisticsRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcStatisticsRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public StatisticsResult aggregate(
      String tenantId,
      StatisticsQuery query,
      StatisticalField field) {
    Map<String, Object> params = params(tenantId, query);
    Map<String, Object> totals =
        jdbc.queryForMap(
            """
            select count(*) as total_count,
                   count(*) filter (where status='done') as completed_count,
                   count(distinct owner_id) filter (where owner_id is not null) as owner_count
            from work_record.wr_record
            where tenant_id=:tenantId and deleted_at is null
              and (:templateId is null or template_id=:templateId)
              and (:versionId is null or template_version_id=:versionId)
              and (:fromTime is null or record_time>=:fromTime)
              and (:toTime is null or record_time<:toTime)
            """,
            params);

    List<StatisticsResult.SeriesPoint> series =
        groupedSeries(query.groupBy(), params);
    StatisticsResult.FieldAggregate aggregate =
        field.present() ? numericAggregate(field, params) : null;

    return new StatisticsResult(
        number(totals.get("total_count")),
        number(totals.get("completed_count")),
        number(totals.get("owner_count")),
        series,
        aggregate);
  }

  @Override
  public WorkloadSummary workload(
      String tenantId,
      String templateId,
      java.time.OffsetDateTime from,
      java.time.OffsetDateTime to,
      int workdayCount,
      StatisticalField field) {
    Map<String, Object> params = new HashMap<>();
    params.put("tenantId", tenantId);
    params.put("templateId", blankToNull(templateId));
    params.put("fromTime", from);
    params.put("toTime", to);
    params.put("workdays", Math.max(workdayCount, 1));

    String valueExpression =
        field.present() && "number".equals(field.fieldType())
            ? "coalesce(sum(case when jsonb_typeof(custom_data_json -> '"
                + sqlLiteral(field.fieldCode())
                + "')='number' then (custom_data_json ->> '"
                + sqlLiteral(field.fieldCode())
                + "')::numeric else 0 end),0)"
            : "0::numeric";

    List<WorkloadSummary.UserWorkload> rows =
        jdbc.query(
            """
            select coalesce(owner_id, creator_id) as user_id,
                   count(*) as record_count,
                   count(*) filter (where status='done') as completed_count,
                   """
                + valueExpression
                + """ as workload,
                   count(*)::numeric / :workdays as records_per_workday
            from work_record.wr_record
            where tenant_id=:tenantId and deleted_at is null
              and (:templateId is null or template_id=:templateId)
              and record_time>=:fromTime and record_time<:toTime
            group by coalesce(owner_id, creator_id)
            order by workload desc, record_count desc, user_id
            """,
            params,
            (rs, rowNum) ->
                new WorkloadSummary.UserWorkload(
                    rs.getString("user_id"),
                    rs.getString("user_id"),
                    rs.getLong("record_count"),
                    rs.getLong("completed_count"),
                    rs.getBigDecimal("workload"),
                    rs.getBigDecimal("records_per_workday")));
    return new WorkloadSummary(workdayCount, rows);
  }

  private List<StatisticsResult.SeriesPoint> groupedSeries(
      String groupBy,
      Map<String, Object> params) {
    Grouping grouping = Grouping.from(groupBy);
    String sql =
        "select " + grouping.expression() + " as group_key, count(*) as item_count"
            + " from work_record.wr_record"
            + " where tenant_id=:tenantId and deleted_at is null"
            + " and (:templateId is null or template_id=:templateId)"
            + " and (:versionId is null or template_version_id=:versionId)"
            + " and (:fromTime is null or record_time>=:fromTime)"
            + " and (:toTime is null or record_time<:toTime)"
            + " group by " + grouping.expression()
            + " order by group_key";
    return jdbc.query(
        sql,
        params,
        (rs, rowNum) ->
            new StatisticsResult.SeriesPoint(
                rs.getString("group_key"),
                rs.getString("group_key"),
                rs.getLong("item_count"),
                null));
  }

  private StatisticsResult.FieldAggregate numericAggregate(
      StatisticalField field,
      Map<String, Object> params) {
    if (!"number".equals(field.fieldType())) {
      return null;
    }
    String code = sqlLiteral(field.fieldCode());
    String numeric =
        "case when jsonb_typeof(custom_data_json -> '" + code + "')='number'"
            + " then (custom_data_json ->> '" + code + "')::numeric end";
    Map<String, Object> row =
        jdbc.queryForMap(
            "select coalesce(sum(" + numeric + "),0) as value_sum,"
                + " avg(" + numeric + ") as value_avg,"
                + " min(" + numeric + ") as value_min,"
                + " max(" + numeric + ") as value_max,"
                + " count(" + numeric + ") as value_count"
                + " from work_record.wr_record"
                + " where tenant_id=:tenantId and deleted_at is null"
                + " and (:templateId is null or template_id=:templateId)"
                + " and (:versionId is null or template_version_id=:versionId)"
                + " and (:fromTime is null or record_time>=:fromTime)"
                + " and (:toTime is null or record_time<:toTime)",
            params);
    return new StatisticsResult.FieldAggregate(
        field.fieldCode(),
        decimal(row.get("value_sum")),
        decimal(row.get("value_avg")),
        decimal(row.get("value_min")),
        decimal(row.get("value_max")),
        number(row.get("value_count")));
  }

  private Map<String, Object> params(String tenantId, StatisticsQuery query) {
    Map<String, Object> params = new HashMap<>();
    params.put("tenantId", tenantId);
    params.put("templateId", blankToNull(query.templateId()));
    params.put("versionId", blankToNull(query.templateVersionId()));
    params.put("fromTime", query.from());
    params.put("toTime", query.to());
    return params;
  }

  private String sqlLiteral(String value) {
    if (value == null || !value.matches("^[a-zA-Z][a-zA-Z0-9_]{0,63}$")) {
      throw new IllegalArgumentException("invalid statistical field code");
    }
    return value;
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private long number(Object value) {
    return value instanceof Number number ? number.longValue() : 0L;
  }

  private BigDecimal decimal(Object value) {
    return value instanceof BigDecimal decimal ? decimal : null;
  }

  private enum Grouping {
    DAY("to_char(record_time at time zone 'UTC', 'YYYY-MM-DD')"),
    MONTH("to_char(record_time at time zone 'UTC', 'YYYY-MM')"),
    STATUS("status"),
    OWNER("coalesce(owner_id, creator_id)"),
    TEMPLATE("template_id");

    private final String expression;

    Grouping(String expression) {
      this.expression = expression;
    }

    String expression() {
      return expression;
    }

    static Grouping from(String value) {
      if (value == null || value.isBlank()) {
        return DAY;
      }
      return Grouping.valueOf(value.trim().toUpperCase());
    }
  }
}
```

### 11.7 StatisticsService.java

```java
package io.aegisops.workrecord.extension.application.service;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.extension.application.command.StatisticsQuery;
import io.aegisops.workrecord.extension.application.model.StatisticsResult;
import io.aegisops.workrecord.extension.application.model.WorkloadSummary;
import io.aegisops.workrecord.extension.application.port.StatisticsRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class StatisticsService {

  private final StatisticsRepository repository;
  private final WorkRecordFieldIndexRepository fields;
  private final WorkRecordCalendarPort calendar;
  private final Clock clock;

  public StatisticsService(
      StatisticsRepository repository,
      WorkRecordFieldIndexRepository fields,
      WorkRecordCalendarPort calendar,
      @org.springframework.beans.factory.annotation.Qualifier("workRecordClock") Clock clock) {
    this.repository = repository;
    this.fields = fields;
    this.calendar = calendar;
    this.clock = clock;
  }

  public StatisticsResult statistics(
      String tenantId,
      StatisticsQuery query,
      UserPrincipal user) {
    requireAnalytics(user);
    validateRange(query.from(), query.to());
    return repository.aggregate(tenantId, query, resolveField(tenantId, query));
  }

  public WorkloadSummary workload(
      String tenantId,
      StatisticsQuery query,
      UserPrincipal user) {
    requireAnalytics(user);
    validateRange(query.from(), query.to());
    int workdays =
        calendar.countWorkdays(
            tenantId,
            query.from().toInstant(),
            query.to().toInstant());
    return repository.workload(
        tenantId,
        query.templateId(),
        query.from(),
        query.to(),
        workdays,
        resolveField(tenantId, query));
  }

  private StatisticsRepository.StatisticalField resolveField(
      String tenantId,
      StatisticsQuery query) {
    if (query.statisticalFieldCode() == null || query.statisticalFieldCode().isBlank()) {
      return StatisticsRepository.StatisticalField.none();
    }
    if (query.templateVersionId() == null || query.templateVersionId().isBlank()) {
      throw new IllegalArgumentException("templateVersionId is required for dynamic statistics");
    }
    List<WorkRecordField> versionFields =
        fields.listByVersion(tenantId, query.templateVersionId());
    WorkRecordField field =
        versionFields.stream()
            .filter(item -> item.fieldCode().equals(query.statisticalFieldCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("statistical field not found"));
    if (!field.statistical() || field.fieldType() != FieldType.NUMBER) {
      throw new IllegalArgumentException("field is not a statistical number field");
    }
    return new StatisticsRepository.StatisticalField(
        field.fieldCode(), field.fieldType().value());
  }

  private void requireAnalytics(UserPrincipal user) {
    if (user == null || !user.hasPermission("work-record:analytics")) {
      throw new AccessDeniedException("not allowed to read work-record analytics");
    }
  }

  private void validateRange(OffsetDateTime from, OffsetDateTime to) {
    if (from == null || to == null || !to.isAfter(from)) {
      throw new IllegalArgumentException("valid statistics time range is required");
    }
    if (java.time.Duration.between(from, to).toDays() > 730) {
      throw new IllegalArgumentException("statistics range cannot exceed 730 days");
    }
  }
}
```

`WorkRecordCalendarPort` 当前没有 `countWorkdays` 时，增加该方法并由 Platform Calendar Adapter 使用 `platform_calendar_day.is_workday=true` 聚合实现。

### 11.8 StatisticsController.java

```java
package io.aegisops.workrecord.extension.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.command.StatisticsQuery;
import io.aegisops.workrecord.extension.application.service.StatisticsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/analytics")
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "server",
    matchIfMissing = true)
public class StatisticsController {

  private final StatisticsService service;

  public StatisticsController(StatisticsService service) {
    this.service = service;
  }

  @GetMapping("/statistics")
  @PreAuthorize("hasAuthority('work-record:analytics')")
  public ApiResponse<?> statistics(
      @ModelAttribute StatisticsQuery query,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.statistics(TenantContext.requireTenantId(), query, user));
  }

  @GetMapping("/workload")
  @PreAuthorize("hasAuthority('work-record:analytics')")
  public ApiResponse<?> workload(
      @ModelAttribute StatisticsQuery query,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.workload(TenantContext.requireTenantId(), query, user));
  }
}
```

### 11.9 NotificationService.java

```java
package io.aegisops.workrecord.extension.application.service;

import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.extension.application.port.NotificationRepository;
import io.aegisops.workrecord.extension.domain.WorkRecordNotification;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

  private final NotificationRepository repository;

  public NotificationService(NotificationRepository repository) {
    this.repository = repository;
  }

  public void createMention(
      String tenantId,
      String userId,
      String recordId,
      String actorName,
      String commentId) {
    repository.insertIfAbsent(
        new WorkRecordNotification(
            Ids.newId(),
            tenantId,
            userId,
            "comment_mention",
            "工作记录评论提到了你",
            actorName + " 在评论中提到了你",
            "work_record",
            recordId,
            "comment-mention:" + commentId + ":" + userId,
            null,
            null));
  }

  public void createMissingDaily(
      String tenantId,
      String userId,
      String templateId,
      LocalDate date) {
    repository.insertIfAbsent(
        new WorkRecordNotification(
            Ids.newId(),
            tenantId,
            userId,
            "daily_record_missing",
            "日报尚未填写",
            date + " 为工作日，请及时填写日报",
            "work_record_template",
            templateId,
            "daily-missing:" + templateId + ":" + date + ":" + userId,
            null,
            null));
  }
}
```

`NotificationRepository.insertIfAbsent` 使用 `on conflict (tenant_id,user_id,dedupe_key) do nothing`，从数据库层保证多 Worker 实例不重复提醒。

### 11.10 MissingDailyReminderScheduler.java

```java
package io.aegisops.worker.job;

import io.aegisops.workrecord.extension.application.service.MissingDailyReminderService;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MissingDailyReminderScheduler {

  private final MissingDailyReminderService service;
  private final Clock clock;

  public MissingDailyReminderScheduler(
      MissingDailyReminderService service,
      Clock clock) {
    this.service = service;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${aiops.work-record.reminder-scan-ms:300000}")
  public void scan() {
    service.scanDueRules(clock.instant());
  }
}
```

### 11.11 MissingDailyReminderService.java

```java
package io.aegisops.workrecord.extension.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.extension.application.port.ReminderRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MissingDailyReminderService {

  private final ReminderRepository reminders;
  private final WorkRecordCalendarPort calendar;
  private final NotificationService notifications;
  private final ObjectMapper objectMapper;

  public MissingDailyReminderService(
      ReminderRepository reminders,
      WorkRecordCalendarPort calendar,
      NotificationService notifications,
      ObjectMapper objectMapper) {
    this.reminders = reminders;
    this.calendar = calendar;
    this.notifications = notifications;
    this.objectMapper = objectMapper;
  }

  public void scanDueRules(Instant now) {
    for (var rule : reminders.findDueRules(now, 200)) {
      ZoneId zone = ZoneId.of(rule.timeZone());
      LocalDate date = now.atZone(zone).toLocalDate();
      if (!calendar.isWorkday(rule.tenantId(), date)) {
        continue;
      }
      for (String userId : targetUsers(rule)) {
        if (!reminders.hasRecord(
            rule.tenantId(), rule.templateId(), userId, date, zone)) {
          notifications.createMissingDaily(
              rule.tenantId(), userId, rule.templateId(), date);
        }
      }
    }
  }

  private Set<String> targetUsers(ReminderRepository.ReminderRule rule) {
    try {
      var node = objectMapper.readTree(rule.targetJson());
      if ("users".equals(rule.targetType())) {
        List<String> users =
            objectMapper.convertValue(node.path("userIds"), new TypeReference<List<String>>() {});
        return new LinkedHashSet<>(users);
      }
      return new LinkedHashSet<>(reminders.usersByRole(rule.tenantId(), node.path("roleCode").asText()));
    } catch (Exception ex) {
      throw new IllegalStateException("invalid reminder target", ex);
    }
  }
}
```

调休工作日由 `calendar.isWorkday()` 决定；周末但被标记为调休上班时必须提醒，节假日必须跳过。

### 11.12 HandoverStatus.java

```java
package io.aegisops.workrecord.extension.domain;

public enum HandoverStatus {
  DRAFT,
  SUBMITTED,
  ACCEPTED,
  COMPLETED,
  CANCELLED;

  public String value() {
    return name().toLowerCase();
  }
}
```

### 11.13 HandoverService.java

```java
package io.aegisops.workrecord.extension.application.service;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.port.HandoverRepository;
import io.aegisops.workrecord.extension.application.port.RecordAccessPort;
import io.aegisops.workrecord.extension.domain.HandoverStatus;
import io.aegisops.workrecord.extension.domain.WorkRecordHandover;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HandoverService {

  private final HandoverRepository repository;
  private final RecordAccessPort records;

  public HandoverService(
      HandoverRepository repository,
      RecordAccessPort records) {
    this.repository = repository;
    this.records = records;
  }

  @Transactional
  public WorkRecordHandover create(
      String tenantId,
      CreateHandover command,
      UserPrincipal user) {
    requirePermission(user);
    if (!command.shiftEnd().isAfter(command.shiftStart())) {
      throw new IllegalArgumentException("shiftEnd must be after shiftStart");
    }
    List<String> recordIds = command.recordIds() == null ? List.of() : command.recordIds();
    if (recordIds.size() > 200) {
      throw new IllegalArgumentException("handover supports at most 200 records");
    }
    for (String recordId : recordIds) {
      records.requireReadable(tenantId, recordId, user);
    }
    return repository.create(tenantId, command, user.id());
  }

  @Transactional
  public WorkRecordHandover submit(
      String tenantId,
      String handoverId,
      int rowVersion,
      UserPrincipal user) {
    WorkRecordHandover current = require(tenantId, handoverId);
    if (!current.createdBy().equals(user.id()) || current.status() != HandoverStatus.DRAFT) {
      throw new AccessDeniedException("handover cannot be submitted");
    }
    return transition(current, HandoverStatus.SUBMITTED, rowVersion);
  }

  @Transactional
  public WorkRecordHandover accept(
      String tenantId,
      String handoverId,
      int rowVersion,
      UserPrincipal user) {
    WorkRecordHandover current = require(tenantId, handoverId);
    if (!current.toUserId().equals(user.id()) || current.status() != HandoverStatus.SUBMITTED) {
      throw new AccessDeniedException("handover cannot be accepted");
    }
    return transition(current, HandoverStatus.ACCEPTED, rowVersion);
  }

  @Transactional
  public WorkRecordHandover complete(
      String tenantId,
      String handoverId,
      int rowVersion,
      UserPrincipal user) {
    WorkRecordHandover current = require(tenantId, handoverId);
    if (!current.toUserId().equals(user.id()) || current.status() != HandoverStatus.ACCEPTED) {
      throw new AccessDeniedException("handover cannot be completed");
    }
    return transition(current, HandoverStatus.COMPLETED, rowVersion);
  }

  private WorkRecordHandover transition(
      WorkRecordHandover current,
      HandoverStatus target,
      int expectedVersion) {
    if (!repository.transition(
        current.tenantId(),
        current.id(),
        current.status(),
        target,
        expectedVersion)) {
      throw new IllegalStateException("handover was modified by another request");
    }
    return require(current.tenantId(), current.id());
  }

  private WorkRecordHandover require(String tenantId, String id) {
    return repository.find(tenantId, id)
        .orElseThrow(() -> new IllegalArgumentException("handover not found"));
  }

  private void requirePermission(UserPrincipal user) {
    if (user == null || !user.hasPermission("work-record:handover")) {
      throw new AccessDeniedException("not allowed to manage handover");
    }
  }

  public record CreateHandover(
      String fromUserId,
      String toUserId,
      java.time.OffsetDateTime shiftStart,
      java.time.OffsetDateTime shiftEnd,
      String summary,
      List<String> recordIds,
      List<String> relationIds) {}
}
```

### 11.14 StatisticsServiceTest.java

```java
package io.aegisops.workrecord.extension.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.extension.application.command.StatisticsQuery;
import io.aegisops.workrecord.extension.application.port.StatisticsRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class StatisticsServiceTest {

  @Test
  void rejectsNonStatisticalUserBeforeQuery() {
    StatisticsRepository repository = Mockito.mock(StatisticsRepository.class);
    WorkRecordFieldIndexRepository fields = Mockito.mock(WorkRecordFieldIndexRepository.class);
    WorkRecordCalendarPort calendar = Mockito.mock(WorkRecordCalendarPort.class);
    StatisticsService service =
        new StatisticsService(repository, fields, calendar, Clock.systemUTC());

    assertThatThrownBy(
            () ->
                service.statistics(
                    "t1",
                    new StatisticsQuery(
                        null,
                        null,
                        OffsetDateTime.parse("2026-07-01T00:00:00Z"),
                        OffsetDateTime.parse("2026-08-01T00:00:00Z"),
                        "day",
                        null),
                    TestPrincipals.normalUser()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

    verifyNoInteractions(repository);
  }
}
```

### 11.15 MissingDailyReminderServiceTest.java

```java
package io.aegisops.workrecord.extension.application.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.extension.application.port.ReminderRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MissingDailyReminderServiceTest {

  @Test
  void holidayDoesNotCreateMissingNotification() {
    ReminderRepository repository = Mockito.mock(ReminderRepository.class);
    WorkRecordCalendarPort calendar = Mockito.mock(WorkRecordCalendarPort.class);
    NotificationService notifications = Mockito.mock(NotificationService.class);
    var rule = TestReminderRules.users("rule1", "t1", "tpl1", "Asia/Tokyo", "u1");
    when(repository.findDueRules(Mockito.any(), Mockito.anyInt()))
        .thenReturn(List.of(rule));
    when(calendar.isWorkday("t1", LocalDate.of(2026, 7, 20))).thenReturn(false);

    var service =
        new MissingDailyReminderService(
            repository,
            calendar,
            notifications,
            new ObjectMapper());
    service.scanDueRules(Instant.parse("2026-07-20T10:00:00Z"));

    verify(notifications, never())
        .createMissingDaily(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  void adjustedWeekendWorkdayCreatesReminder() {
    ReminderRepository repository = Mockito.mock(ReminderRepository.class);
    WorkRecordCalendarPort calendar = Mockito.mock(WorkRecordCalendarPort.class);
    NotificationService notifications = Mockito.mock(NotificationService.class);
    var rule = TestReminderRules.users("rule1", "t1", "tpl1", "Asia/Tokyo", "u1");
    when(repository.findDueRules(Mockito.any(), Mockito.anyInt()))
        .thenReturn(List.of(rule));
    when(calendar.isWorkday("t1", LocalDate.of(2026, 7, 19))).thenReturn(true);
    when(repository.hasRecord(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(false);

    var service =
        new MissingDailyReminderService(
            repository,
            calendar,
            notifications,
            new ObjectMapper());
    service.scanDueRules(Instant.parse("2026-07-19T10:00:00Z"));

    verify(notifications).createMissingDaily("t1", "u1", "tpl1", LocalDate.of(2026, 7, 19));
  }
}
```

---

## 补充领域模型与 Repository 契约

### 16.1 WorkRecordNotification.java

```java
package io.aegisops.workrecord.extension.domain;

import java.time.OffsetDateTime;

public record WorkRecordNotification(
    String id,
    String tenantId,
    String userId,
    String notificationType,
    String title,
    String content,
    String resourceType,
    String resourceId,
    String dedupeKey,
    OffsetDateTime createdAt,
    OffsetDateTime readAt) {}
```

### 16.2 WorkRecordHandover.java

```java
package io.aegisops.workrecord.extension.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record WorkRecordHandover(
    String id,
    String tenantId,
    String fromUserId,
    String toUserId,
    OffsetDateTime shiftStart,
    OffsetDateTime shiftEnd,
    HandoverStatus status,
    String summary,
    List<String> recordIds,
    List<String> relationIds,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime acceptedAt,
    OffsetDateTime completedAt,
    int rowVersion) {

  public WorkRecordHandover {
    recordIds = recordIds == null ? List.of() : List.copyOf(recordIds);
    relationIds = relationIds == null ? List.of() : List.copyOf(relationIds);
  }
}
```

### 16.7 NotificationRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.workrecord.extension.domain.WorkRecordNotification;
import java.util.List;

public interface NotificationRepository {

  boolean insertIfAbsent(WorkRecordNotification notification);

  List<WorkRecordNotification> listUnread(
      String tenantId,
      String userId,
      int limit);

  boolean markRead(
      String tenantId,
      String userId,
      String notificationId);
}
```

### 16.8 ReminderRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

public interface ReminderRepository {

  List<ReminderRule> findDueRules(Instant now, int limit);

  boolean hasRecord(
      String tenantId,
      String templateId,
      String userId,
      LocalDate date,
      ZoneId zoneId);

  List<String> usersByRole(String tenantId, String roleCode);

  record ReminderRule(
      String id,
      String tenantId,
      String templateId,
      String targetType,
      String targetJson,
      LocalTime cutoffTime,
      String timeZone) {}
}
```

### 16.9 HandoverRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.workrecord.extension.application.service.HandoverService.CreateHandover;
import io.aegisops.workrecord.extension.domain.HandoverStatus;
import io.aegisops.workrecord.extension.domain.WorkRecordHandover;
import java.util.List;
import java.util.Optional;

public interface HandoverRepository {

  WorkRecordHandover create(
      String tenantId,
      CreateHandover command,
      String actorId);

  Optional<WorkRecordHandover> find(String tenantId, String handoverId);

  List<WorkRecordHandover> listForUser(
      String tenantId,
      String userId,
      int limit);

  boolean transition(
      String tenantId,
      String handoverId,
      HandoverStatus expected,
      HandoverStatus target,
      int expectedVersion);
}
```
