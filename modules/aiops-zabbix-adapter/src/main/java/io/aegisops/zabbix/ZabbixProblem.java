package io.aegisops.zabbix;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ZabbixProblem(
    String eventId,
    String objectId,
    String name,
    int severity,
    Instant clock,
    List<String> hostIds,
    Map<String, String> tags,
    JsonNode raw) {

  public String recoveryEventId() {
    if (raw == null) {
      return null;
    }
    String value = raw.path("r_eventid").asText(null);
    return value == null || value.isBlank() || "0".equals(value) ? null : value;
  }

  public Instant recoveryClock() {
    if (raw == null) {
      return null;
    }
    long epochSeconds = raw.path("r_clock").asLong(0);
    return epochSeconds > 0 ? Instant.ofEpochSecond(epochSeconds) : null;
  }

  public boolean recovered() {
    return recoveryEventId() != null || recoveryClock() != null;
  }
}
