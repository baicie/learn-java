package io.aegisops.asset.api.dto;

import java.util.List;

public record AssetPageResponse(long total, int page, int pageSize, List<AssetResponse> items) {}
