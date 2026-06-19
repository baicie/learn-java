package io.aegisops.execution.dto;

public record PostmortemGenerateRequest(
    String generatedBy,
    Boolean includeAiDiagnosis,
    Boolean includeRca,
    Boolean includeExecutions,
    Boolean includeRollback,
    Boolean includeTimeline,
    Boolean generateActionItems) {}
