package io.aegisops.incident;

import java.time.OffsetDateTime;

public record IncidentRecord(
    String id,
    String tenantId,
    String title,
    String summary,
    String severity,
    String status,
    String source,
    String primaryAssetId,
    String aggregationKey,
    int alertCount,
    OffsetDateTime startedAt,
    OffsetDateTime detectedAt,
    OffsetDateTime lastSeenAt,
    OffsetDateTime resolvedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {
  public static IncidentRecord from(IncidentSummaryRecord record) {
    return new IncidentRecord(
        record.id(),
        record.tenantId(),
        record.title(),
        record.summary(),
        record.severity(),
        record.status(),
        record.source(),
        record.primaryAssetId(),
        record.aggregationKey(),
        record.alertCount(),
        record.startedAt(),
        record.detectedAt(),
        record.lastSeenAt(),
        record.resolvedAt(),
        record.createdAt(),
        record.updatedAt());
  }
}
