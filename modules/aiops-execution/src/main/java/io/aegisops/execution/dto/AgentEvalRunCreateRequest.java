package io.aegisops.execution.dto;

public record AgentEvalRunCreateRequest(
    String datasetId, String promptProfileId, String mode, String createdBy) {}
