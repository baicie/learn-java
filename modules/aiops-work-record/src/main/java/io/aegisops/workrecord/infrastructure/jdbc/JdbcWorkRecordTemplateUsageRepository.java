package io.aegisops.workrecord.infrastructure.jdbc;

import io.aegisops.workrecord.application.port.WorkRecordTemplateUsageRepository;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkRecordTemplateUsageRepository implements WorkRecordTemplateUsageRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcWorkRecordTemplateUsageRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public long countRecordsByTemplate(String tenantId, String templateId) {
    Long value =
        jdbc.queryForObject(
            """
            select count(*)
              from work_record.wr_record
             where tenant_id = :tenantId
               and template_id = :templateId
               and deleted_at is null
            """,
            Map.of("tenantId", tenantId, "templateId", templateId),
            Long.class);
    return value == null ? 0L : value;
  }

  @Override
  public long countRecordsByTemplateVersion(String tenantId, String templateVersionId) {
    Long value =
        jdbc.queryForObject(
            """
            select count(*)
              from work_record.wr_record
             where tenant_id = :tenantId
               and template_version_id = :templateVersionId
               and deleted_at is null
            """,
            Map.of("tenantId", tenantId, "templateVersionId", templateVersionId),
            Long.class);
    return value == null ? 0L : value;
  }
}