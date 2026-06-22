package io.aegisops.zabbix;

import java.time.Instant;
import java.util.List;

public record ZabbixTrendQuery(
    List<String> itemIds, Instant timeFrom, Instant timeTill, int limit) {
  public int normalizedLimit() {
    if (limit <= 0) {
      return 5000;
    }
    return Math.min(limit, 20000);
  }
}
