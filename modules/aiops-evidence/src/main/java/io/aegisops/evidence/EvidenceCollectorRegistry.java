package io.aegisops.evidence;

import io.aegisops.common.exception.AppException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EvidenceCollectorRegistry {
  private final List<EvidenceCollector> collectors;

  public EvidenceCollectorRegistry(List<EvidenceCollector> collectors) {
    this.collectors = collectors;
  }

  public EvidenceCollector get(String collectorKey) {
    return collectors.stream()
        .filter(collector -> collector.collectorKey().equals(collectorKey))
        .findFirst()
        .orElseThrow(
            () ->
                new AppException(
                    "EVIDENCE_COLLECTOR_NOT_FOUND",
                    "Evidence collector not found: " + collectorKey));
  }
}
