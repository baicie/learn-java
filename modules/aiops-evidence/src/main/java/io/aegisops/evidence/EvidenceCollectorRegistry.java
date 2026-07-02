package io.aegisops.evidence;

import io.aegisops.common.exception.AppException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EvidenceCollectorRegistry {
  private final Map<String, EvidenceCollector> collectorsByKey;

  public EvidenceCollectorRegistry(List<EvidenceCollector> collectors) {
    Map<String, EvidenceCollector> map = new LinkedHashMap<>();
    for (EvidenceCollector collector : collectors) {
      String key = normalizeKey(collector.collectorKey());
      EvidenceCollector previous = map.putIfAbsent(key, collector);
      if (previous != null) {
        throw new IllegalStateException("Duplicate EvidenceCollector key: " + key);
      }
    }
    this.collectorsByKey = Map.copyOf(map);
  }

  public EvidenceCollector get(String collectorKey) {
    String key = normalizeKey(collectorKey);
    EvidenceCollector collector = collectorsByKey.get(key);
    if (collector == null) {
      throw new AppException(
          "EVIDENCE_COLLECTOR_NOT_FOUND", "Evidence collector not found: " + key);
    }
    return collector;
  }

  public EvidenceCollector getSupported(String collectorKey, EvidenceCollectRequest request) {
    EvidenceCollector collector = get(collectorKey);
    if (!collector.supports(request)) {
      throw new AppException(
          "EVIDENCE_COLLECTOR_NOT_SUPPORTED",
          "Evidence collector does not support request: " + collectorKey);
    }
    return collector;
  }

  private String normalizeKey(String collectorKey) {
    if (collectorKey == null || collectorKey.isBlank()) {
      throw new AppException("EVIDENCE_COLLECTOR_REQUIRED", "Evidence collector key is required");
    }
    return collectorKey.trim();
  }
}
