package io.aegisops.plugin.dto;

import java.time.OffsetDateTime;

public record PluginDescriptorResponse(
    String id,
    String pluginKey,
    String name,
    String version,
    String description,
    String provider,
    String status,
    String manifestJson,
    String capabilitiesJson,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
