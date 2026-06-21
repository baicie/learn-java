package io.aegisops.plugin.dto;

public record PluginExtensionPointResponse(
    String extensionPoint, String description, boolean frontend, boolean agentTool) {}
