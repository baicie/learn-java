package io.aegisops.asset.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;

public record AssetImportPreviewDraft(
    String tenantId,
    String sourceInstanceId,
    String fileName,
    String checksum,
    String actorId,
    List<AssetImportRowDraft> rows,
    OffsetDateTime createdAt) {}
