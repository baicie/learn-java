package io.aegisops.execution.dto;

public record AnsibleCredentialCreateRequest(
    String name, String description, String credentialType, String secretRef, String createdBy) {}
