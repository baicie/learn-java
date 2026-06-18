package io.aegisops.execution.dto;

public record ExecutionArtifactCreateCommand(
    String id,
    String tenantId,
    String executionId,
    String stepId,
    String artifactType,
    String name,
    String content,
    String metadataJson) {}
