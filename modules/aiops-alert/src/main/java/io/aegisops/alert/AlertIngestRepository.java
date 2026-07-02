package io.aegisops.alert;

import io.aegisops.common.id.Ids;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Repository for alert event upsert with idempotent handling. */
@Repository
public class AlertIngestRepository {
  private final JdbcTemplate jdbc;

  public AlertIngestRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public AlertIngestResult upsert(
      String tenantId,
      AlertIngestRequest request,
      String fingerprint,
      String aggregationKey,
      String labelsJson,
      String rawJson) {
    String id = Ids.newId();
    String source = normalize(request.source(), "webhook");
    String status = normalizeStatus(request.status());
    String severity = normalize(request.severity(), "info");
    OffsetDateTime startsAt = request.startsAt() == null ? OffsetDateTime.now() : request.startsAt();

    String sql =
        """
            insert into alert_event(
              id, tenant_id, source, source_event_id, severity, title, description,
              asset_id, entity_type, entity_name, labels, starts_at, ends_at, status,
              raw_payload, fingerprint, aggregation_key, updated_at, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?::jsonb, ?, ?, now(), now())
            on conflict (tenant_id, source, source_event_id) where source_event_id is not null
            do update set
              severity = excluded.severity,
              title = excluded.title,
              description = excluded.description,
              labels = excluded.labels,
              starts_at = excluded.starts_at,
              ends_at = excluded.ends_at,
              status = excluded.status,
              raw_payload = excluded.raw_payload,
              fingerprint = excluded.fingerprint,
              aggregation_key = excluded.aggregation_key,
              updated_at = now()
            returning id, xmax = 0 as created
            """;

    return jdbc.queryForObject(
        sql,
        (rs, rowNum) ->
            new AlertIngestResult(
                rs.getString("id"), rs.getBoolean("created"), fingerprint, aggregationKey),
        id,
        tenantId,
        source,
        request.sourceEventId(),
        severity,
        request.title(),
        request.description(),
        request.assetId(),
        request.entityType(),
        request.entityName(),
        labelsJson,
        startsAt,
        request.endsAt(),
        status,
        rawJson,
        fingerprint,
        aggregationKey);
  }

  private String normalize(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim().toLowerCase();
  }

  private String normalizeStatus(String status) {
    if (status == null || status.isBlank()) {
      return "open";
    }
    return "resolved".equalsIgnoreCase(status) ? "resolved" : "open";
  }
}
