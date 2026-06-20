package io.aegisops.execution.dto;

import java.util.List;

public record AgentEvalCaseFromIncidentCaseRequest(
    String createdBy, List<String> extraKeywords, List<String> forbiddenActions) {}
