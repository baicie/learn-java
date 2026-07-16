package io.aegisops.asset.api.dto;

import java.time.OffsetDateTime;

public record AssetImportPreviewResponse(
    String jobId,
    String fileName,
    String sourceInstanceId,
    String status,
    int totalRows,
    int validRows,
    int invalidRows,
    int conflictRows,
    int createdRows,
    int updatedRows,
    OffsetDateTime createdAt) {}
