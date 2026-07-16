package io.aegisops.asset.api.dto;

import java.util.List;
import java.util.Map;

public record AssetImportRowResponse(
    int rowNumber,
    String externalId,
    String validationStatus,
    String resolutionAction,
    String resolvedAssetId,
    Map<String, Object> payload,
    List<String> errorCodes) {}
