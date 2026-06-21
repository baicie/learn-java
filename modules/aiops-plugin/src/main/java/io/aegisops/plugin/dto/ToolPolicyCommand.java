package io.aegisops.plugin.dto;

/** Command for upserting a tool policy. */
public record ToolPolicyCommand(
    String id,
    String tenantId,
    String tenantPluginId,
    String pluginId,
    String toolKey,
    String status,
    String riskLevel,
    String createdBy) {}
