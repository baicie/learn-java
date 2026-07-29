package io.aegisops.incident;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

final class IncidentRows {
  private IncidentRows() {}

  static IncidentSummaryRecord summary(ResultSet rs) throws SQLException {
    return new IncidentSummaryRecord(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("title"),
        rs.getString("summary"),
        rs.getString("severity"),
        rs.getString("status"),
        rs.getString("source"),
        rs.getString("primary_asset_id"),
        rs.getString("aggregation_key"),
        rs.getInt("alert_count"),
        rs.getBigDecimal("impact_score"),
        rs.getObject("started_at", OffsetDateTime.class),
        rs.getObject("detected_at", OffsetDateTime.class),
        rs.getObject("last_seen_at", OffsetDateTime.class),
        rs.getObject("resolved_at", OffsetDateTime.class),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }

  static AlertCandidate alertCandidate(ResultSet rs) throws SQLException {
    return new AlertCandidate(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("source"),
        rs.getString("source_event_id"),
        rs.getString("severity"),
        rs.getString("title"),
        rs.getString("description"),
        rs.getString("asset_id"),
        rs.getString("entity_type"),
        rs.getString("entity_name"),
        rs.getString("fingerprint"),
        rs.getString("aggregation_key"),
        rs.getString("labels_json"),
        rs.getObject("starts_at", OffsetDateTime.class),
        rs.getObject("ends_at", OffsetDateTime.class),
        rs.getObject("created_at", OffsetDateTime.class));
  }
}
