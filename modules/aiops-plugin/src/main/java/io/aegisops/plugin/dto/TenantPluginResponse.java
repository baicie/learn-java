package io.aegisops.plugin.dto;

import java.time.OffsetDateTime;

public record TenantPluginResponse(
    String id,
    String pluginId,
    String pluginKey,
    String name,
    String version,
    String status,
    String configJson,
    OffsetDateTime enabledAt,
    OffsetDateTime disabledAt) {}
