package io.aegisops.execution.dto;

import java.util.List;

public record IncidentCaseCreateFromPostmortemRequest(
    String createdBy, Integer qualityScore, List<String> tags) {}
