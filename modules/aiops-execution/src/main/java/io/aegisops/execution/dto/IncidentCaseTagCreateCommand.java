package io.aegisops.execution.dto;

public record IncidentCaseTagCreateCommand(String id, String tenantId, String caseId, String tag) {}
