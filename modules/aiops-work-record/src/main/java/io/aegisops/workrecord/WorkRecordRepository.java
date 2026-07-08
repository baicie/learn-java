package io.aegisops.workrecord;

import io.aegisops.common.api.PageResult;
import io.aegisops.common.id.Ids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkRecordRepository {
  private final JdbcTemplate jdbc;

  public WorkRecordRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<WorkRecord> list(String tenantId, String ownerId, String status) {
    return queryList(
        "where tenant_id = ? and deleted_at is null and (? is null or owner_id = ?) and (? is null or status = ?)",
        tenantId,
        ownerId,
        ownerId,
        status,
        status);
  }

  public long count(String tenantId, String ownerId, String status) {
    Long total =
        jdbc.queryForObject(
            """
                select count(*) from wr_record
                 where tenant_id = ? and deleted_at is null
                   and (? is null or owner_id = ?)
                   and (? is null or status = ?)
                """,
            Long.class,
            tenantId,
            ownerId,
            ownerId,
            status,
            status);
    return total == null ? 0L : total;
  }

  public List<WorkRecord> page(String tenantId, String ownerId, String status, int page, int size) {
    int offset = (page - 1) * size;
    return jdbc.query(
        """
            select id, tenant_id, template_id, title, status, owner_id, creator_id, record_time,
                   builtin_data_json::text, custom_data_json::text, created_at, updated_at
            from wr_record
            where tenant_id = ?
              and deleted_at is null
              and (? is null or owner_id = ?)
              and (? is null or status = ?)
            order by record_time desc, created_at desc
            limit ? offset ?
            """,
        (rs, rowNum) -> mapRecord(rs),
        tenantId,
        ownerId,
        ownerId,
        status,
        status,
        size,
        offset);
  }

  public long countForUser(String tenantId, String userId, String status) {
    if (userId == null || userId.isBlank()) {
      return 0L;
    }
    Long total =
        jdbc.queryForObject(
            """
                select count(*) from wr_record
                 where tenant_id = ? and deleted_at is null
                   and (owner_id = ? or creator_id = ?)
                   and (? is null or status = ?)
                """,
            Long.class,
            tenantId,
            userId,
            userId,
            status,
            status);
    return total == null ? 0L : total;
  }

  public List<WorkRecord> pageForUser(
      String tenantId, String userId, String status, int page, int size) {
    if (userId == null || userId.isBlank()) {
      return List.of();
    }
    int offset = (page - 1) * size;
    return jdbc.query(
        """
            select id, tenant_id, template_id, title, status, owner_id, creator_id, record_time,
                   builtin_data_json::text, custom_data_json::text, created_at, updated_at
            from wr_record
            where tenant_id = ?
              and deleted_at is null
              and (owner_id = ? or creator_id = ?)
              and (? is null or status = ?)
            order by record_time desc, created_at desc
            limit ? offset ?
            """,
        (rs, rowNum) -> mapRecord(rs),
        tenantId,
        userId,
        userId,
        status,
        status,
        size,
        offset);
  }

  // ---- Enhanced pagination with dynamic filters ----

  public PageResult<WorkRecord> page(
      String tenantId,
      int page,
      int pageSize,
      String templateId,
      List<String> status,
      String keyword,
      OffsetDateTime recordTimeFrom,
      OffsetDateTime recordTimeTo,
      String creatorId,
      String ownerId,
      List<DynamicFieldFilter> filters) {

    StringBuilder where = new StringBuilder("where tenant_id = ? and deleted_at is null");
    List<Object> params = new ArrayList<>();
    params.add(tenantId);

    appendInClause(where, params, "status", status);
    appendKeywordClause(where, params, keyword);
    appendTimeRangeClause(where, params, recordTimeFrom, recordTimeTo);
    appendEqClause(where, params, "creator_id", creatorId);
    appendEqClause(where, params, "owner_id", ownerId);
    appendEqClause(where, params, "template_id", templateId);
    appendDynamicFilters(where, params, filters);

    int offset = (page - 1) * pageSize;
    long total = countWithFiltersInternal(where.toString(), params.toArray());

    List<Object> allParams = new ArrayList<>(params);
    allParams.add(pageSize);
    allParams.add(offset);

    List<WorkRecord> items = jdbc.query(
        "select id, tenant_id, template_id, title, status, owner_id, creator_id, record_time, "
            + "builtin_data_json::text, custom_data_json::text, created_at, updated_at "
            + "from wr_record " + where
            + " order by record_time desc, created_at desc limit ? offset ?",
        (rs, rowNum) -> mapRecord(rs),
        allParams.toArray());

    return new PageResult<>(total, page, pageSize, items);
  }

  public PageResult<WorkRecord> pageForUser(
      String tenantId,
      String userId,
      int page,
      int pageSize,
      String templateId,
      List<String> status,
      String keyword,
      OffsetDateTime recordTimeFrom,
      OffsetDateTime recordTimeTo,
      List<DynamicFieldFilter> filters) {

    if (userId == null || userId.isBlank()) {
      return new PageResult<>(0, page, pageSize, List.of());
    }

    StringBuilder where = new StringBuilder(
        "where tenant_id = ? and deleted_at is null and (owner_id = ? or creator_id = ?)");
    List<Object> params = new ArrayList<>();
    params.add(tenantId);
    params.add(userId);
    params.add(userId);

    appendInClause(where, params, "status", status);
    appendKeywordClause(where, params, keyword);
    appendTimeRangeClause(where, params, recordTimeFrom, recordTimeTo);
    appendEqClause(where, params, "template_id", templateId);
    appendDynamicFilters(where, params, filters);

    int offset = (page - 1) * pageSize;
    long total = countWithFiltersInternal(where.toString(), params.toArray());

    List<Object> allParams = new ArrayList<>(params);
    allParams.add(pageSize);
    allParams.add(offset);

    List<WorkRecord> items = jdbc.query(
        "select id, tenant_id, template_id, title, status, owner_id, creator_id, record_time, "
            + "builtin_data_json::text, custom_data_json::text, created_at, updated_at "
            + "from wr_record " + where
            + " order by record_time desc, created_at desc limit ? offset ?",
        (rs, rowNum) -> mapRecord(rs),
        allParams.toArray());

    return new PageResult<>(total, page, pageSize, items);
  }

  private void appendInClause(StringBuilder where, List<Object> params, String column, List<String> values) {
    if (values != null && !values.isEmpty()) {
      where.append(" and ").append(column).append(" in (");
      for (int i = 0; i < values.size(); i++) {
        where.append("?");
        if (i < values.size() - 1) where.append(",");
        params.add(values.get(i));
      }
      where.append(")");
    }
  }

  private void appendKeywordClause(StringBuilder where, List<Object> params, String keyword) {
    if (keyword != null && !keyword.isBlank()) {
      where.append(" and title ilike ?");
      params.add("%" + keyword + "%");
    }
  }

  private void appendTimeRangeClause(StringBuilder where, List<Object> params,
      OffsetDateTime from, OffsetDateTime to) {
    if (from != null) {
      where.append(" and record_time >= ?");
      params.add(from);
    }
    if (to != null) {
      where.append(" and record_time <= ?");
      params.add(to);
    }
  }

  private void appendEqClause(StringBuilder where, List<Object> params, String column, String value) {
    if (value != null && !value.isBlank()) {
      where.append(" and ").append(column).append(" = ?");
      params.add(value);
    }
  }

  @SuppressWarnings("unchecked")
  private void appendDynamicFilters(StringBuilder where, List<Object> params,
      List<DynamicFieldFilter> filters) {
    if (filters == null || filters.isEmpty()) {
      return;
    }
    for (DynamicFieldFilter f : filters) {
      String code = f.fieldCode();
      String op = f.operator();
      String jsonbPath = "(custom_data_json->>'" + code + "')";

      switch (op) {
        case "eq":
          where.append(" and ").append(jsonbPath).append(" = ?");
          params.add(f.value() != null ? f.value().toString() : null);
          break;
        case "contains":
          where.append(" and ").append(jsonbPath).append(" ilike ?");
          params.add("%" + (f.value() != null ? f.value().toString() : "") + "%");
          break;
        case "in":
          if (f.values() != null) {
            List<Object> values = (List<Object>) f.values();
            if (!values.isEmpty()) {
              where.append(" and ").append(jsonbPath).append(" in (");
              for (int i = 0; i < values.size(); i++) {
                where.append("?");
                if (i < values.size() - 1) where.append(",");
                params.add(values.get(i).toString());
              }
              where.append(")");
            }
          }
          break;
        case "gte":
          where.append(" and ").append(jsonbPath).append("::text >= ?");
          params.add(f.value() != null ? f.value().toString() : "");
          break;
        case "lte":
          where.append(" and ").append(jsonbPath).append("::text <= ?");
          params.add(f.value() != null ? f.value().toString() : "");
          break;
        case "exists":
          where.append(" and ").append(jsonbPath).append(" is not null");
          break;
        case "between":
          if (f.values() != null) {
            List<Object> values = (List<Object>) f.values();
            if (values.size() >= 2) {
              where.append(" and ").append(jsonbPath).append("::text between ? and ?");
              params.add(values.get(0).toString());
              params.add(values.get(1).toString());
            }
          }
          break;
      }
    }
  }

  private long countWithFiltersInternal(String whereClause, Object[] params) {
    Long total = jdbc.queryForObject(
        "select count(*) from wr_record " + whereClause, Long.class, params);
    return total == null ? 0L : total;
  }

  public List<WorkRecordTemplate> listTemplates(String tenantId) {
    return jdbc.query(
        "select id, tenant_id, name, code, description, enabled, schema_json::text, designer_json::text, "
            + "created_by, created_at, updated_at from wr_template where tenant_id = ? and enabled = true order by name",
        (rs, rowNum) -> new WorkRecordTemplate(
            rs.getString("id"),
            rs.getString("tenant_id"),
            rs.getString("name"),
            rs.getString("code"),
            rs.getString("description"),
            rs.getBoolean("enabled"),
            rs.getString("schema_json"),
            rs.getString("designer_json"),
            rs.getString("created_by"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)));
  }

  // ---- Export 专用方法 ----

  public long countWithFilters(String tenantId, String templateId, List<String> status,
      String keyword, OffsetDateTime recordTimeFrom, OffsetDateTime recordTimeTo,
      List<DynamicFieldFilter> filters, String creatorId, String ownerId) {
    StringBuilder where = new StringBuilder("where tenant_id = ? and deleted_at is null");
    List<Object> params = new ArrayList<>();
    params.add(tenantId);
    appendInClause(where, params, "status", status);
    appendKeywordClause(where, params, keyword);
    appendTimeRangeClause(where, params, recordTimeFrom, recordTimeTo);
    appendEqClause(where, params, "creator_id", creatorId);
    appendEqClause(where, params, "owner_id", ownerId);
    appendEqClause(where, params, "template_id", templateId);
    appendDynamicFilters(where, params, filters);
    return countWithFiltersInternal(where.toString(), params.toArray());
  }

  public List<WorkRecord> pageForExport(String tenantId, int maxRows,
      String templateId, List<String> status, String keyword,
      OffsetDateTime recordTimeFrom, OffsetDateTime recordTimeTo,
      List<DynamicFieldFilter> filters) {
    StringBuilder where = new StringBuilder("where tenant_id = ? and deleted_at is null");
    List<Object> params = new ArrayList<>();
    params.add(tenantId);
    appendInClause(where, params, "status", status);
    appendKeywordClause(where, params, keyword);
    appendTimeRangeClause(where, params, recordTimeFrom, recordTimeTo);
    appendEqClause(where, params, "template_id", templateId);
    appendDynamicFilters(where, params, filters);
    params.add(maxRows);
    return jdbc.query(
        "select id, tenant_id, template_id, title, status, owner_id, creator_id, record_time, "
            + "builtin_data_json::text, custom_data_json::text, created_at, updated_at "
            + "from wr_record " + where + " order by record_time desc, created_at desc limit ?",
        (rs, rowNum) -> mapRecord(rs),
        params.toArray());
  }

  public long countForExportUser(String tenantId, String userId, String templateId,
      List<String> status, String keyword, OffsetDateTime recordTimeFrom, OffsetDateTime recordTimeTo,
      List<DynamicFieldFilter> filters) {
    if (userId == null || userId.isBlank()) return 0;
    StringBuilder where = new StringBuilder(
        "where tenant_id = ? and deleted_at is null and (owner_id = ? or creator_id = ?)");
    List<Object> params = new ArrayList<>();
    params.add(tenantId);
    params.add(userId);
    params.add(userId);
    appendInClause(where, params, "status", status);
    appendKeywordClause(where, params, keyword);
    appendTimeRangeClause(where, params, recordTimeFrom, recordTimeTo);
    appendEqClause(where, params, "template_id", templateId);
    appendDynamicFilters(where, params, filters);
    return countWithFiltersInternal(where.toString(), params.toArray());
  }

  public List<WorkRecord> pageForExportUser(String tenantId, String userId, int maxRows,
      String templateId, List<String> status, String keyword,
      OffsetDateTime recordTimeFrom, OffsetDateTime recordTimeTo,
      List<DynamicFieldFilter> filters) {
    if (userId == null || userId.isBlank()) return List.of();
    StringBuilder where = new StringBuilder(
        "where tenant_id = ? and deleted_at is null and (owner_id = ? or creator_id = ?)");
    List<Object> params = new ArrayList<>();
    params.add(tenantId);
    params.add(userId);
    params.add(userId);
    appendInClause(where, params, "status", status);
    appendKeywordClause(where, params, keyword);
    appendTimeRangeClause(where, params, recordTimeFrom, recordTimeTo);
    appendEqClause(where, params, "template_id", templateId);
    appendDynamicFilters(where, params, filters);
    params.add(maxRows);
    return jdbc.query(
        "select id, tenant_id, template_id, title, status, owner_id, creator_id, record_time, "
            + "builtin_data_json::text, custom_data_json::text, created_at, updated_at "
            + "from wr_record " + where + " order by record_time desc, created_at desc limit ?",
        (rs, rowNum) -> mapRecord(rs),
        params.toArray());
  }

  private List<WorkRecord> queryList(String whereClause, Object... params) {
    return jdbc.query(
        "select id, tenant_id, template_id, title, status, owner_id, creator_id, record_time,"
            + " builtin_data_json::text, custom_data_json::text, created_at, updated_at"
            + " from wr_record "
            + whereClause
            + " order by record_time desc, created_at desc limit 200",
        (rs, rowNum) -> mapRecord(rs),
        params);
  }

  public List<WorkRecord> listForUser(String tenantId, String userId, String status) {
    if (userId == null || userId.isBlank()) {
      return List.of();
    }
    return jdbc.query(
        """
            select id, tenant_id, template_id, title, status, owner_id, creator_id, record_time,
                   builtin_data_json::text, custom_data_json::text, created_at, updated_at
            from wr_record
            where tenant_id = ?
              and deleted_at is null
              and (owner_id = ? or creator_id = ?)
              and (? is null or status = ?)
            order by record_time desc, created_at desc
            limit 200
            """,
        (rs, rowNum) -> mapRecord(rs),
        tenantId,
        userId,
        userId,
        status,
        status);
  }

  public List<WorkRecord> export(String tenantId, String ownerId, String status, int maxRows) {
    int limit = Math.max(1, maxRows);
    return jdbc.query(
        """
            select id, tenant_id, template_id, title, status, owner_id, creator_id, record_time,
                   builtin_data_json::text, custom_data_json::text, created_at, updated_at
            from wr_record
            where tenant_id = ?
              and deleted_at is null
              and (? is null or owner_id = ?)
              and (? is null or status = ?)
            order by record_time desc, created_at desc
            limit ?
            """,
        (rs, rowNum) -> mapRecord(rs),
        tenantId,
        ownerId,
        ownerId,
        status,
        status,
        limit);
  }

  public List<WorkRecord> exportForUser(
      String tenantId, String userId, String status, int maxRows) {
    if (userId == null || userId.isBlank()) {
      return List.of();
    }
    int limit = Math.max(1, maxRows);
    return jdbc.query(
        """
            select id, tenant_id, template_id, title, status, owner_id, creator_id, record_time,
                   builtin_data_json::text, custom_data_json::text, created_at, updated_at
            from wr_record
            where tenant_id = ?
              and deleted_at is null
              and (owner_id = ? or creator_id = ?)
              and (? is null or status = ?)
            order by record_time desc, created_at desc
            limit ?
            """,
        (rs, rowNum) -> mapRecord(rs),
        tenantId,
        userId,
        userId,
        status,
        status,
        limit);
  }

  public Optional<WorkRecord> find(String tenantId, String id) {
    List<WorkRecord> rows =
        jdbc.query(
            """
                select id, tenant_id, template_id, title, status, owner_id, creator_id, record_time,
                       builtin_data_json::text, custom_data_json::text, created_at, updated_at
                from wr_record
                where tenant_id = ? and id = ? and deleted_at is null
                """,
            (rs, rowNum) -> mapRecord(rs),
            tenantId,
            id);
    return rows.stream().findFirst();
  }

  public WorkRecord create(String tenantId, CreateWorkRecordRequest request, String creatorId) {
    String id = Ids.newId();
    jdbc.update(
        """
            insert into wr_record(
              id, tenant_id, template_id, title, status, owner_id, creator_id, record_time,
              builtin_data_json, custom_data_json)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
            """,
        id,
        tenantId,
        request.templateId(),
        request.title(),
        request.status() == null || request.status().isBlank() ? RecordStatus.DRAFT : request.status(),
        request.ownerId(),
        creatorId,
        request.recordTime(),
        blankJson(request.builtinDataJson()),
        blankJson(request.customDataJson()));
    return find(tenantId, id).orElseThrow();
  }

  public Optional<WorkRecord> update(String tenantId, String id, UpdateWorkRecordRequest request) {
    int rows =
        jdbc.update(
            """
                update wr_record
                   set title             = coalesce(?, title),
                       status            = coalesce(?, status),
                       owner_id          = coalesce(?, owner_id),
                       record_time       = coalesce(?, record_time),
                       builtin_data_json = coalesce(?::jsonb, builtin_data_json),
                       custom_data_json  = coalesce(?::jsonb, custom_data_json),
                       updated_at        = now()
                 where tenant_id = ? and id = ? and deleted_at is null
                """,
            request.title(),
            request.status(),
            request.ownerId(),
            request.recordTime(),
            nullableJson(request.builtinDataJson()),
            nullableJson(request.customDataJson()),
            tenantId,
            id);
    if (rows == 0) {
      return Optional.empty();
    }
    return find(tenantId, id);
  }

  public void softDelete(String tenantId, String id) {
    jdbc.update(
        "update wr_record set deleted_at = now() where tenant_id = ? and id = ?", tenantId, id);
  }

  private WorkRecord mapRecord(ResultSet rs) throws SQLException {
    return new WorkRecord(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("template_id"),
        rs.getString("title"),
        rs.getString("status"),
        rs.getString("owner_id"),
        rs.getString("creator_id"),
        rs.getObject("record_time", OffsetDateTime.class),
        rs.getString("builtin_data_json"),
        rs.getString("custom_data_json"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }

  private String blankJson(String value) {
    return value == null || value.isBlank() ? "{}" : value;
  }

  private String nullableJson(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value;
  }
}
