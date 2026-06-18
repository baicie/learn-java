package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AnsibleCredentialResponse(
    String id,
    String name,
    String description,
    String credentialType,
    String secretRef,
    boolean enabled,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
