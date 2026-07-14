package io.aegisops.workrecord.application.command;

import java.math.BigDecimal;
import java.util.List;

public record WorkloadSummary(int workdayCount, List<UserWorkload> users) {
  public WorkloadSummary {
    users = users == null ? List.of() : List.copyOf(users);
  }

  public record UserWorkload(
      String userId,
      String displayName,
      long recordCount,
      long completedCount,
      BigDecimal numericWorkload,
      BigDecimal recordsPerWorkday) {}
}
