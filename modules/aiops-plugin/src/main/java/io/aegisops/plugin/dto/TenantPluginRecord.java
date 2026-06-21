package io.aegisops.plugin.dto;

import java.time.OffsetDateTime;

public record TenantPluginRecord(
    String id,
    String tenantId,
    String pluginId,
    String pluginKey,
    String name,
    String version,
    String status,
    String configJson,
    String manifestJson,
    String capabilitiesJson,
    String enabledBy,
    OffsetDateTime enabledAt,
    String disabledBy,
    OffsetDateTime disabledAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
