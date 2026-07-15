package io.aegisops.workrecord.infrastructure.jdbc;

import io.aegisops.workrecord.application.port.UploadSessionRepository;
import io.aegisops.workrecord.domain.model.UploadSession;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUploadSessionRepository implements UploadSessionRepository {
  private static final String COLUMNS =
      """
      id, tenant_id, requested_by, purpose, object_key, original_file_name, content_type,
      declared_size_bytes, status, expires_at, consumed_at, created_at, updated_at
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final RowMapper<UploadSession> rowMapper = JdbcUploadSessionRepository::mapRow;

  public JdbcUploadSessionRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public UploadSession insert(UploadSession session) {
    List<UploadSession> rows =
        jdbc.query(
            """
            insert into work_record.wr_upload_session(
              id, tenant_id, requested_by, purpose, object_key, original_file_name, content_type,
              declared_size_bytes, status, expires_at, created_at, updated_at)
            values (
              :id, :tenantId, :requestedBy, :purpose, :objectKey, :fileName, :contentType,
              :sizeBytes, 'prepared', :expiresAt, :createdAt, :createdAt)
            returning
            """
                + COLUMNS,
            parameters(session),
            rowMapper);
    return rows.getFirst();
  }

  @Override
  public Optional<UploadSession> consumePrepared(
      String tenantId, String id, String requestedBy, String purpose, OffsetDateTime now) {
    List<UploadSession> rows =
        jdbc.query(
            """
            update work_record.wr_upload_session
               set status = 'consumed', consumed_at = :now, updated_at = :now
             where tenant_id = :tenantId and id = :id and requested_by = :requestedBy
               and purpose = :purpose and status = 'prepared' and expires_at > :now
            returning
            """
                + COLUMNS,
            new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", id)
                .addValue("requestedBy", requestedBy)
                .addValue("purpose", purpose)
                .addValue("now", now),
            rowMapper);
    return rows.stream().findFirst();
  }

  private static MapSqlParameterSource parameters(UploadSession session) {
    return new MapSqlParameterSource()
        .addValue("id", session.id())
        .addValue("tenantId", session.tenantId())
        .addValue("requestedBy", session.requestedBy())
        .addValue("purpose", session.purpose())
        .addValue("objectKey", session.objectKey())
        .addValue("fileName", session.originalFileName())
        .addValue("contentType", session.contentType())
        .addValue("sizeBytes", session.declaredSizeBytes())
        .addValue("expiresAt", session.expiresAt())
        .addValue("createdAt", session.createdAt());
  }

  private static UploadSession mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new UploadSession(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("requested_by"),
        rs.getString("purpose"),
        rs.getString("object_key"),
        rs.getString("original_file_name"),
        rs.getString("content_type"),
        rs.getLong("declared_size_bytes"),
        rs.getString("status"),
        rs.getObject("expires_at", OffsetDateTime.class),
        rs.getObject("consumed_at", OffsetDateTime.class),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }
}
