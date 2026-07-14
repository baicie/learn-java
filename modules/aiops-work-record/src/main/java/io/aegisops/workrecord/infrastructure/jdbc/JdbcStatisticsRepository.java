package io.aegisops.workrecord.infrastructure.jdbc;

import io.aegisops.workrecord.application.command.StatisticsQuery;
import io.aegisops.workrecord.application.command.StatisticsResult;
import io.aegisops.workrecord.application.command.WorkloadSummary;
import io.aegisops.workrecord.application.port.StatisticsRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcStatisticsRepository implements StatisticsRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcStatisticsRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public StatisticsResult aggregate(
      String tenantId, StatisticsQuery query, StatisticalField field) {
    Map<String, Object> params = params(tenantId, query);
    Map<String, Object> totals =
        jdbc.queryForMap(
            """
        select count(*) as total_count,
               count(*) filter (where status = 'done') as completed_count,
               count(distinct coalesce(owner_id, creator_id)) as owner_count
          from work_record.wr_record
         where tenant_id = :tenantId and deleted_at is null
           and (:templateId is null or template_id = :templateId)
           and (:versionId is null or template_version_id = :versionId)
           and record_time >= :fromTime and record_time < :toTime
        """,
            params);
    return new StatisticsResult(
        number(totals.get("total_count")),
        number(totals.get("completed_count")),
        number(totals.get("owner_count")),
        groupedSeries(query.groupBy(), params),
        field.present() ? numericAggregate(field, params) : null);
  }

  @Override
  public WorkloadSummary workload(
      String tenantId,
      String templateId,
      java.time.OffsetDateTime from,
      java.time.OffsetDateTime to,
      int workdayCount,
      StatisticalField field) {
    Map<String, Object> params = new HashMap<>();
    params.put("tenantId", tenantId);
    params.put("templateId", blankToNull(templateId));
    params.put("fromTime", from);
    params.put("toTime", to);
    params.put("workdays", Math.max(workdayCount, 1));
    params.put("fieldCode", field.fieldCode());
    String workload =
        field.present()
            ? "coalesce(sum(case when jsonb_typeof(r.custom_data_json -> :fieldCode) = 'number' "
                + "then (r.custom_data_json ->> :fieldCode)::numeric else 0 end), 0)"
            : "0::numeric";
    List<WorkloadSummary.UserWorkload> rows =
        jdbc.query(
            "select coalesce(r.owner_id, r.creator_id) as user_id, "
                + "coalesce(u.display_name, coalesce(r.owner_id, r.creator_id)) as display_name, "
                + "count(*) as record_count, count(*) filter (where r.status = 'done') as completed_count, "
                + workload
                + " as workload, count(*)::numeric / :workdays as records_per_workday "
                + "from work_record.wr_record r left join public.sys_user u "
                + "on u.tenant_id = r.tenant_id and u.id = coalesce(r.owner_id, r.creator_id) "
                + "where r.tenant_id = :tenantId and r.deleted_at is null "
                + "and (:templateId is null or r.template_id = :templateId) "
                + "and r.record_time >= :fromTime and r.record_time < :toTime "
                + "group by coalesce(r.owner_id, r.creator_id), u.display_name "
                + "order by workload desc, record_count desc, user_id",
            params,
            (rs, rowNum) ->
                new WorkloadSummary.UserWorkload(
                    rs.getString("user_id"), rs.getString("display_name"),
                    rs.getLong("record_count"), rs.getLong("completed_count"),
                    rs.getBigDecimal("workload"), rs.getBigDecimal("records_per_workday")));
    return new WorkloadSummary(workdayCount, rows);
  }

  private List<StatisticsResult.SeriesPoint> groupedSeries(
      String groupBy, Map<String, Object> params) {
    String expression = Grouping.from(groupBy).expression;
    String sql =
        "select "
            + expression
            + " as group_key, count(*) as item_count "
            + "from work_record.wr_record where tenant_id = :tenantId and deleted_at is null "
            + "and (:templateId is null or template_id = :templateId) "
            + "and (:versionId is null or template_version_id = :versionId) "
            + "and record_time >= :fromTime and record_time < :toTime "
            + "group by "
            + expression
            + " order by group_key";
    return jdbc.query(
        sql,
        params,
        (rs, rowNum) ->
            new StatisticsResult.SeriesPoint(
                rs.getString("group_key"),
                rs.getString("group_key"),
                rs.getLong("item_count"),
                null));
  }

  private StatisticsResult.FieldAggregate numericAggregate(
      StatisticalField field, Map<String, Object> params) {
    params.put("fieldCode", field.fieldCode());
    String numeric =
        "case when jsonb_typeof(custom_data_json -> :fieldCode) = 'number' "
            + "then (custom_data_json ->> :fieldCode)::numeric end";
    Map<String, Object> row =
        jdbc.queryForMap(
            "select coalesce(sum("
                + numeric
                + "), 0) as value_sum, avg("
                + numeric
                + ") as value_avg, min("
                + numeric
                + ") as value_min, max("
                + numeric
                + ") as value_max, count("
                + numeric
                + ") as value_count "
                + "from work_record.wr_record where tenant_id = :tenantId and deleted_at is null "
                + "and (:templateId is null or template_id = :templateId) "
                + "and (:versionId is null or template_version_id = :versionId) "
                + "and record_time >= :fromTime and record_time < :toTime",
            params);
    return new StatisticsResult.FieldAggregate(
        field.fieldCode(),
        decimal(row.get("value_sum")),
        decimal(row.get("value_avg")),
        decimal(row.get("value_min")),
        decimal(row.get("value_max")),
        number(row.get("value_count")));
  }

  private static Map<String, Object> params(String tenantId, StatisticsQuery query) {
    Map<String, Object> params = new HashMap<>();
    params.put("tenantId", tenantId);
    params.put("templateId", blankToNull(query.templateId()));
    params.put("versionId", blankToNull(query.templateVersionId()));
    params.put("fromTime", query.from());
    params.put("toTime", query.to());
    return params;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static long number(Object value) {
    return value instanceof Number number ? number.longValue() : 0L;
  }

  private static BigDecimal decimal(Object value) {
    return value instanceof BigDecimal decimal ? decimal : null;
  }

  private enum Grouping {
    DAY("to_char(record_time at time zone 'UTC', 'YYYY-MM-DD')"),
    MONTH("to_char(record_time at time zone 'UTC', 'YYYY-MM')"),
    STATUS("status"),
    OWNER("coalesce(owner_id, creator_id)"),
    TEMPLATE("template_id");

    private final String expression;

    Grouping(String expression) {
      this.expression = expression;
    }

    static Grouping from(String value) {
      try {
        return value == null || value.isBlank() ? DAY : valueOf(value.trim().toUpperCase());
      } catch (IllegalArgumentException ex) {
        throw new IllegalArgumentException("unsupported statistics grouping", ex);
      }
    }
  }
}
