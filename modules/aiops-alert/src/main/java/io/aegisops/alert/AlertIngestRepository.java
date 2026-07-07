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
              asset_id = excluded.asset_id,
              entity_type = excluded.entity_type,
              entity_name = excluded.entity_name,
              labels = excluded.labels,
              starts_at = least(alert_event.starts_at, excluded.starts_at),
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
      case "info", "low", "warning", "critical", "disaster" -> normalized;
      case "high" -> "critical";
      default -> "info";
    };
  }

  private String normalizeStatus(String status) {
    if (status == null || status.isBlank()) {
      return "open";
    }
    String normalized = status.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "resolved", "closed", "ok" -> "resolved";
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
