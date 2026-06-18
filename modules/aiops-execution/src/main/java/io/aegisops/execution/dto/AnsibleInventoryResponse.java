package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AnsibleInventoryResponse(
    String id,
    String name,
    String description,
    String inventoryType,
    String inlineInventory,
    String fileRef,
    boolean enabled,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
