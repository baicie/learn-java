package io.aegisops.workrecord;

import io.aegisops.common.id.Ids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
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

  /**
   * 仅返回当前用户作为 owner 或 creator 的记录。userId 为空时退化为租户级查询，与 {@link #list(String, String, String)}
   * 一致——这是为了避免 null userId 时静默越权。
   */
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

  /** 导出专用：显式返回最多 {@code maxRows} 行，超过上限的记录会被截断。调用方负责根据返回值判断是否需要提示用户。 */
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
        request.status() == null || request.status().isBlank() ? "draft" : request.status(),
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

  /**
   * 用于 update 路径：{@code null} 透传给 SQL，让 {@code coalesce(?::jsonb, ...)} 保留现有 值；只有非 null 时才校验为合法
   * JSON。{@code ""} 视为 null，避免空串变成空对象。
   */
  private String nullableJson(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value;
  }
}
