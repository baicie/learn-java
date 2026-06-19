package io.aegisops.execution.dto;

import java.time.LocalDate;

public record PostmortemActionItemCreateCommand(
    String id,
    String tenantId,
    String postmortemId,
    String title,
    String description,
    String owner,
    String priority,
    String status,
    LocalDate dueDate,
    String sourceType,
    String sourceRefId,
    String createdBy) {}
