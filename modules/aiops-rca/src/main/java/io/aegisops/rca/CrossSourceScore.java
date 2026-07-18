package io.aegisops.rca;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CrossSourceScore(
    String assetId,
    BigDecimal score,
    Map<String, BigDecimal> signals,
    List<String> evidenceRefs,
    String explanation) {}
