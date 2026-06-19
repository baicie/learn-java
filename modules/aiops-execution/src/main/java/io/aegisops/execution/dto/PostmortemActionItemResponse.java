package io.aegisops.execution.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PostmortemActionItemResponse(
    String id,
    String title,
    String description,
    String owner,
    String priority,
    String status,
    LocalDate dueDate,
    String sourceType,
    String sourceRefId,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
