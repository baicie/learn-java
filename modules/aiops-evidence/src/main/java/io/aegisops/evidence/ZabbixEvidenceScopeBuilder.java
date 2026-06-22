package io.aegisops.evidence;

import io.aegisops.zabbix.ZabbixClient;
import io.aegisops.zabbix.ZabbixHost;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a {@link ZabbixEvidenceScope} from the alerts linked to an incident. Splits the scope into
 * {@code apiHostId} (a real Zabbix hostid, resolved from the alert labels or, as a last resort, by
 * looking up the host by name) and {@code displayHostKey} (a stable label used in evidence keys and
 * payloads). Also collects the precise Zabbix IDs found in the alert labels so event / trigger
 * queries can be narrowly targeted.
 */
final class ZabbixEvidenceScopeBuilder {
  private final List<ZabbixEvidenceDao.AlertContext> alerts;
  private final ZabbixClient client;

  ZabbixEvidenceScopeBuilder(List<ZabbixEvidenceDao.AlertContext> alerts, ZabbixClient client) {
    this.alerts = alerts;
    this.client = client;
  }

  ZabbixEvidenceCollectorService.ZabbixEvidenceScope build() {
    String explicitHostId = firstHostId();
    String hostName = firstHostName();
    String apiHostId = explicitHostId;

    if ((apiHostId == null || apiHostId.isBlank()) && hostName != null && !hostName.isBlank()) {
      apiHostId = resolveHostIdByName(hostName);
    }

    String displayHostKey = firstNonBlank(explicitHostId, hostName, "unknown-host");

    return new ZabbixEvidenceCollectorService.ZabbixEvidenceScope(
        apiHostId,
        displayHostKey,
        firstLabel("service"),
        firstLabel("env"),
        collectLabelValues("zabbixEventId", "zabbixProblemId"),
        collectLabelValues("zabbixTriggerId"),
        collectLabelValues("zabbixObjectId"));
  }

  private String firstHostId() {
    for (ZabbixEvidenceDao.AlertContext alert : alerts) {
      String hostId = stringLabel(alert.labels(), "zabbixHostId");
      if (hostId != null) {
        return hostId;
      }

      Object hostIds = alert.labels().get("zabbixHostIds");
      if (hostIds instanceof Iterable<?> iterable) {
        for (Object item : iterable) {
          if (item != null && !String.valueOf(item).isBlank()) {
            return String.valueOf(item).trim();
          }
        }
      }
    }
    return null;
  }

  private String firstHostName() {
    for (ZabbixEvidenceDao.AlertContext alert : alerts) {
      String hostName = stringLabel(alert.labels(), "zabbixHostName");
      if (hostName != null) {
        return hostName;
      }

      String aggregationHostKey = stringLabel(alert.labels(), "aggregationHostKey");
      if (aggregationHostKey != null) {
        return aggregationHostKey;
      }
    }
    return null;
  }

  private String resolveHostIdByName(String hostName) {
    if (hostName == null || hostName.isBlank()) {
      return null;
    }

    try {
      return client.getHosts(5000).stream()
          .filter(host -> hostNameMatches(host, hostName))
          .map(ZabbixHost::hostId)
          .filter(value -> value != null && !value.isBlank())
          .findFirst()
          .orElse(null);
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private static boolean hostNameMatches(ZabbixHost host, String hostName) {
    if (host == null || hostName == null) {
      return false;
    }

    return hostName.equalsIgnoreCase(nullToBlank(host.host()))
        || hostName.equalsIgnoreCase(nullToBlank(host.name()))
        || hostName.equalsIgnoreCase(nullToBlank(host.hostId()));
  }

  private String firstLabel(String key) {
    for (ZabbixEvidenceDao.AlertContext alert : alerts) {
      String value = stringLabel(alert.labels(), key);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private List<String> collectLabelValues(String... keys) {
    Set<String> values = new LinkedHashSet<>();

    for (ZabbixEvidenceDao.AlertContext alert : alerts) {
      for (String key : keys) {
        addLabelValue(values, alert.labels().get(key));
      }
    }

    return List.copyOf(values);
  }

  private static void addLabelValue(Set<String> values, Object value) {
    if (value == null) {
      return;
    }

    if (value instanceof Iterable<?> iterable) {
      for (Object item : iterable) {
        addLabelValue(values, item);
      }
      return;
    }

    String text = String.valueOf(value).trim();
    if (!text.isBlank() && !"null".equalsIgnoreCase(text)) {
      values.add(text);
    }
  }

  private static String stringLabel(Map<String, Object> labels, String key) {
    if (labels == null || key == null) {
      return null;
    }

    Object value = labels.get(key);
    if (value == null || String.valueOf(value).isBlank()) {
      return null;
    }
    return String.valueOf(value).trim();
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

  private static String nullToBlank(String value) {
    return value == null ? "" : value;
  }
}
