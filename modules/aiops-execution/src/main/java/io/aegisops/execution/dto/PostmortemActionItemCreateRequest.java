package io.aegisops.execution.dto;

import java.time.LocalDate;

public record PostmortemActionItemCreateRequest(
    String title,
    String description,
    String owner,
    String priority,
    LocalDate dueDate,
    String sourceType,
    String sourceRefId,
    String createdBy) {}
