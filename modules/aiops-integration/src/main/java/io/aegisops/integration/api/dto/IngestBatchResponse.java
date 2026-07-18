package io.aegisops.integration.api.dto;

import java.util.List;

public record IngestBatchResponse(int accepted, List<IngestResponse> results) {}
