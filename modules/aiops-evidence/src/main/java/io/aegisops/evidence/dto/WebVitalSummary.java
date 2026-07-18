package io.aegisops.evidence.dto;

import java.math.BigDecimal;

public record WebVitalSummary(
    String name, BigDecimal minimum, BigDecimal maximum, BigDecimal average, long samples) {}
