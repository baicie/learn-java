package io.aegisops.evidence.dto;

import java.math.BigDecimal;

public record MetricSeriesSummary(
    String name,
    String query,
    BigDecimal min,
    BigDecimal max,
    BigDecimal avg,
    BigDecimal latest,
    int points) {}
