package io.aegisops.execution.dto;

import java.util.Map;

/** DTO for creating postmortem report sections. */
public record PostmortemSectionParams(
    String tenantId,
    String postmortemId,
    int order,
    String sectionType,
    String title,
    String content,
    Map<String, ?> metadata) {}
