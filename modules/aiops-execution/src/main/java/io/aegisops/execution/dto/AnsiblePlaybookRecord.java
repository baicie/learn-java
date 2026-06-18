package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AnsiblePlaybookRecord(
    String id,
    String tenantId,
    String name,
    String description,
    String playbookRef,
    String playbookContent,
    String variablesSchemaJson,
    String allowedTagsJson,
    boolean enabled,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
