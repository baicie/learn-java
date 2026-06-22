package io.aegisops.integration.zabbix;

import io.aegisops.common.exception.AppException;
import io.aegisops.datasource.zabbix.ZabbixAggregationKeyBuilder;
import io.aegisops.datasource.zabbix.ZabbixExternalIds;
import io.aegisops.datasource.zabbix.ZabbixSeverityMapper;
import io.aegisops.datasource.zabbix.ZabbixTagNormalizer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ZabbixWebhookMapper {

  public ZabbixWebhookAlertMapping map(String datasourceId, ZabbixWebhookPayload payload) {
    if (payload == null) {
      throw new AppException("ZABBIX_WEBHOOK_PAYLOAD_EMPTY", "Zabbix webhook payload is required");
    }

    String resolvedDatasourceId = firstNonBlank(datasourceId, payload.datasourceId());
    if (isBlank(resolvedDatasourceId)) {
      throw new AppException(
          "ZABBIX_WEBHOOK_DATASOURCE_REQUIRED", "Zabbix datasourceId is required");
    }

    String problemId = firstNonBlank(payload.problemId(), payload.eventId());
    if (isBlank(problemId)) {
      throw new AppException("ZABBIX_WEBHOOK_EVENT_REQUIRED", "Zabbix eventId is required");
    }

    String objectId = firstNonBlank(payload.objectId(), payload.triggerId(), problemId);
    String sourceEventId = ZabbixExternalIds.sourceId(resolvedDatasourceId, problemId);
    String status = normalizeStatus(payload);
    Map<String, String> tags = normalizeTags(payload);

    Map<String, String> envAndService = extractEntityLabels(payload, tags);
    String app = envAndService.get("app");
    String env = envAndService.get("env");
    String service = envAndService.get("service");
    String endpoint = envAndService.get("endpoint");

    String entityType = inferEntityType(service, endpoint);
    String entityName = resolveEntityName(endpoint, service, payload, problemId);
    OffsetDateTime startsAt = resolveStartsAt(payload);

    OffsetDateTime endsAt =
        "resolved".equals(status) ? firstNonNull(payload.endsAt(), startsAt) : null;

    Map<String, Object> labels =
        buildLabels(
            new LabelsContext(resolvedDatasourceId, payload, problemId, objectId, tags),
            envAndService);

    String title = firstNonBlank(payload.title(), "Zabbix event " + problemId);
    String description = firstNonBlank(payload.message(), title);
    String fingerprint = ZabbixExternalIds.fingerprint(resolvedDatasourceId, objectId);
    String hostId = firstNonBlank(payload.hostId());
    String aggregationKey =
        ZabbixAggregationKeyBuilder.build(resolvedDatasourceId, hostId, service, env, startsAt);
    labels.put("aggregationKey", aggregationKey);

    return new ZabbixWebhookAlertMapping(
        resolvedDatasourceId,
        sourceEventId,
        hostIds(payload.hostId()),
        ZabbixSeverityMapper.map(payload.severity()),
        title,
        description,
        entityType,
        entityName,
        labels,
        startsAt,
        endsAt,
        status,
        payload,
        fingerprint,
        aggregationKey);
  }

  private Map<String, String> extractEntityLabels(
      ZabbixWebhookPayload payload, Map<String, String> tags) {
    Map<String, String> result = new LinkedHashMap<>();
    result.put(
        "app", firstNonBlank(payload.app(), ZabbixTagNormalizer.first(tags, "app", "application")));
    result.put(
        "env",
        firstNonBlank(
            payload.env(),
            payload.environment(),
            ZabbixTagNormalizer.first(tags, "env", "environment")));
    result.put(
        "service",
        firstNonBlank(
            payload.service(),
            payload.component(),
            ZabbixTagNormalizer.first(tags, "service", "component")));
    result.put(
        "endpoint",
        firstNonBlank(
            payload.endpoint(), payload.url(), ZabbixTagNormalizer.first(tags, "endpoint", "url")));
    return result;
  }

  private Map<String, Object> buildLabels(LabelsContext ctx, Map<String, String> envAndService) {
    Map<String, Object> labels = new LinkedHashMap<>();
    labels.put("datasourceId", ctx.datasourceId());
    labels.put("zabbixEventId", ctx.payload().eventId());
    labels.put("zabbixProblemId", ctx.problemId());
    labels.put("zabbixRecoveryEventId", ctx.payload().recoveryEventId());
    labels.put("zabbixTriggerId", ctx.payload().triggerId());
    labels.put("zabbixObjectId", ctx.objectId());
    labels.put("zabbixHostId", ctx.payload().hostId());
    labels.put("zabbixHostName", firstNonBlank(ctx.payload().hostName(), ctx.payload().host()));
    labels.put("zabbixStatus", ctx.payload().status());
    labels.put("zabbixEventValue", ctx.payload().eventValue());
    labels.put("zabbixTags", ctx.tags());
    putIfPresent(labels, "app", envAndService.get("app"));
    putIfPresent(labels, "env", envAndService.get("env"));
    putIfPresent(labels, "service", envAndService.get("service"));
    putIfPresent(labels, "endpoint", envAndService.get("endpoint"));
    return labels;
  }

  private record LabelsContext(
      String datasourceId,
      ZabbixWebhookPayload payload,
      String problemId,
      String objectId,
      Map<String, String> tags) {}

  private Map<String, String> normalizeTags(ZabbixWebhookPayload payload) {
    Map<String, String> result = new LinkedHashMap<>();
    result.putAll(
        ZabbixTagNormalizer.normalize(payload.tags() == null ? Map.of() : payload.tags()));
    putIfPresent(result, "app", payload.app());
    putIfPresent(result, "env", firstNonBlank(payload.env(), payload.environment()));
    putIfPresent(result, "service", firstNonBlank(payload.service(), payload.component()));
    putIfPresent(result, "endpoint", firstNonBlank(payload.endpoint(), payload.url()));
    return result;
  }

  private String normalizeStatus(ZabbixWebhookPayload payload) {
    String status = firstNonBlank(payload.status(), payload.eventValue());
    if (status == null) {
      return "open";
    }
    String normalized = status.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "resolved", "resolve", "ok", "closed", "0" -> "resolved";
      case "problem", "open", "firing", "1" -> "open";
      default -> "open";
    };
  }

  private String inferEntityType(String service, String endpoint) {
    if (!isBlank(endpoint)) {
      return "endpoint";
    }
    if (!isBlank(service)) {
      return "service";
    }
    return "host";
  }

  private OffsetDateTime resolveStartsAt(ZabbixWebhookPayload payload) {
    OffsetDateTime fromField = payload.startsAt();
    if (fromField != null) {
      return fromField;
    }
    OffsetDateTime fromClock = clockToOffset(payload.clock());
    return fromClock != null ? fromClock : OffsetDateTime.now(ZoneOffset.UTC);
  }

  private String resolveEntityName(
      String endpoint, String service, ZabbixWebhookPayload payload, String problemId) {
    if (!isBlank(endpoint)) {
      return endpoint;
    }
    if (!isBlank(service)) {
      return service;
    }
    if (!isBlank(payload.hostName())) {
      return payload.hostName();
    }
    if (!isBlank(payload.host())) {
      return payload.host();
    }
    return "Zabbix event " + problemId;
  }

  private OffsetDateTime clockToOffset(Long clock) {
    if (clock == null || clock <= 0) {
      return null;
    }
    return OffsetDateTime.ofInstant(Instant.ofEpochSecond(clock), ZoneOffset.UTC);
  }

  private List<String> hostIds(String hostId) {
    if (isBlank(hostId)) {
      return List.of();
    }
    return List.of(hostId.trim());
  }

  private <T> T firstNonNull(T first, T second) {
    return first != null ? first : second;
  }

  private String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (!isBlank(value)) {
        return value.trim();
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private void putIfPresent(Map<String, ?> rawMap, String key, String value) {
    if (isBlank(value)) {
      return;
    }
    ((Map<String, Object>) rawMap).put(key, value.trim());
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
