package io.aegisops.workrecord.infrastructure.jdbc;

import io.aegisops.common.api.PageResult;
import io.aegisops.common.exception.AppException;
import io.aegisops.common.exception.ErrorCode;
import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.application.command.CreateRecordCommand;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.command.UpdateRecordCommand;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.application.port.WorkRecordTelemetry;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkRecordRepository implements WorkRecordRepository {

  private final NamedParameterJdbcTemplate jdbc;
  private final WorkRecordJsonbFilterSqlBuilder jsonbFilterSqlBuilder;
  private final WorkRecordTelemetry telemetry;

  @Autowired
  public JdbcWorkRecordRepository(
      NamedParameterJdbcTemplate jdbc,
      WorkRecordJsonbFilterSqlBuilder jsonbFilterSqlBuilder,
      WorkRecordTelemetry telemetry) {
    this.jdbc = jdbc;
    this.jsonbFilterSqlBuilder = jsonbFilterSqlBuilder;
    this.telemetry = telemetry;
  }

  /** 包内构造器：兼容旧测试（默认 ObjectMapper 与 noop telemetry） */
  JdbcWorkRecordRepository(NamedParameterJdbcTemplate jdbc) {
    this(
        jdbc,
        new WorkRecordJsonbFilterSqlBuilder(new com.fasterxml.jackson.databind.ObjectMapper()),
        WorkRecordTelemetry.noop());
  }

  @Override
  public WorkRecord create(String tenantId, CreateRecordCommand command, String creatorId) {
    String id = Ids.newId();
    Map<String, Object> params = new HashMap<>();
    params.put("id", id);
    params.put("tenantId", tenantId);
    params.put("templateId", command.templateId());
    params.put("templateVersionId", command.templateVersionId());
    params.put("title", command.title());
    params.put("status", command.status());
    params.put("ownerId", command.ownerId());
    params.put("creatorId", actorOrSystem(creatorId));
    params.put("recordTime", command.recordTime());
    params.put("builtin", blankJson(command.builtinDataJson()));
    params.put("custom", blankJson(command.customDataJson()));

    jdbc.update(
        """
        insert into work_record.wr_record(
          id, tenant_id, template_id, template_version_id, title, status,
          owner_id, creator_id, record_time, builtin_data_json, custom_data_json)
        values (
          :id, :tenantId, :templateId, :templateVersionId, :title, :status,
          :ownerId, :creatorId, :recordTime, cast(:builtin as jsonb), cast(:custom as jsonb))
        """,
        params);
    return find(tenantId, id).orElseThrow();
  }

  @Override
  public Optional<WorkRecord> find(String tenantId, String recordId) {
    List<WorkRecord> rows =
        jdbc.query(
            """
            select id, tenant_id, template_id, template_version_id, title, status,
                   owner_id, creator_id, record_time,
                   builtin_data_json::text, custom_data_json::text,
                   row_version, created_at, updated_at, deleted_at
              from work_record.wr_record
             where tenant_id = :tenantId and id = :id and deleted_at is null
            """,
            Map.of("tenantId", tenantId, "id", recordId),
            (rs, rowNum) -> mapRecord(rs));
    return rows.stream().findFirst();
  }

  @Override
  public WorkRecord update(String tenantId, String recordId, UpdateRecordCommand command) {
    Map<String, Object> params = new HashMap<>();
    params.put("tenantId", tenantId);
    params.put("id", recordId);
    params.put("title", command.title());
    params.put("status", command.status());
    params.put("ownerId", command.ownerId());
    params.put("recordTime", command.recordTime());
    params.put("builtin", command.builtinDataJson());
    params.put("custom", command.customDataJson());

    jdbc.update(
        """
        update work_record.wr_record
           set title = coalesce(:title, title),
               status = coalesce(:status, status),
               owner_id = coalesce(:ownerId, owner_id),
               record_time = coalesce(:recordTime, record_time),
               builtin_data_json = coalesce(cast(:builtin as jsonb), builtin_data_json),
               custom_data_json = coalesce(cast(:custom as jsonb), custom_data_json),
               row_version = row_version + 1
         where tenant_id = :tenantId and id = :id and deleted_at is null
        """,
        params);
    return find(tenantId, recordId).orElseThrow();
  }

  @Override
  public WorkRecord softDelete(String tenantId, String recordId) {
    List<WorkRecord> rows =
        jdbc.query(
            """
            update work_record.wr_record
               set deleted_at = now(),
                   updated_at = now(),
                   row_version = row_version + 1
             where tenant_id = :tenantId
               and id = :id
               and deleted_at is null
            returning
               id,
               tenant_id,
               template_id,
               template_version_id,
               title,
               status,
               owner_id,
               creator_id,
               record_time,
               builtin_data_json::text,
               custom_data_json::text,
               row_version,
               created_at,
               updated_at,
               deleted_at
            """,
            Map.of("tenantId", tenantId, "id", recordId),
            (rs, rowNum) -> mapRecord(rs));
    return rows.stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("work record was not deleted: " + recordId));
  }

  @Override
  public PageResult<WorkRecord> page(String tenantId, RecordQuery query) {
    int page = Math.max(1, query.page());
    int size = Math.max(1, query.pageSize());

    long offset;
    try {
      offset = Math.multiplyExact((long) page - 1L, (long) size);
    } catch (ArithmeticException ex) {
      throw new AppException(ErrorCode.PAGE_WINDOW_EXCEEDED, "page window is too large", ex);
    }

    StringBuilder where = new StringBuilder(" where tenant_id = :tenantId and deleted_at is null ");
    Map<String, Object> params = new HashMap<>();
    params.put("tenantId", tenantId);

    if (query.onlySelf()) {
      where.append(" and (owner_id = :selfId or creator_id = :selfId) ");
      params.put("selfId", query.currentUserId());
    }
    if (query.templateId() != null && !query.templateId().isBlank()) {
      where.append(" and template_id = :templateId ");
      params.put("templateId", query.templateId());
    }
    if (query.templateVersionId() != null && !query.templateVersionId().isBlank()) {
      where.append(" and template_version_id = :templateVersionId ");
      params.put("templateVersionId", query.templateVersionId());
    }
    if (query.statuses() != null && !query.statuses().isEmpty()) {
      where.append(" and status in (:statuses) ");
      params.put("statuses", query.statuses());
    }
    if (query.keyword() != null && !query.keyword().isBlank()) {
      where.append(" and title ilike :keyword ");
      params.put("keyword", "%" + query.keyword() + "%");
    }
    if (query.recordTimeFrom() != null) {
      where.append(" and record_time >= :recordTimeFrom ");
      params.put("recordTimeFrom", query.recordTimeFrom());
    }
    if (query.recordTimeTo() != null) {
      where.append(" and record_time < :recordTimeTo ");
      params.put("recordTimeTo", query.recordTimeTo());
    }
    if (query.creatorId() != null && !query.creatorId().isBlank()) {
      where.append(" and creator_id = :creatorId ");
      params.put("creatorId", query.creatorId());
    }
    if (query.ownerId() != null && !query.ownerId().isBlank()) {
      where.append(" and owner_id = :ownerId ");
      params.put("ownerId", query.ownerId());
    }
    if (query.recordIds() != null && !query.recordIds().isEmpty()) {
      where.append(" and id in (:recordIds) ");
      params.put("recordIds", query.recordIds());
    }

    applyDynamicFilters(where, params, query);

    Long total =
        timed(
            "record_count",
            () ->
                jdbc.queryForObject(
                    "select count(*) from work_record.wr_record " + where, params, Long.class));

    params.put("limit", size);
    params.put("offset", offset);

    List<WorkRecord> items =
        timed(
            "record_page",
            () ->
                jdbc.query(
                    """
                    select id, tenant_id, template_id, template_version_id, title, status,
                           owner_id, creator_id, record_time,
                           builtin_data_json::text, custom_data_json::text,
                           row_version, created_at, updated_at, deleted_at
                      from work_record.wr_record
                    """
                        + where
                        + orderBy(query.sortBy(), query.sortDir())
                        + " limit :limit offset :offset",
                    params,
                    (rs, rowNum) -> mapRecord(rs)));

    return new PageResult<>(total == null ? 0L : total, page, size, items);
  }

  @Override
  public List<WorkRecord> listForExport(String tenantId, RecordQuery query, int maxRows) {
    List<WorkRecord> items = new ArrayList<>();
    int page = 1;
    int size = Math.max(1, maxRows);
    int remaining = maxRows;
    while (remaining > 0) {
      RecordQuery limited =
          new RecordQuery(
              page,
              Math.min(size, remaining),
              query.templateId(),
              query.templateVersionId(),
              query.statuses(),
              query.keyword(),
              query.recordTimeFrom(),
              query.recordTimeTo(),
              query.creatorId(),
              query.ownerId(),
              query.onlySelf(),
              query.currentUserId(),
              query.dynamicFilters(),
              query.sortBy(),
              query.sortDir(),
              query.quickView(),
              query.workdayCount(),
              query.recordIds());
      PageResult<WorkRecord> result = page(tenantId, limited);
      items.addAll(result.items());
      if (result.items().size() < result.size()) {
        break;
      }
      remaining -= result.items().size();
      page += 1;
    }
    return items;
  }

  private WorkRecord mapRecord(ResultSet rs) throws SQLException {
    return new WorkRecord(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("template_id"),
        rs.getString("template_version_id"),
        rs.getString("title"),
        RecordStatus.from(rs.getString("status")),
        rs.getString("owner_id"),
        rs.getString("creator_id"),
        rs.getObject("record_time", OffsetDateTime.class),
        rs.getString("builtin_data_json"),
        rs.getString("custom_data_json"),
        rs.getInt("row_version"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class),
        rs.getObject("deleted_at", OffsetDateTime.class));
  }

  private String actorOrSystem(String actor) {
    return (actor == null || actor.isBlank()) ? "system" : actor;
  }

  private String blankJson(String raw) {
    return (raw == null || raw.isBlank()) ? "{}" : raw;
  }

  private void applyDynamicFilters(
      StringBuilder where, Map<String, Object> params, RecordQuery query) {
    jsonbFilterSqlBuilder.appendFilters(where, params, query.dynamicFilters());
  }

  private String orderBy(String sortBy, String sortDir) {
    String direction = "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";

    return switch (sortBy == null ? "" : sortBy) {
      case "title" -> " order by title " + direction + ", created_at desc, id desc ";
      case "status" -> " order by status " + direction + ", created_at desc, id desc ";
      case "ownerId" ->
          " order by owner_id " + direction + " nulls last, created_at desc, id desc ";
      case "creatorId" -> " order by creator_id " + direction + ", created_at desc, id desc ";
      case "createdAt" -> " order by created_at " + direction + ", id desc ";
      case "recordTime" -> " order by record_time " + direction + ", created_at desc, id desc ";
      default -> " order by record_time desc, created_at desc, id desc ";
    };
  }

  private <T> T timed(String operation, java.util.function.Supplier<T> supplier) {
    long started = System.nanoTime();
    try {
      return supplier.get();
    } finally {
      telemetry.recordQuery(operation, Duration.ofNanos(System.nanoTime() - started));
    }
  }
}
