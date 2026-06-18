package io.aegisops.execution.dto;

public record AnsibleCredentialCreateCommand(
    String id,
    String tenantId,
    String name,
    String description,
    String credentialType,
    String secretRef,
    boolean enabled,
    String createdBy) {}
