package io.aegisops.workrecord.infrastructure.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.application.port.WorkRecordCommentRepository;
import io.aegisops.workrecord.domain.model.WorkRecordComment;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkRecordCommentRepository implements WorkRecordCommentRepository {
  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public JdbcWorkRecordCommentRepository(
      NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<WorkRecordComment> list(String tenantId, String recordId, int limit) {
    return jdbc.query(
        """
        select id, record_id, content, mentions_json::text, created_by,
               created_at, updated_at, row_version
          from work_record.wr_comment
         where tenant_id = :tenantId and record_id = :recordId and deleted_at is null
         order by created_at, id limit :limit
        """,
        Map.of("tenantId", tenantId, "recordId", recordId, "limit", limit),
        this::map);
  }

  @Override
  public Optional<WorkRecordComment> find(String tenantId, String id) {
    return jdbc
        .query(
            """
            select id, record_id, content, mentions_json::text, created_by,
                   created_at, updated_at, row_version
              from work_record.wr_comment
             where tenant_id = :tenantId and id = :id and deleted_at is null
            """,
            Map.of("tenantId", tenantId, "id", id),
            this::map)
        .stream()
        .findFirst();
  }

  @Override
  public WorkRecordComment create(
      String tenantId,
      String recordId,
      String content,
      List<String> mentionUserIds,
      String createdBy) {
    String id = Ids.newId();
    jdbc.update(
        """
        insert into work_record.wr_comment(
          id, tenant_id, record_id, content, mentions_json, created_by)
        values (:id, :tenantId, :recordId, :content, cast(:mentions as jsonb), :createdBy)
        """,
        Map.of(
            "id", id,
            "tenantId", tenantId,
            "recordId", recordId,
            "content", content,
            "mentions", json(mentionUserIds),
            "createdBy", createdBy));
    return find(tenantId, id).orElseThrow();
  }

  @Override
  public boolean update(
      String tenantId,
      String id,
      String content,
      List<String> mentionUserIds,
      int expectedVersion) {
    return jdbc.update(
            """
            update work_record.wr_comment
               set content = :content, mentions_json = cast(:mentions as jsonb),
                   row_version = row_version + 1, updated_at = now()
             where tenant_id = :tenantId and id = :id and deleted_at is null
               and row_version = :expectedVersion
            """,
            Map.of(
                "tenantId", tenantId,
                "id", id,
                "content", content,
                "mentions", json(mentionUserIds),
                "expectedVersion", expectedVersion))
        == 1;
  }

  @Override
  public boolean softDelete(String tenantId, String id, int expectedVersion) {
    return jdbc.update(
            """
            update work_record.wr_comment
               set deleted_at = now(), updated_at = now(), row_version = row_version + 1
             where tenant_id = :tenantId and id = :id and deleted_at is null
               and row_version = :expectedVersion
            """,
            Map.of("tenantId", tenantId, "id", id, "expectedVersion", expectedVersion))
        == 1;
  }

  private WorkRecordComment map(ResultSet rs, int rowNumber) throws SQLException {
    try {
      return new WorkRecordComment(
          rs.getString("id"),
          rs.getString("record_id"),
          rs.getString("content"),
          objectMapper.readValue(
              rs.getString("mentions_json"), new TypeReference<List<String>>() {}),
          rs.getString("created_by"),
          rs.getObject("created_at", java.time.OffsetDateTime.class),
          rs.getObject("updated_at", java.time.OffsetDateTime.class),
          rs.getInt("row_version"));
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new SQLException("invalid comment mentions JSON", ex);
    }
  }

  private String json(List<String> values) {
    try {
      return objectMapper.writeValueAsString(values);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalArgumentException("invalid mentions", ex);
    }
  }
}
