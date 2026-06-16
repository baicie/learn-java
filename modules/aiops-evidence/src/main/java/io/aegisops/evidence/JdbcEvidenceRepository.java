package io.aegisops.evidence;

import io.aegisops.evidence.dto.ChangeEvidence;
import io.aegisops.evidence.dto.ChangeEvidenceEvent;
import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.LogEvidence;
import io.aegisops.evidence.dto.LogPattern;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEvidenceRepository implements EvidenceRepository {
  private final JdbcTemplate jdbc;

  public JdbcEvidenceRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public LogEvidence queryLogs(EvidenceQueryRequest request, int maxPatterns) {
    if (request.primaryAssetId() == null || request.primaryAssetId().isBlank()) {
      return LogEvidence.unavailable("Primary asset id is empty.");
    }

    List<LogPattern> patterns =
        jdbc.query(
            """
            select severity,
                   min(message) as sample,
                   count(*) as count,
                   min(occurred_at) as first_seen_at,
                   max(occurred_at) as last_seen_at
            from log_event
            where tenant_id = ?
              and asset_id = ?
              and occurred_at >= ?
              and occurred_at <= ?
              and severity in ('error', 'fatal', 'critical', 'warn', 'warning')
            group by severity, left(message, 160)
            order by count(*) desc, max(occurred_at) desc
            limit ?
            """,
            (rs, rowNum) ->
                new LogPattern(
                    rs.getString("severity"),
                    rs.getString("sample"),
                    rs.getLong("count"),
                    rs.getObject("first_seen_at", OffsetDateTime.class),
                    rs.getObject("last_seen_at", OffsetDateTime.class)),
            request.tenantId(),
            request.primaryAssetId(),
            request.startedAt(),
            request.lastSeenAt(),
            maxPatterns);

    if (patterns.isEmpty()) {
      return LogEvidence.unavailable("No error log evidence found.");
    }

    return new LogEvidence(true, "", patterns);
  }

  @Override
  public ChangeEvidence queryChanges(EvidenceQueryRequest request, int maxChanges) {
    List<ChangeEvidenceEvent> events =
        jdbc.query(
            """
            select id, change_type, title, description, source, operator, risk_level, occurred_at
            from change_event
            where tenant_id = ?
              and (asset_id = ? or service_name in (?))
              and occurred_at >= ?
              and occurred_at <= ?
            order by occurred_at desc
            limit ?
            """,
            (rs, rowNum) ->
                new ChangeEvidenceEvent(
                    rs.getString("id"),
                    rs.getString("change_type"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("source"),
                    rs.getString("operator"),
                    rs.getString("risk_level"),
                    rs.getObject("occurred_at", OffsetDateTime.class)),
            request.tenantId(),
            request.primaryAssetId(),
            firstTitleOrEmpty(request),
            request.startedAt(),
            request.lastSeenAt(),
            maxChanges);

    if (events.isEmpty()) {
      return ChangeEvidence.unavailable("No change evidence found.");
    }

    return new ChangeEvidence(true, "", events);
  }

  private String firstTitleOrEmpty(EvidenceQueryRequest request) {
    if (request.normalizedAlertTitles().isEmpty()) {
      return "";
    }
    return request.normalizedAlertTitles().get(0);
  }
}
