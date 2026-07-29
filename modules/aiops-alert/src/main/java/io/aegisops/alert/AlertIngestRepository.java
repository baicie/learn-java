package io.aegisops.alert;

import io.aegisops.common.id.Ids;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AlertIngestRepository {
  private static final String UPSERT_SQL =
      """
          insert into alert_event(
            id, tenant_id, source, source_event_id, severity, title, description,
            asset_id, entity_type, entity_name, labels, starts_at, ends_at, status,
            raw_payload, fingerprint, aggregation_key, updated_at, created_at
          ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?::jsonb, ?, ?, now(), now())
          on conflict (tenant_id, source, source_event_id) where source_event_id is not null
          do update set
            severity = case
              when alert_event.status = 'resolved' and excluded.status = 'open'
                then alert_event.severity
              else excluded.severity
            end,
            title = case
              when alert_event.status = 'resolved' and excluded.status = 'open'
                then alert_event.title
              else excluded.title
            end,
            description = case
              when alert_event.status = 'resolved' and excluded.status = 'open'
                then alert_event.description
              else excluded.description
            end,
            asset_id = case
              when alert_event.status = 'resolved' and excluded.status = 'open'
                then alert_event.asset_id
              else excluded.asset_id
            end,
            entity_type = case
              when alert_event.status = 'resolved' and excluded.status = 'open'
                then alert_event.entity_type
              else excluded.entity_type
            end,
            entity_name = case
              when alert_event.status = 'resolved' and excluded.status = 'open'
                then alert_event.entity_name
              else excluded.entity_name
            end,
            labels = case
              when alert_event.status = 'resolved' and excluded.status = 'open'
                then alert_event.labels
              else excluded.labels
            end,
            starts_at = case
              when alert_event.status = 'resolved' and excluded.status = 'open'
                then alert_event.starts_at
              else least(alert_event.starts_at, excluded.starts_at)
            end,
            ends_at = case
              when alert_event.status = 'resolved' and excluded.status = 'open'
                then alert_event.ends_at
              else excluded.ends_at
            end,
            status = case
              when alert_event.status = 'resolved' and excluded.status = 'open'
                then alert_event.status
              else excluded.status
            end,
            raw_payload = case
              when alert_event.status = 'resolved' and excluded.status = 'open'
                then alert_event.raw_payload
              else excluded.raw_payload
            end,
            fingerprint = case
              when alert_event.status = 'resolved' and excluded.status = 'open'
                then alert_event.fingerprint
              else excluded.fingerprint
            end,
            aggregation_key = case
              when alert_event.status = 'resolved' and excluded.status = 'open'
                then alert_event.aggregation_key
              else excluded.aggregation_key
            end,
            updated_at = case
              when alert_event.status = 'resolved' and excluded.status = 'open'
                then alert_event.updated_at
              else now()
            end
          returning id, xmax = 0 as created, status, fingerprint, aggregation_key
          """;

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
    String source = normalizeText(request.source(), "webhook");
    String sourceEventId = effectiveSourceEventId(request.sourceEventId(), fingerprint);
    String status = normalizeStatus(request.status());
    String severity = normalizeSeverity(request.severity());
    OffsetDateTime startsAt =
        request.startsAt() == null ? OffsetDateTime.now() : request.startsAt();

    return jdbc.queryForObject(
        UPSERT_SQL,
        (rs, rowNum) ->
            new AlertIngestResult(
                rs.getString("id"),
                rs.getBoolean("created"),
                rs.getString("status"),
                rs.getString("fingerprint"),
                rs.getString("aggregation_key")),
        id,
        tenantId,
        source,
        sourceEventId,
        severity,
        request.title().trim(),
        blankToNull(request.description()),
        blankToNull(request.assetId()),
        blankToNull(request.entityType()),
        blankToNull(request.entityName()),
        labelsJson,
        startsAt,
        request.endsAt(),
        status,
        rawJson,
        fingerprint,
        aggregationKey);
  }

  private String effectiveSourceEventId(String sourceEventId, String fingerprint) {
    if (sourceEventId != null && !sourceEventId.isBlank()) {
      return sourceEventId.trim();
    }
    return "fingerprint:" + sha256(fingerprint);
  }

  private String normalizeText(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim().toLowerCase(Locale.ROOT);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String normalizeSeverity(String severity) {
    if (severity == null || severity.isBlank()) {
      return "info";
    }
    String normalized = severity.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "info", "low", "warning", "medium", "high", "critical", "disaster" -> normalized;
      case "average" -> "medium";
      default -> "info";
    };
  }

  private String normalizeStatus(String status) {
    if (status == null || status.isBlank()) {
      return "open";
    }
    String normalized = status.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "recovered", "resolved", "closed", "ok" -> "resolved";
      default -> "open";
    };
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
