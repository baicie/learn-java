package io.aegisops.execution.dto;

public record AgentPromptProfileCreateCommand(
    String id,
    String tenantId,
    String name,
    String version,
    String status,
    String systemPrompt,
    String diagnosisPromptTemplate,
    String metadataJson,
    String createdBy) {}
