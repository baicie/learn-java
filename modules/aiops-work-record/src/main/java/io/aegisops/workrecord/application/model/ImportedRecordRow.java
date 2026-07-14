package io.aegisops.workrecord.application.model;

import java.time.OffsetDateTime;
import java.util.Map;

public record ImportedRecordRow(
    int rowNumber,
    String title,
    String status,
    String ownerId,
    OffsetDateTime recordTime,
    Map<String, Object> customData) {

  public ImportedRecordRow {
    if (rowNumber < 2) {
      throw new IllegalArgumentException("Excel data row number must be at least 2");
    }
    customData = customData == null ? Map.of() : Map.copyOf(customData);
  }
}
