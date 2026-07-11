package io.aegisops.incident;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class IncidentAggregationPolicy {
  public String aggregationKey(AlertCandidate alert) {
    if (alert.aggregationKey() != null && !alert.aggregationKey().isBlank()) {
      return alert.aggregationKey().trim();
    }

    String source = nonBlank(alert.source(), "unknown");

    if (alert.fingerprint() != null && !alert.fingerprint().isBlank()) {
      String fingerprint = alert.fingerprint().trim();
      return fingerprint.startsWith(source + ":") ? fingerprint : source + ":" + fingerprint;
    }

    String assetPart = nonBlank(alert.assetId(), "no-asset");
    String titlePart = normalizeTitle(alert.title());
    return source + ":" + assetPart + ":" + titlePart;
  }

  public String title(String aggregationKey, List<AlertCandidate> alerts) {
    if (alerts == null || alerts.isEmpty()) {
      return "Incident " + aggregationKey;
    }

    if (isZabbixGroup(alerts)) {
      String service =
          alerts.stream()
              .map(alert -> alert.entityName())
              .filter(value -> value != null && !value.isBlank())
              .findFirst()
              .orElse("zabbix service");

      return service + " 主机与服务异常";
    }

    String firstTitle = alerts.get(0).title();
    boolean sameTitle =
        alerts.stream().allMatch(alert -> Objects.equals(firstTitle, alert.title()));

    if (sameTitle && firstTitle != null && !firstTitle.isBlank()) {
      return firstTitle;
    }

    String entity =
        alerts.stream()
            .map(alert -> alert.entityName())
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse("related assets");

    return alerts.size() + " related alerts on " + entity;
  }

  public String summary(String aggregationKey, List<AlertCandidate> alerts) {
    String severity = highestSeverity(alerts);

    if (isZabbixGroup(alerts)) {
      return "Aggregated "
          + alerts.size()
          + " Zabbix alert(s), severity="
          + severity
          + ", aggregationKey="
          + aggregationKey;
    }

    return "Aggregated "
        + alerts.size()
        + " alert(s), severity="
        + severity
        + ", aggregationKey="
        + aggregationKey;
  }

  public String highestSeverity(List<AlertCandidate> alerts) {
    return IncidentSeverity.max(alerts.stream().map(alert -> alert.severity()).toList());
  }

  public String primaryAssetId(List<AlertCandidate> alerts) {
    return alerts.stream()
        .map(alert -> alert.assetId())
        .filter(value -> value != null && !value.isBlank())
        .findFirst()
        .orElse(null);
  }

  public OffsetDateTime firstStartedAt(List<AlertCandidate> alerts) {
    return alerts.stream()
        .map(alert -> alert.startsAt())
        .filter(item -> item != null)
        .min(Comparator.naturalOrder())
        .orElse(OffsetDateTime.now());
  }

  public OffsetDateTime lastSeenAt(List<AlertCandidate> alerts) {
    return alerts.stream()
        .map(alert -> alert.startsAt())
        .filter(item -> item != null)
        .max(Comparator.naturalOrder())
        .orElse(OffsetDateTime.now());
  }

  private boolean isZabbixGroup(List<AlertCandidate> alerts) {
    return alerts != null
        && !alerts.isEmpty()
        && alerts.stream().allMatch(alert -> "zabbix".equalsIgnoreCase(alert.source()));
  }

  private String normalizeTitle(String title) {
    if (title == null || title.isBlank()) {
      return "untitled";
    }

    return title
        .trim()
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9\u4e00-\u9fa5]+", "-")
        .replaceAll("(^-+|-+$)", "");
  }

  private String nonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
