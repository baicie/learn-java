package io.aegisops.workrecord;

public record CreateTemplateRequest(
    String name,
    String code,
    String description,
    Boolean enabled,
    String schemaJson,
    String designerJson) {}
