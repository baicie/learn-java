package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionArtifactRecord(
    String id,
    String tenantId,
    String executionId,
    String stepId,
    String artifactType,
    String name,
    String content,
    String metadataJson,
    OffsetDateTime createdAt) {}
