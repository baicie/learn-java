package io.aegisops.zabbix;

import java.time.Instant;
import java.util.List;

public record ZabbixEventQuery(
    List<String> eventIds,
    List<String> hostIds,
    List<String> objectIds,
    Instant timeFrom,
    Instant timeTill,
    int limit) {
  public int normalizedLimit() {
    if (limit <= 0) {
      return 100;
    }
    return Math.min(limit, 1000);
  }
}
