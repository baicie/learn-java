package io.aegisops.workrecord.application.command;

import java.math.BigDecimal;
import java.util.List;

public record StatisticsResult(
    long totalRecords,
    long completedRecords,
    long distinctOwners,
    List<SeriesPoint> series,
    FieldAggregate fieldAggregate) {
  public StatisticsResult {
    series = series == null ? List.of() : List.copyOf(series);
  }

  public record SeriesPoint(String key, String label, long count, BigDecimal value) {}

  public record FieldAggregate(
      String fieldCode,
      BigDecimal sum,
      BigDecimal average,
      BigDecimal minimum,
      BigDecimal maximum,
      long valueCount) {}
}
