package io.aegisops.execution.dto;

public record AgentEvalDatasetCreateCommand(
    String id, String tenantId, String name, String description, String status, String createdBy) {}
