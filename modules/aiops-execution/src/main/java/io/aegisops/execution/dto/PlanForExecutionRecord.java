package io.aegisops.execution.dto;

public record PlanForExecutionRecord(
    String id,
    String tenantId,
    String incidentId,
    String status,
    String riskLevel,
    String title,
    String summary) {}
