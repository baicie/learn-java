package io.aegisops.server.health;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component("workRecord")
public class WorkRecordHealthIndicator implements HealthIndicator {

  private static final List<String> REQUIRED_TABLES =
      List.of(
          "work_record.wr_template",
          "work_record.wr_template_version",
          "work_record.wr_template_field",
          "work_record.wr_record");

  private static final List<String> REQUIRED_INDEXES =
      List.of(
          "idx_wr_record_tenant_time_live",
          "idx_wr_record_tenant_template_time_live",
          "idx_wr_record_tenant_owner_time_live",
          "idx_wr_record_tenant_creator_time_live",
          "idx_wr_record_custom_jsonb_live");

  private final NamedParameterJdbcTemplate jdbc;
  private final boolean requireIndexes;

  public WorkRecordHealthIndicator(
      NamedParameterJdbcTemplate jdbc,
      @Value("${aiops.work-record.health.require-indexes:false}") boolean requireIndexes) {
    this.jdbc = jdbc;
    this.requireIndexes = requireIndexes;
  }

  @Override
  public Health health() {
    try {
      jdbc.queryForObject("select 1", Map.of(), Integer.class);

      List<String> missingTables =
          REQUIRED_TABLES.stream().filter(table -> !relationExists(table)).toList();

      if (!missingTables.isEmpty()) {
        return Health.down().withDetail("missingTables", missingTables).build();
      }

      if (requireIndexes) {
        List<String> missingIndexes =
            REQUIRED_INDEXES.stream().filter(index -> !indexExists(index)).toList();

        if (!missingIndexes.isEmpty()) {
          return Health.down().withDetail("missingIndexes", missingIndexes).build();
        }
      }

      return Health.up()
          .withDetail("tables", REQUIRED_TABLES.size())
          .withDetail("indexesRequired", requireIndexes)
          .build();
    } catch (RuntimeException ex) {
      return Health.down(ex).build();
    }
  }

  private boolean relationExists(String qualifiedName) {
    Boolean value =
        jdbc.queryForObject(
            """
            select
                to_regclass(:relationName) is not null
            """,
            Map.of("relationName", qualifiedName),
            Boolean.class);

    return Boolean.TRUE.equals(value);
  }

  private boolean indexExists(String indexName) {
    Boolean value =
        jdbc.queryForObject(
            """
            select exists (
                select 1
                from pg_indexes
                where schemaname = 'work_record'
                  and indexname = :indexName
            )
            """,
            Map.of("indexName", indexName),
            Boolean.class);

    return Boolean.TRUE.equals(value);
  }
}