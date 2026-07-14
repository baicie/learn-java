package io.aegisops.workrecord.domain.model;

import java.time.OffsetDateTime;
import java.util.List;

public record WorkRecordHandover(
    String id,
    String tenantId,
    String fromUserId,
    String toUserId,
    OffsetDateTime shiftStart,
    OffsetDateTime shiftEnd,
    HandoverStatus status,
    String summary,
    List<String> recordIds,
    List<String> relationIds,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime acceptedAt,
    OffsetDateTime completedAt,
    int rowVersion) {
  public WorkRecordHandover {
    recordIds = recordIds == null ? List.of() : List.copyOf(recordIds);
    relationIds = relationIds == null ? List.of() : List.copyOf(relationIds);
  }
}
