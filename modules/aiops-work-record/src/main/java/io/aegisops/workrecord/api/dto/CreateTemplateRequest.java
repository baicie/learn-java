package io.aegisops.workrecord.api.dto;

public record CreateTemplateRequest(
    String name,
    String code,
    String description,
    Boolean enabled,
    String schemaJson,
    String designerJson) {}
