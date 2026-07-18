package io.aegisops.rca;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CrossSourceCandidate(
    String assetId,
    Map<String, BigDecimal> signals,
    List<String> evidenceRefs,
    String explanation) {
  public CrossSourceCandidate {
    signals = signals == null ? Map.of() : Map.copyOf(signals);
    evidenceRefs =
        evidenceRefs == null
            ? List.of()
            : evidenceRefs.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
  }
}
