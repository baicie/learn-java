package io.aegisops.workrecord.infrastructure.jdbc;

import io.aegisops.workrecord.application.port.AiPeriodReportRepository;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiPeriodReportRepository implements AiPeriodReportRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcAiPeriodReportRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public PeriodSnapshot snapshot(
      String tenantId, OffsetDateTime from, OffsetDateTime to, int sampleLimit) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("tenantId", tenantId);
    params.put("fromTime", from);
    params.put("toTime", to);
    params.put("sampleLimit", Math.max(0, sampleLimit));
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            with statistics as (
              select case
                       when grouping(status) = 0 then 'status'
                       when grouping(owner_key) = 0 then 'owner'
                       else 'total'
                     end as statistic_type,
                     case
                       when grouping(status) = 0 then status
                       when grouping(owner_key) = 0 then owner_key
                       else ''
                     end as statistic_key,
                     count(*) as item_count
                from (
                  select status, coalesce(owner_id, '') as owner_key
                    from work_record.wr_record
                   where tenant_id = :tenantId
                     and deleted_at is null
                     and record_time >= :fromTime
                     and record_time < :toTime
                ) period_records
               group by grouping sets ((), (status), (owner_key))
            ), ranked as (
              select statistics.*,
                     case when statistic_type = 'owner'
                       then row_number() over (partition by statistic_type
                         order by item_count desc, statistic_key)
                       else 0
                     end as owner_rank
                from statistics
            )
            select statistic_type, statistic_key, item_count
              from ranked
             where statistic_type <> 'owner' or owner_rank <= 10
             order by case statistic_type when 'total' then 0 when 'status' then 1 else 2 end,
                      case when statistic_type = 'owner' then item_count end desc,
                      statistic_key
            """,
            params);
    Aggregates aggregates = aggregates(rows);
    List<WorkRecord> samples =
        jdbc.query(
            """
            select id, tenant_id, template_id, template_version_id, title, status,
                   owner_id, creator_id, record_time,
                   builtin_data_json::text, custom_data_json::text,
                   row_version, created_at, updated_at, deleted_at
              from work_record.wr_record
             where tenant_id = :tenantId
               and deleted_at is null
               and record_time >= :fromTime
               and record_time < :toTime
             order by record_time asc, id asc
             limit :sampleLimit
            """,
            params,
            (rs, rowNum) -> mapRecord(rs));
    return new PeriodSnapshot(
        aggregates.recordCount(), aggregates.statusCounts(), aggregates.ownerCounts(), samples);
  }

  private static Aggregates aggregates(List<Map<String, Object>> rows) {
    long recordCount = 0L;
    List<Count> statusCounts = new ArrayList<>();
    List<Count> ownerCounts = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      String type = String.valueOf(row.get("statistic_type"));
      String key = String.valueOf(row.get("statistic_key"));
      long value = number(row.get("item_count"));
      if ("total".equals(type)) {
        recordCount = value;
      } else if ("status".equals(type)) {
        statusCounts.add(new Count(key, value));
      } else if ("owner".equals(type)) {
        ownerCounts.add(new Count(key, value));
      }
    }
    return new Aggregates(recordCount, statusCounts, ownerCounts);
  }

  private static WorkRecord mapRecord(ResultSet rs) throws SQLException {
    return new WorkRecord(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("template_id"),
        rs.getString("template_version_id"),
        rs.getString("title"),
        RecordStatus.from(rs.getString("status")),
        rs.getString("owner_id"),
        rs.getString("creator_id"),
        rs.getObject("record_time", OffsetDateTime.class),
        rs.getString("builtin_data_json"),
        rs.getString("custom_data_json"),
        rs.getInt("row_version"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class),
        rs.getObject("deleted_at", OffsetDateTime.class));
  }

  private static long number(Object value) {
    return value instanceof Number number ? number.longValue() : 0L;
  }

  private record Aggregates(long recordCount, List<Count> statusCounts, List<Count> ownerCounts) {}
}
