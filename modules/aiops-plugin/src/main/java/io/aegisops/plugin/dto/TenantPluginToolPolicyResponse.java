package io.aegisops.plugin.dto;

import java.time.OffsetDateTime;

public record TenantPluginToolPolicyResponse(
    String id,
    String tenantPluginId,
    String pluginId,
    String pluginKey,
    String toolKey,
    String status,
    String riskLevel,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
