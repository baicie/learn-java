package io.aegisops.asset.infrastructure.persistence;

import java.util.List;
import java.util.Map;

public record AssetImportRowDraft(
    int rowNumber,
    String externalId,
    Map<String, Object> payload,
    String validationStatus,
    String resolutionAction,
    List<String> errorCodes) {}
