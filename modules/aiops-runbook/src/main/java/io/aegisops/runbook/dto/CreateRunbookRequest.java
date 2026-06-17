package io.aegisops.runbook.dto;

import java.util.List;
import java.util.Map;

public record CreateRunbookRequest(
    String name,
    String description,
    String category,
    String riskLevel,
    Map<String, Object> matchers,
    Map<String, Object> variables,
    List<CreateRunbookStepRequest> steps) {}
