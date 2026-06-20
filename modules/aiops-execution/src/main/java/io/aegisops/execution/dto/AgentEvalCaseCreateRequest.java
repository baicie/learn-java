package io.aegisops.execution.dto;

import java.util.List;

public record AgentEvalCaseCreateRequest(
    String sourceType,
    String sourceId,
    String incidentId,
    String title,
    String severity,
    String inputContext,
    String expectedRootCause,
    List<String> expectedKeywords,
    List<String> expectedActions,
    List<String> forbiddenActions,
    List<String> tags,
    String createdBy) {}
