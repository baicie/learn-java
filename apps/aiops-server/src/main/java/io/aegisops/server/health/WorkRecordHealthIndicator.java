package io.aegisops.server.health;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 工作记录模块的就绪探针。
 *
 * <p>检查项：
 *
 * <ol>
 *   <li>数据库连通。
 *   <li>所有关键表存在。
 *   <li>（生产可配置）所有关键索引存在且 {@code indisvalid = true} 且 {@code indisready = true}。
 *       仅仅查 {@code pg_indexes} 无法发现 {@code CREATE INDEX CONCURRENTLY} 失败后留下的
 *       无效索引条目；这些索引必须被清理后再重建。
 *   <li>关键 CHECK 约束已通过 {@code VALIDATE CONSTRAINT} 校验（{@code convalidated = true}）。
 * </ol>
 *
 * <p>任意一项失败，返回 {@link Health#down()}，阻止 Kubernetes / LB 把流量打进来。
 */
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

  private static final List<String> REQUIRED_CONSTRAINTS =
      List.of(
          "ck_wr_template_draft_schema_size",
          "ck_wr_template_version_schema_size",
          "ck_wr_record_custom_size");

  private final NamedParameterJdbcTemplate jdbc;
  private final boolean requireIndexes;
  private final boolean requireValidatedConstraints;

  public WorkRecordHealthIndicator(
      NamedParameterJdbcTemplate jdbc,
      @Value("${aiops.work-record.health.require-indexes:false}") boolean requireIndexes,
      @Value("${aiops.work-record.health.require-validated-constraints:false}")
          boolean requireValidatedConstraints) {
    this.jdbc = jdbc;
    this.requireIndexes = requireIndexes;
    this.requireValidatedConstraints = requireValidatedConstraints;
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

      Map<String, Object> details = new LinkedHashMap<>();
      details.put("tables", REQUIRED_TABLES.size());

      if (requireIndexes) {
        List<String> missingIndexes =
            REQUIRED_INDEXES.stream().filter(index -> !isValidIndex(index)).toList();
        details.put("indexesRequired", true);
        details.put("indexesChecked", REQUIRED_INDEXES.size());

        if (!missingIndexes.isEmpty()) {
          details.put("missingOrInvalidIndexes", missingIndexes);
          return Health.down().withDetails(details).build();
        }
      } else {
        details.put("indexesRequired", false);
      }

      if (requireValidatedConstraints) {
        List<String> unvalidatedConstraints =
            REQUIRED_CONSTRAINTS.stream()
                .filter(constraint -> !isValidatedConstraint(constraint))
                .toList();
        details.put("constraintsRequired", true);
        details.put("constraintsChecked", REQUIRED_CONSTRAINTS.size());

        if (!unvalidatedConstraints.isEmpty()) {
          details.put("unvalidatedConstraints", unvalidatedConstraints);
          return Health.down().withDetails(details).build();
        }
      } else {
        details.put("constraintsRequired", false);
      }

      return Health.up().withDetails(details).build();
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

  private boolean isValidIndex(String indexName) {
    Boolean value =
        jdbc.queryForObject(
            """
            select exists (
                select 1
                from pg_class index_class
                join pg_namespace namespace
                  on namespace.oid = index_class.relnamespace
                join pg_index index_state
                  on index_state.indexrelid = index_class.oid
                where namespace.nspname = 'work_record'
                  and index_class.relname = :indexName
                  and index_state.indisvalid = true
                  and index_state.indisready = true
            )
            """,
            Map.of("indexName", indexName),
            Boolean.class);

    return Boolean.TRUE.equals(value);
  }

  private boolean isValidatedConstraint(String constraintName) {
    Boolean value =
        jdbc.queryForObject(
            """
            select exists (
                select 1
                from pg_constraint
                where conname = :constraintName
                  and convalidated = true
            )
            """,
            Map.of("constraintName", constraintName),
            Boolean.class);

    return Boolean.TRUE.equals(value);
  }
}
