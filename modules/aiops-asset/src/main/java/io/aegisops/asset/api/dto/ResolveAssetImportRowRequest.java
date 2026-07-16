package io.aegisops.asset.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolveAssetImportRowRequest(@NotBlank String action, String targetAssetId) {}
