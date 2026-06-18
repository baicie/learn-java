package io.aegisops.execution.dto;

public record AnsibleInventoryCreateCommand(
    String id,
    String tenantId,
    String name,
    String description,
    String inventoryType,
    String inlineInventory,
    String fileRef,
    boolean enabled,
    String createdBy) {}
