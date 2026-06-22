package io.aegisops.datasource.zabbix;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

public final class ZabbixAggregationKeyBuilder {
  private static final int DEFAULT_WINDOW_MINUTES = 10;
  private static final DateTimeFormatter BUCKET_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMddHHmm");

  private ZabbixAggregationKeyBuilder() {}

  public static String build(
      String datasourceId, String hostId, String service, String env, OffsetDateTime startsAt) {
    return build(new AggregationKeyContext(datasourceId, hostId, service, env), startsAt);
  }

  public static String build(AggregationKeyContext context, OffsetDateTime startsAt) {
    return build(context, startsAt, DEFAULT_WINDOW_MINUTES);
  }

  public static String build(
      AggregationKeyContext context, OffsetDateTime startsAt, int windowMinutes) {
    String safeDatasource = normalizePart(context.datasourceId(), "no-datasource");
    String safeHost = normalizePart(context.hostId(), "no-host");
    String safeService = normalizePart(context.service(), "no-service");
    String safeEnv = normalizePart(context.env(), "no-env");
    String bucket = windowBucket(startsAt, windowMinutes);

    return "zabbix:"
        + safeDatasource
        + ":"
        + safeHost
        + ":"
        + safeService
        + ":"
        + safeEnv
        + ":"
        + bucket;
  }

  public static String buildFromLabels(
      String datasourceId, Map<String, ?> labels, OffsetDateTime startsAt) {
    String hostId =
        firstNonBlank(
            stringValue(labels, "zabbixHostId"),
            firstListValue(labels, "zabbixHostIds"),
            stringValue(labels, "hostId"),
            stringValue(labels, "host"));
    String service =
        firstNonBlank(
            stringValue(labels, "service"),
            stringValue(labels, "component"),
            stringValue(labels, "app"));
    String env = firstNonBlank(stringValue(labels, "env"), stringValue(labels, "environment"));

    return build(datasourceId, hostId, service, env, startsAt);
  }

  public static String windowBucket(OffsetDateTime startsAt, int windowMinutes) {
    int safeWindow = windowMinutes <= 0 ? DEFAULT_WINDOW_MINUTES : windowMinutes;

    OffsetDateTime time = startsAt == null ? OffsetDateTime.now(ZoneOffset.UTC) : startsAt;
    OffsetDateTime utc = time.withOffsetSameInstant(ZoneOffset.UTC);

    int minute = utc.getMinute();
    int bucketMinute = (minute / safeWindow) * safeWindow;

    OffsetDateTime bucket = utc.withMinute(bucketMinute).withSecond(0).withNano(0);

    return BUCKET_FORMATTER.format(bucket);
  }

  private static String normalizePart(String value, String fallback) {
    String text = firstNonBlank(value);
    if (text == null) {
      return fallback;
    }

    return text.trim()
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9._\\-\u4e00-\u9fa5]+", "-")
        .replaceAll("(^-+|-+$)", "");
  }

  private static String stringValue(Map<String, ?> labels, String key) {
    if (labels == null || key == null) {
      return null;
    }

    Object value = labels.get(key);
    if (value == null) {
      return null;
    }

    String text = String.valueOf(value);
    return text.isBlank() ? null : text.trim();
  }

  private static String firstListValue(Map<String, ?> labels, String key) {
    if (labels == null || key == null) {
      return null;
    }

    Object value = labels.get(key);
    if (value instanceof Iterable<?> iterable) {
      for (Object item : iterable) {
        if (item != null && !String.valueOf(item).isBlank()) {
          return String.valueOf(item).trim();
        }
      }
    }

    return null;
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }

    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }

    return null;
  }

  public record AggregationKeyContext(
      String datasourceId, String hostId, String service, String env) {}
}
