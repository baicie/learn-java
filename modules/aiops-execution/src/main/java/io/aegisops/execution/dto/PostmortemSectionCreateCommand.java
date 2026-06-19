package io.aegisops.execution.dto;

public record PostmortemSectionCreateCommand(
    String id,
    String tenantId,
    String postmortemId,
    int sectionOrder,
    String sectionType,
    String title,
    String content,
    String metadataJson) {}
