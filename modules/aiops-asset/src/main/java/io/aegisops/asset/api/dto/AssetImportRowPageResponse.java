package io.aegisops.asset.api.dto;

import java.util.List;

public record AssetImportRowPageResponse(
    long total, int page, int pageSize, List<AssetImportRowResponse> items) {}
