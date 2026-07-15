package io.aegisops.workrecord.infrastructure.jdbc;

import io.aegisops.common.api.PageResult;
import io.aegisops.workrecord.application.port.AsyncJobRepository;
import io.aegisops.workrecord.domain.model.AsyncJob;
import io.aegisops.workrecord.domain.model.AsyncJobStatus;
import io.aegisops.workrecord.domain.model.AsyncJobType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAsyncJobRepository implements AsyncJobRepository {
  private static final String COLUMNS =
      """
      id, tenant_id, job_type, status, requested_by, request_json::text, result_json::text,
      source_object_key, total_count, processed_count, success_count, failure_count, result_object_key,
      result_file_name, result_content_type, error_object_key, idempotency_key, error_message,
      row_version, started_at, finished_at,
      expires_at, created_at, updated_at
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final RowMapper<AsyncJob> rowMapper = JdbcAsyncJobRepository::mapRow;

  public JdbcAsyncJobRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public PageResult<AsyncJob> page(
      String tenantId,
      String requestedBy,
      AsyncJobStatus status,
      AsyncJobType jobType,
      int page,
      int size) {
    StringBuilder where = new StringBuilder(" where tenant_id = :tenantId");
    MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenantId);
    if (requestedBy != null) {
      where.append(" and requested_by = :requestedBy");
      params.addValue("requestedBy", requestedBy);
    }
    if (status != null) {
      where.append(" and status = :status");
      params.addValue("status", status.storageValue());
    }
    if (jobType != null) {
      where.append(" and job_type = :jobType");
      params.addValue("jobType", jobType.storageValue());
    }

    Long total =
        jdbc.queryForObject(
            "select count(*) from work_record.wr_async_job" + where, params, Long.class);
    params.addValue("limit", size).addValue("offset", (long) (page - 1) * size);
    List<AsyncJob> items =
        jdbc.query(
            "select "
                + COLUMNS
                + " from work_record.wr_async_job"
                + where
                + " order by created_at desc, id desc limit :limit offset :offset",
            params,
            rowMapper);
    return new PageResult<>(total == null ? 0 : total, page, size, items);
  }

  @Override
  public Optional<AsyncJob> findById(String tenantId, String id) {
    List<AsyncJob> jobs =
        jdbc.query(
            "select "
                + COLUMNS
                + " from work_record.wr_async_job where tenant_id = :tenantId and id = :id",
            Map.of("tenantId", tenantId, "id", id),
            rowMapper);
    return jobs.stream().findFirst();
  }

  @Override
  public Optional<AsyncJob> cancelQueued(String tenantId, String id, OffsetDateTime cancelledAt) {
    List<AsyncJob> jobs =
        jdbc.query(
            """
            update work_record.wr_async_job
               set status = 'cancelled', finished_at = :cancelledAt,
                   updated_at = :cancelledAt, row_version = row_version + 1
             where tenant_id = :tenantId and id = :id and status = 'queued'
            returning
            """
                + COLUMNS,
            new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", id)
                .addValue("cancelledAt", cancelledAt),
            rowMapper);
    return jobs.stream().findFirst();
  }

  @Override
  public AsyncJob insert(AsyncJob job) {
    jdbc.update(
        """
        insert into work_record.wr_async_job(
          id, tenant_id, job_type, status, requested_by, request_json, result_json, source_object_key,
          total_count, processed_count, success_count, failure_count, result_object_key,
          result_file_name, result_content_type, error_object_key, idempotency_key, error_message,
          row_version, started_at, finished_at,
          expires_at, created_at, updated_at)
        values (
          :id, :tenantId, :jobType, :status, :requestedBy, cast(:requestJson as jsonb),
          cast(:resultJson as jsonb), :sourceObjectKey, :totalCount, :processedCount, :successCount,
          :failureCount, :resultObjectKey, :resultFileName, :resultContentType, :errorObjectKey,
          :idempotencyKey, :errorMessage, :rowVersion,
          :startedAt, :finishedAt, :expiresAt, :createdAt, :updatedAt)
        """,
        parameters(job));
    return findById(job.tenantId(), job.id()).orElseThrow();
  }

  @Override
  public Optional<AsyncJob> claimQueued(String tenantId, String id, OffsetDateTime startedAt) {
    List<AsyncJob> jobs =
        jdbc.query(
            """
            update work_record.wr_async_job
               set status = 'processing', started_at = :startedAt, updated_at = :startedAt,
                   row_version = row_version + 1
             where tenant_id = :tenantId and id = :id and status = 'queued'
            returning
            """
                + COLUMNS,
            new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", id)
                .addValue("startedAt", startedAt),
            rowMapper);
    return jobs.stream().findFirst();
  }

  @Override
  public boolean updateProgress(
      String tenantId, String id, JobProgress progress, OffsetDateTime now) {
    return jdbc.update(
            """
            update work_record.wr_async_job
               set total_count = :total, processed_count = :processed,
                   success_count = :success, failure_count = :failure,
                   updated_at = :now, row_version = row_version + 1
             where tenant_id = :tenantId and id = :id and status = 'processing'
            """,
            progressParameters(tenantId, id, progress, now))
        == 1;
  }

  @Override
  public boolean complete(
      String tenantId, String id, JobCompletion completion, OffsetDateTime now) {
    MapSqlParameterSource params = progressParameters(tenantId, id, completion.progress(), now);
    params
        .addValue("status", completion.status().storageValue())
        .addValue("resultJson", completion.resultJson())
        .addValue("resultObjectKey", completion.resultObjectKey())
        .addValue("resultFileName", completion.resultFileName())
        .addValue("resultContentType", completion.resultContentType())
        .addValue("errorObjectKey", completion.errorObjectKey());
    return jdbc.update(
            """
            update work_record.wr_async_job
               set status = :status, result_json = cast(:resultJson as jsonb),
                   result_object_key = :resultObjectKey, result_file_name = :resultFileName,
                   result_content_type = :resultContentType, error_object_key = :errorObjectKey,
                   total_count = :total, processed_count = :processed,
                   success_count = :success, failure_count = :failure,
                   error_message = null, finished_at = :now, updated_at = :now,
                   row_version = row_version + 1
             where tenant_id = :tenantId and id = :id and status = 'processing'
            """,
            params)
        == 1;
  }

  @Override
  public boolean fail(String tenantId, String id, String errorMessage, OffsetDateTime now) {
    return jdbc.update(
            """
            update work_record.wr_async_job
               set status = 'failed', error_message = :errorMessage,
                   finished_at = :now, updated_at = :now, row_version = row_version + 1
             where tenant_id = :tenantId and id = :id and status = 'processing'
            """,
            new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", id)
                .addValue("errorMessage", errorMessage)
                .addValue("now", now))
        == 1;
  }

  private static MapSqlParameterSource progressParameters(
      String tenantId, String id, JobProgress progress, OffsetDateTime now) {
    return new MapSqlParameterSource()
        .addValue("tenantId", tenantId)
        .addValue("id", id)
        .addValue("total", progress.totalCount())
        .addValue("processed", progress.processedCount())
        .addValue("success", progress.successCount())
        .addValue("failure", progress.failureCount())
        .addValue("now", now);
  }

  private static MapSqlParameterSource parameters(AsyncJob job) {
    return new MapSqlParameterSource()
        .addValue("id", job.id())
        .addValue("tenantId", job.tenantId())
        .addValue("jobType", job.jobType().storageValue())
        .addValue("status", job.status().storageValue())
        .addValue("requestedBy", job.requestedBy())
        .addValue("requestJson", job.requestJson())
        .addValue("resultJson", job.resultJson())
        .addValue("sourceObjectKey", job.sourceObjectKey())
        .addValue("totalCount", job.totalCount())
        .addValue("processedCount", job.processedCount())
        .addValue("successCount", job.successCount())
        .addValue("failureCount", job.failureCount())
        .addValue("resultObjectKey", job.resultObjectKey())
        .addValue("resultFileName", job.resultFileName())
        .addValue("resultContentType", job.resultContentType())
        .addValue("errorObjectKey", job.errorObjectKey())
        .addValue("idempotencyKey", job.idempotencyKey())
        .addValue("errorMessage", job.errorMessage())
        .addValue("rowVersion", job.rowVersion())
        .addValue("startedAt", job.startedAt())
        .addValue("finishedAt", job.finishedAt())
        .addValue("expiresAt", job.expiresAt())
        .addValue("createdAt", job.createdAt())
        .addValue("updatedAt", job.updatedAt());
  }

  private static AsyncJob mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new AsyncJob(
        rs.getString("id"),
        rs.getString("tenant_id"),
        AsyncJobType.fromStorage(rs.getString("job_type")),
        AsyncJobStatus.fromStorage(rs.getString("status")),
        rs.getString("requested_by"),
        rs.getString("request_json"),
        rs.getString("result_json"),
        rs.getString("source_object_key"),
        rs.getInt("total_count"),
        rs.getInt("processed_count"),
        rs.getInt("success_count"),
        rs.getInt("failure_count"),
        rs.getString("result_object_key"),
        rs.getString("result_file_name"),
        rs.getString("result_content_type"),
        rs.getString("error_object_key"),
        rs.getString("idempotency_key"),
        rs.getString("error_message"),
        rs.getLong("row_version"),
        rs.getObject("started_at", OffsetDateTime.class),
        rs.getObject("finished_at", OffsetDateTime.class),
        rs.getObject("expires_at", OffsetDateTime.class),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }
}
