package io.aegisops.workrecord.infrastructure.jdbc;

import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.application.port.WorkRecordAttachmentRepository;
import io.aegisops.workrecord.domain.model.UploadSession;
import io.aegisops.workrecord.domain.model.WorkRecordAttachment;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkRecordAttachmentRepository implements WorkRecordAttachmentRepository {
  private static final String COLUMNS =
      "id, record_id, upload_id, object_key, file_name, content_type, size_bytes, sha256, status, uploaded_by, created_at";
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcWorkRecordAttachmentRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<WorkRecordAttachment> list(String tenantId, String recordId) {
    return jdbc.query(
        "select "
            + COLUMNS
            + " from work_record.wr_attachment where tenant_id=:tenantId and record_id=:recordId and deleted_at is null order by created_at desc",
        Map.of("tenantId", tenantId, "recordId", recordId),
        this::map);
  }

  @Override
  public Optional<WorkRecordAttachment> find(String tenantId, String id) {
    return jdbc
        .query(
            "select "
                + COLUMNS
                + " from work_record.wr_attachment where tenant_id=:tenantId and id=:id and deleted_at is null",
            Map.of("tenantId", tenantId, "id", id),
            this::map)
        .stream()
        .findFirst();
  }

  @Override
  public WorkRecordAttachment create(
      String tenantId, String recordId, UploadSession upload, String uploadedBy) {
    String id = Ids.newId();
    jdbc.update(
        """
        insert into work_record.wr_attachment(
          id, tenant_id, record_id, upload_id, object_key, file_name, content_type,
          size_bytes, status, uploaded_by)
        values (:id, :tenantId, :recordId, :uploadId, :objectKey, :fileName,
                :contentType, :sizeBytes, 'pending_scan', :uploadedBy)
        """,
        new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
            .addValue("id", id)
            .addValue("tenantId", tenantId)
            .addValue("recordId", recordId)
            .addValue("uploadId", upload.id())
            .addValue("objectKey", upload.objectKey())
            .addValue("fileName", upload.originalFileName())
            .addValue("contentType", upload.contentType())
            .addValue("sizeBytes", upload.declaredSizeBytes())
            .addValue("uploadedBy", uploadedBy));
    return find(tenantId, id).orElseThrow();
  }

  @Override
  public boolean markReady(String tenantId, String id, String sha256) {
    return updateStatus(tenantId, id, "ready", sha256);
  }

  @Override
  public boolean quarantine(String tenantId, String id) {
    return updateStatus(tenantId, id, "quarantined", null);
  }

  @Override
  public boolean markDeleted(String tenantId, String id) {
    return jdbc.update(
            "update work_record.wr_attachment set status='deleted', deleted_at=now(), updated_at=now() where tenant_id=:tenantId and id=:id and deleted_at is null",
            Map.of("tenantId", tenantId, "id", id))
        == 1;
  }

  private boolean updateStatus(String tenantId, String id, String status, String sha256) {
    return jdbc.update(
            "update work_record.wr_attachment set status=:status, sha256=:sha256, updated_at=now() where tenant_id=:tenantId and id=:id and status='pending_scan'",
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", id)
                .addValue("status", status)
                .addValue("sha256", sha256))
        == 1;
  }

  private WorkRecordAttachment map(ResultSet rs, int rowNumber) throws SQLException {
    return new WorkRecordAttachment(
        rs.getString("id"),
        rs.getString("record_id"),
        rs.getString("upload_id"),
        rs.getString("object_key"),
        rs.getString("file_name"),
        rs.getString("content_type"),
        rs.getLong("size_bytes"),
        rs.getString("sha256"),
        rs.getString("status"),
        rs.getString("uploaded_by"),
        rs.getObject("created_at", java.time.OffsetDateTime.class));
  }
}
