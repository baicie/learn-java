package io.aegisops.runbook.dto;

public record RunbookCreateCommand(
    String id,
    String tenantId,
    String name,
    String description,
    String category,
    String riskLevel,
    boolean enabled,
    String matchersJson,
    String variablesJson) {}
