package io.aegisops.plugin.dto;

import java.util.List;

public record TenantFrontendManifestResponse(
    String tenantId, List<String> enabledPluginKeys, List<Object> contributions) {}
