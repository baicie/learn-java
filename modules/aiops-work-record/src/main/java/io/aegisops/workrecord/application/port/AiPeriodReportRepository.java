package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.OffsetDateTime;
import java.util.List;

public interface AiPeriodReportRepository {
  PeriodSnapshot snapshot(String tenantId, OffsetDateTime from, OffsetDateTime to, int sampleLimit);

  record Count(String key, long count) {}

  record PeriodSnapshot(
      long recordCount,
      List<Count> statusCounts,
      List<Count> ownerCounts,
      List<WorkRecord> samples) {
    public PeriodSnapshot {
      statusCounts = statusCounts == null ? List.of() : List.copyOf(statusCounts);
      ownerCounts = ownerCounts == null ? List.of() : List.copyOf(ownerCounts);
      samples = samples == null ? List.of() : List.copyOf(samples);
    }
  }
}
