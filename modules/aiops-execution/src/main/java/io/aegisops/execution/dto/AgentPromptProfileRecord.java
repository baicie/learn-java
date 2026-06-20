package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentPromptProfileRecord(
    String id,
    String tenantId,
    String name,
    String version,
    String status,
    String systemPrompt,
    String diagnosisPromptTemplate,
    String metadataJson,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
