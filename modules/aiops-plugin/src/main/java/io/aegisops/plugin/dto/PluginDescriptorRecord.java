package io.aegisops.plugin.dto;

import java.time.OffsetDateTime;

public record PluginDescriptorRecord(
    String id,
    String pluginKey,
    String name,
    String version,
    String description,
    String provider,
    String status,
    String manifestJson,
    String capabilitiesJson,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
