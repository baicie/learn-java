package io.aegisops.workrecord.infrastructure.jdbc;

import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.application.port.AsyncJobItemRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAsyncJobItemRepository implements AsyncJobItemRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcAsyncJobItemRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean begin(
      String tenantId, String jobId, String itemKey, int rowNumber, OffsetDateTime now) {
    return jdbc.update(
            """
            insert into work_record.wr_async_job_item(
              id, tenant_id, job_id, item_key, row_number, status, created_at, updated_at)
            values (:id, :tenantId, :jobId, :itemKey, :rowNumber, 'pending', :now, :now)
            on conflict (job_id, item_key) do nothing
            """,
            Map.of(
                "id", Ids.newId(),
                "tenantId", tenantId,
                "jobId", jobId,
                "itemKey", itemKey,
                "rowNumber", rowNumber,
                "now", now))
        == 1;
  }

  @Override
  public boolean succeed(
      String tenantId, String jobId, String itemKey, String resourceId, OffsetDateTime now) {
    return jdbc.update(
            """
            update work_record.wr_async_job_item
               set status = 'succeeded', resource_id = :resourceId, updated_at = :now
             where tenant_id = :tenantId and job_id = :jobId and item_key = :itemKey
               and status = 'pending'
            """,
            Map.of(
                "tenantId", tenantId,
                "jobId", jobId,
                "itemKey", itemKey,
                "resourceId", resourceId,
                "now", now))
        == 1;
  }
}
