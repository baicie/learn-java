package io.aegisops.execution.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PostmortemReportResponse(
    String id,
    String tenantId,
    String incidentId,
    String status,
    String severity,
    String title,
    String summary,
    String impact,
    String rootCause,
    String detection,
    String resolution,
    String prevention,
    String markdown,
    String sourceSnapshotJson,
    String generatedBy,
    OffsetDateTime generatedAt,
    List<PostmortemSectionResponse> sections,
    List<PostmortemActionItemResponse> actionItems,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
