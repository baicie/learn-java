package io.aegisops.workrecord.infrastructure.jdbc;

import io.aegisops.common.api.PageResult;
import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.application.command.CreateRecordCommand;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.command.UpdateRecordCommand;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkRecordRepository implements WorkRecordRepository {

  private final NamedParameterJdbcTemplate jdbc;
  private final WorkRecordJsonbFilterSqlBuilder jsonbFilterSqlBuilder;

  public JdbcWorkRecordRepository(
      NamedParameterJdbcTemplate jdbc,
      WorkRecordJsonbFilterSqlBuilder jsonbFilterSqlBuilder) {
    this.jdbc = jdbc;
    this.jsonbFilterSqlBuilder = jsonbFilterSqlBuilder;
  }

  /** 包内构造器：兼容旧测试（默认 ObjectMapper） */
  JdbcWorkRecordRepository(NamedParameterJdbcTemplate jdbc) {
    this(jdbc, new WorkRecordJsonbFilterSqlBuilder(new com.fasterxml.jackson.databind.ObjectMapper()));
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
  public void softDelete(String tenantId, String recordId) {
    jdbc.update(
        """
        update work_record.wr_record
           set deleted_at = coalesce(deleted_at, now())
         where tenant_id = :tenantId and id = :id
        """,
        Map.of("tenantId", tenantId, "id", recordId));
  }

  @Override
  public PageResult<WorkRecord> page(String tenantId, RecordQuery query) {
    int page = Math.max(1, query.page());
    int size = Math.max(1, query.pageSize());
    int offset = (page - 1) * size;

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

    applyDynamicFilters(where, params, query);

    Long total =
        jdbc.queryForObject(
            "select count(*) from work_record.wr_record " + where, params, Long.class);

    params.put("limit", size);
    params.put("offset", offset);

    List<WorkRecord> items =
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
            (rs, rowNum) -> mapRecord(rs));

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
              query.workdayCount());
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
    String dir = "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";
    String column =
        switch (sortBy == null ? "" : sortBy) {
          case "title" -> "title";
          case "status" -> "status";
          case "ownerId" -> "owner_id";
          case "creatorId" -> "creator_id";
          case "createdAt" -> "created_at";
          case "recordTime" -> "record_time";
          default -> "record_time";
        };
    return " order by " + column + " " + dir + ", created_at desc ";
  }
}
