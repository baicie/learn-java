package io.aegisops.datasource.zabbix;

import io.aegisops.zabbix.ZabbixHost;
import io.aegisops.zabbix.ZabbixProblem;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ZabbixSyncMapper {
  public ZabbixHostAssetMapping mapHost(String datasourceId, ZabbixHost host) {
    if (host == null || isBlank(host.hostId())) {
      return null;
    }

    String sourceId = ZabbixExternalIds.sourceId(datasourceId, host.hostId());
    String name = firstNonBlank(host.host(), host.name(), "zabbix-host-" + host.hostId());
    String displayName = firstNonBlank(host.name(), host.host(), name);

    Map<String, Object> tags = new LinkedHashMap<>();
    tags.put("datasourceId", datasourceId);
    tags.put("zabbixHostId", host.hostId());
    tags.put("groups", host.groups());

    String status = "1".equals(host.status()) ? "disabled" : "active";

    return new ZabbixHostAssetMapping(sourceId, name, displayName, host.ip(), tags, status);
  }

  public ZabbixAlertEventMapping mapProblem(String datasourceId, ZabbixProblem problem) {
    if (problem == null || isBlank(problem.eventId())) {
      return null;
    }

    Map<String, String> normalizedTags = ZabbixTagNormalizer.normalize(problem.tags());

    String app = ZabbixTagNormalizer.first(normalizedTags, "app", "application");
    String env = ZabbixTagNormalizer.first(normalizedTags, "env", "environment");
    String service = ZabbixTagNormalizer.first(normalizedTags, "service", "component");
    String endpoint = ZabbixTagNormalizer.first(normalizedTags, "endpoint", "api", "url");

    String entityType = inferEntityType(service, endpoint);
    String entityName =
        firstNonBlank(endpoint, service, problem.name(), "zabbix-problem-" + problem.eventId());

    Map<String, Object> labels = new LinkedHashMap<>();
    labels.put("datasourceId", datasourceId);
    labels.put("zabbixEventId", problem.eventId());
    labels.put("zabbixObjectId", problem.objectId());
    labels.put("zabbixHostIds", safeList(problem.hostIds()));
    labels.put("zabbixTags", problem.tags());
    labels.put("normalizedTags", normalizedTags);

    putIfPresent(labels, "app", app);
    putIfPresent(labels, "env", env);
    putIfPresent(labels, "service", service);
    putIfPresent(labels, "endpoint", endpoint);

    String fingerprintKey = firstNonBlank(problem.objectId(), problem.eventId());
    String title = firstNonBlank(problem.name(), "Zabbix problem " + problem.eventId());
    OffsetDateTime startsAt = OffsetDateTime.ofInstant(problem.clock(), ZoneOffset.UTC);

    return new ZabbixAlertEventMapping(
        ZabbixExternalIds.sourceId(datasourceId, problem.eventId()),
        safeList(problem.hostIds()),
        ZabbixSeverityMapper.map(problem.severity()),
        title,
        "Zabbix problem event " + problem.eventId(),
        entityType,
        entityName,
        labels,
        startsAt,
        "open",
        problem.raw(),
        ZabbixExternalIds.fingerprint(datasourceId, fingerprintKey));
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

  private List<String> safeList(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }

    List<String> result = new ArrayList<>();
    for (String value : values) {
      if (!isBlank(value)) {
        result.add(value.trim());
      }
    }
    return List.copyOf(result);
  }

  private void putIfPresent(Map<String, Object> labels, String key, String value) {
    if (!isBlank(value)) {
      labels.put(key, value.trim());
    }
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

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
