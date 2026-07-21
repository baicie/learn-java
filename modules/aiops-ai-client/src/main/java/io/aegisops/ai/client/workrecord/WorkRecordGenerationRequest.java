package io.aegisops.ai.client.workrecord;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record WorkRecordGenerationRequest(
    String contractVersion,
    String generationType,
    String tenantId,
    String resourceId,
    String actorId,
    LocalDate periodStart,
    LocalDate periodEnd,
    String locale,
    String promptVersion,
    List<RecordItem> records,
    Map<String, Object> statistics,
    String traceId) {
  public WorkRecordGenerationRequest {
    records = records == null ? List.of() : List.copyOf(records);
    statistics = statistics == null ? Map.of() : Map.copyOf(statistics);
  }

  public record RecordItem(
      String id,
      String title,
      String status,
      OffsetDateTime recordTime,
      String ownerName,
      Map<String, Object> fields,
      List<Map<String, Object>> relations) {
    public RecordItem {
      fields = fields == null ? Map.of() : Map.copyOf(fields);
      relations = relations == null ? List.of() : List.copyOf(relations);
    }
  }
}
