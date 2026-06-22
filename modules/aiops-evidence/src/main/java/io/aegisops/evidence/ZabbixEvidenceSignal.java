package io.aegisops.evidence;

import io.aegisops.zabbix.ZabbixItem;
import java.util.Locale;

public enum ZabbixEvidenceSignal {
  CPU_UTILIZATION("metric_cpu_high"),
  MEMORY_UTILIZATION("metric_memory_high"),
  LOAD_AVERAGE("metric_load_high"),
  ORDER_CREATE_LATENCY("metric_api_slow"),
  HEALTH_STATUS("metric_health_check_failed"),
  ERROR_COUNT("metric_error_log_increased"),
  UNKNOWN("unknown");

  private final String evidenceType;

  ZabbixEvidenceSignal(String evidenceType) {
    this.evidenceType = evidenceType;
  }

  public String evidenceType() {
    return evidenceType;
  }

  public static ZabbixEvidenceSignal classify(ZabbixItem item) {
    if (item == null || item.key() == null) {
      return UNKNOWN;
    }

    String key = item.key().toLowerCase(Locale.ROOT);
    String name = item.name() == null ? "" : item.name().toLowerCase(Locale.ROOT);
    String text = key + " " + name;

    if (text.contains("cpu")) {
      return CPU_UTILIZATION;
    }

    if (text.contains("memory") || text.contains("vm.memory")) {
      return MEMORY_UTILIZATION;
    }

    if (text.contains("load")) {
      return LOAD_AVERAGE;
    }

    if (text.contains("order.create.time")
        || text.contains("response.time")
        || text.contains("web.test.time")
        || text.contains("api slow")) {
      return ORDER_CREATE_LATENCY;
    }

    if (text.contains("health.status")
        || text.contains("health")
        || text.contains("rspcode")
        || text.contains("status")) {
      return HEALTH_STATUS;
    }

    if (text.contains("error.count") || text.contains("log") || text.contains("error")) {
      return ERROR_COUNT;
    }

    return UNKNOWN;
  }
}
