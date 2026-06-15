package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.AiDiagnosisRecord;
import io.aegisops.ai.client.dto.AiIncidentRecord;
import java.sql.ResultSet;
import java.sql.SQLException;

final class AiRows {
  private AiRows() {}

  static AiIncidentRecord incident(ResultSet rs) throws SQLException {
    return new AiIncidentRecord(
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
        rs.getString("suspected_root_cause"),
        rs.getBigDecimal("confidence"),
        rs.getObject("started_at", java.time.OffsetDateTime.class),
        rs.getObject("detected_at", java.time.OffsetDateTime.class),
        rs.getObject("last_seen_at", java.time.OffsetDateTime.class),
        rs.getObject("created_at", java.time.OffsetDateTime.class),
        rs.getObject("updated_at", java.time.OffsetDateTime.class));
  }

  static AiDiagnosisRecord diagnosis(ResultSet rs) throws SQLException {
    return new AiDiagnosisRecord(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("incident_id"),
        rs.getString("status"),
        rs.getString("provider"),
        rs.getString("model"),
        rs.getString("agent_name"),
        rs.getString("summary"),
        rs.getString("root_cause"),
        rs.getString("impact"),
        rs.getString("next_steps_json"),
        rs.getString("runbook_suggestions_json"),
        rs.getString("risks_json"),
        rs.getObject("created_at", java.time.OffsetDateTime.class));
  }
}
