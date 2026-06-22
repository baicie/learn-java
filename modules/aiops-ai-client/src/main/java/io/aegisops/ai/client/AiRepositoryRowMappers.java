package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.AiEvidenceRecord;
import io.aegisops.ai.client.dto.AiTimelineRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.RowMapper;

public final class AiRepositoryRowMappers {

  private AiRepositoryRowMappers() {}

  public static RowMapper<AiEvidenceRecord> evidenceMapper() {
    return AiRepositoryRowMappers::toEvidenceRecord;
  }

  public static RowMapper<AiTimelineRecord> timelineMapper() {
    return AiRepositoryRowMappers::toTimelineRecord;
  }

  private static AiEvidenceRecord toEvidenceRecord(ResultSet rs, int rowNum) throws SQLException {
    return new AiEvidenceRecord(
        rs.getString("id"),
        rs.getString("evidence_key"),
        rs.getString("source"),
        rs.getString("evidence_type"),
        rs.getString("title"),
        rs.getString("summary"),
        toOffsetDateTime(rs.getObject("time_range_start")),
        toOffsetDateTime(rs.getObject("time_range_end")),
        rs.getBigDecimal("confidence"),
        rs.getString("payload_json"));
  }

  private static AiTimelineRecord toTimelineRecord(ResultSet rs, int rowNum) throws SQLException {
    return new AiTimelineRecord(
        rs.getString("id"),
        toOffsetDateTime(rs.getObject("event_time")),
        rs.getString("event_type"),
        rs.getString("title"),
        rs.getString("description"),
        rs.getString("source"),
        rs.getString("payload_json"));
  }

  private static OffsetDateTime toOffsetDateTime(Object value) {
    if (value instanceof OffsetDateTime odt) {
      return odt;
    }
    return null;
  }
}
