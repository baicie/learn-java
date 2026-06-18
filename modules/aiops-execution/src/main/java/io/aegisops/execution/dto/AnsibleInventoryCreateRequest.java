package io.aegisops.execution.dto;

public record AnsibleInventoryCreateRequest(
    String name,
    String description,
    String inventoryType,
    String inlineInventory,
    String fileRef,
    String createdBy) {}
