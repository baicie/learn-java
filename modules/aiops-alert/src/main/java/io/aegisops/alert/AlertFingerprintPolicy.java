package io.aegisops.alert;

import java.util.Locale;
import org.springframework.stereotype.Component;

/** Policy for computing alert fingerprint and aggregation key. */
@Component
public class AlertFingerprintPolicy {

  public String fingerprint(AlertIngestRequest request) {
    String source = normalize(request.source(), "unknown");
    String asset = normalize(request.assetId(), normalize(request.entityName(), "no-asset"));
    String sourceEvent = normalize(request.sourceEventId(), "no-event");
    String title = normalize(request.title(), "untitled");
    return source + ":" + asset + ":" + sourceEvent + ":" + title;
  }

  public String aggregationKey(AlertIngestRequest request) {
    String source = normalize(request.source(), "unknown");
    String asset = normalize(request.assetId(), normalize(request.entityName(), "no-asset"));
    String service = normalize(request.entityName(), "unknown-service");
    return source + ":" + asset + ":" + service;
  }

  private String normalize(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value
        .trim()
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
        .replaceAll("(^-+|-+$)", "");
  }
}
