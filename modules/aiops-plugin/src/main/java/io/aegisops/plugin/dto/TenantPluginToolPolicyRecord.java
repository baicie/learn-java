package io.aegisops.plugin.dto;

import java.time.OffsetDateTime;

public record TenantPluginToolPolicyRecord(
    String id,
    String tenantId,
    String tenantPluginId,
    String pluginId,
    String pluginKey,
    String toolKey,
    String status,
    String riskLevel,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
