package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.application.command.StatisticsQuery;
import io.aegisops.workrecord.application.command.StatisticsResult;
import io.aegisops.workrecord.application.command.WorkloadSummary;
import java.time.OffsetDateTime;

public interface StatisticsRepository {
  StatisticsResult aggregate(String tenantId, StatisticsQuery query, StatisticalField field);

  WorkloadSummary workload(
      String tenantId,
      String templateId,
      OffsetDateTime from,
      OffsetDateTime to,
      int workdayCount,
      StatisticalField field);

  record StatisticalField(String fieldCode, String fieldType) {
    public static StatisticalField none() {
      return new StatisticalField(null, null);
    }

    public boolean present() {
      return fieldCode != null;
    }
  }
}
