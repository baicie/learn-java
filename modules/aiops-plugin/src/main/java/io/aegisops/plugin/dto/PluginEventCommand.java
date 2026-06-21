package io.aegisops.plugin.dto;

/** Command for creating a plugin event record. */
public record PluginEventCommand(
    String id,
    String tenantId,
    String pluginId,
    String tenantPluginId,
    String eventType,
    String summary,
    String actor,
    String metadataJson) {}
