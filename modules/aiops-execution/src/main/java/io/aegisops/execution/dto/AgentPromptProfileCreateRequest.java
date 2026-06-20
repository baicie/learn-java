package io.aegisops.execution.dto;

import java.util.Map;

public record AgentPromptProfileCreateRequest(
    String name,
    String version,
    String systemPrompt,
    String diagnosisPromptTemplate,
    Map<String, Object> metadata,
    String createdBy) {}
