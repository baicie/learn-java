package io.aegisops.workrecord.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WorkRecordExportPolicy {
  private final int maxRows;

  public WorkRecordExportPolicy(@Value("${aiops.work-record.export.max-rows:5000}") int maxRows) {
    if (maxRows < 1 || maxRows > 100_000) {
      throw new IllegalArgumentException(
          "aiops.work-record.export.max-rows must be between 1 and 100000");
    }
    this.maxRows = maxRows;
  }

  public int maxRows() {
    return maxRows;
  }
}
