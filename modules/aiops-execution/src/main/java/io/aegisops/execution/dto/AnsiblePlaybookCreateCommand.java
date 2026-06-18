package io.aegisops.execution.dto;

public record AnsiblePlaybookCreateCommand(
    String id,
    String tenantId,
    String name,
    String description,
    String playbookRef,
    String playbookContent,
    String variablesSchemaJson,
    String allowedTagsJson,
    boolean enabled,
    String createdBy) {}
