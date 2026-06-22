package io.aegisops.zabbix;

import java.util.List;

public record ZabbixItemQuery(
    List<String> hostIds, String keySearch, String nameSearch, int limit) {
  public int normalizedLimit() {
    if (limit <= 0) {
      return 500;
    }
    return Math.min(limit, 5000);
  }
}
