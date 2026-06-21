package io.aegisops.execution;

import java.util.Map;

/** DTO for creating report sections. */
public record CreateSectionParams(
    String tenantId,
    String reportId,
    int order,
    String sectionType,
    String title,
    String content,
    Map<String, ?> metadata) {}
