package io.aegisops.zabbix;

import java.util.List;

public record ZabbixTriggerQuery(
    List<String> hostIds, List<String> triggerIds, String descriptionSearch, int limit) {
  public int normalizedLimit() {
    if (limit <= 0) {
      return 100;
    }
    return Math.min(limit, 1000);
  }
}
