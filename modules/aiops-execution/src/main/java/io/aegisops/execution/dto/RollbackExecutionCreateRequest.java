package io.aegisops.execution.dto;

public record RollbackExecutionCreateRequest(String requestedBy, Integer maxAttempts) {}
