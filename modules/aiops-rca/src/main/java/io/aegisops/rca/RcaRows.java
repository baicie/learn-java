package io.aegisops.rca;

import java.sql.ResultSet;
import java.sql.SQLException;

final class RcaRows {
    private RcaRows() {}

    static RcaIncidentRecord incident(ResultSet rs) throws SQLException {
        return new RcaIncidentRecord(
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
                rs.getObject("resolved_at", java.time.OffsetDateTime.class),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }

    static RcaAnalysisRecord analysis(ResultSet rs) throws SQLException {
        return new RcaAnalysisRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("incident_id"),
                rs.getString("status"),
                rs.getString("suspected_root_cause"),
                rs.getBigDecimal("confidence"),
                rs.getString("summary"),
                rs.getString("evidence"),
                rs.getString("model_version"),
                rs.getObject("created_at", java.time.OffsetDateTime.class)
        );
    }
}
