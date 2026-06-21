package io.aegisops.plugin.dto;

public record PluginDescriptorCreateCommand(
    String id,
    String pluginKey,
    String name,
    String version,
    String description,
    String provider,
    String status,
    String manifestJson,
    String capabilitiesJson,
    String createdBy) {}
