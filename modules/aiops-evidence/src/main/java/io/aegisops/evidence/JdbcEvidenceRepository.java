package io.aegisops.evidence;

import io.aegisops.evidence.dto.ChangeEvidence;
import io.aegisops.evidence.dto.ChangeEvidenceEvent;
import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.LogEvidence;
import io.aegisops.evidence.dto.LogPattern;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEvidenceRepository implements EvidenceRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcEvidenceRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public LogEvidence queryLogs(EvidenceQueryRequest request, int maxPatterns) {
    if (isBlank(request.primaryAssetId()) && request.normalizedServiceNames().isEmpty()) {
      return LogEvidence.unavailable("Primary asset id and service names are empty.");
    }

    MapSqlParameterSource params =
        baseParams(request)
            .addValue("maxPatterns", maxPatterns)
            .addValue("serviceNames", request.normalizedServiceNames());

    List<String> filters = new ArrayList<>();
    filters.add("tenant_id = :tenantId");
    filters.add("occurred_at >= :startedAt");
    filters.add("occurred_at <= :lastSeenAt");
    filters.add("severity in ('error', 'fatal', 'critical', 'warn', 'warning')");

    List<String> entityFilters = new ArrayList<>();
    if (!isBlank(request.primaryAssetId())) {
      entityFilters.add("asset_id = :primaryAssetId");
    }
    if (!request.normalizedServiceNames().isEmpty()) {
      entityFilters.add("service_name in (:serviceNames)");
    }

    filters.add("(" + String.join(" or ", entityFilters) + ")");

    String sql =
        """
        select severity,
               min(message) as sample,
               count(*) as count,
               min(occurred_at) as first_seen_at,
               max(occurred_at) as last_seen_at
        from log_event
        where %s
        group by severity, left(message, 160)
        order by count(*) desc, max(occurred_at) desc
        limit :maxPatterns
        """
            .formatted(String.join("\n  and ", filters));

    List<LogPattern> patterns =
        jdbc.query(
            sql,
            params,
            (rs, rowNum) ->
                new LogPattern(
                    rs.getString("severity"),
                    rs.getString("sample"),
                    rs.getLong("count"),
                    rs.getObject("first_seen_at", OffsetDateTime.class),
                    rs.getObject("last_seen_at", OffsetDateTime.class)));

    if (patterns.isEmpty()) {
      return LogEvidence.unavailable("No error log evidence found.");
    }

    return new LogEvidence(true, "", patterns);
  }

  @Override
  public ChangeEvidence queryChanges(EvidenceQueryRequest request, int maxChanges) {
    if (isBlank(request.primaryAssetId()) && request.normalizedServiceNames().isEmpty()) {
      return ChangeEvidence.unavailable("Primary asset id and service names are empty.");
    }

    MapSqlParameterSource params =
        baseParams(request)
            .addValue("maxChanges", maxChanges)
            .addValue("serviceNames", request.normalizedServiceNames());

    List<String> filters = new ArrayList<>();
    filters.add("tenant_id = :tenantId");
    filters.add("occurred_at >= :startedAt");
    filters.add("occurred_at <= :lastSeenAt");

    List<String> entityFilters = new ArrayList<>();
    if (!isBlank(request.primaryAssetId())) {
      entityFilters.add("asset_id = :primaryAssetId");
    }
    if (!request.normalizedServiceNames().isEmpty()) {
      entityFilters.add("service_name in (:serviceNames)");
    }

    filters.add("(" + String.join(" or ", entityFilters) + ")");

    String sql =
        """
        select id, change_type, title, description, source, operator, risk_level, occurred_at
        from change_event
        where %s
        order by occurred_at desc
        limit :maxChanges
        """
            .formatted(String.join("\n  and ", filters));

    List<ChangeEvidenceEvent> events =
        jdbc.query(
            sql,
            params,
            (rs, rowNum) ->
                new ChangeEvidenceEvent(
                    rs.getString("id"),
                    rs.getString("change_type"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("source"),
                    rs.getString("operator"),
                    rs.getString("risk_level"),
                    rs.getObject("occurred_at", OffsetDateTime.class)));

    if (events.isEmpty()) {
      return ChangeEvidence.unavailable("No change evidence found.");
    }

    return new ChangeEvidence(true, "", events);
  }

  private MapSqlParameterSource baseParams(EvidenceQueryRequest request) {
    return new MapSqlParameterSource()
        .addValue("tenantId", request.tenantId())
        .addValue("primaryAssetId", request.primaryAssetId())
        .addValue("startedAt", request.startedAt())
        .addValue("lastSeenAt", request.lastSeenAt());
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
