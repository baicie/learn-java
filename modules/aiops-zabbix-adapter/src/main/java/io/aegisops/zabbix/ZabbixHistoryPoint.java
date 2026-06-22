package io.aegisops.zabbix;

import java.time.Instant;
import java.util.Map;
import java.util.OptionalDouble;

public record ZabbixHistoryPoint(
    String itemId, int valueType, Instant clock, String value, Map<String, Object> raw) {
  public OptionalDouble doubleValue() {
    if (value == null || value.isBlank()) {
      return OptionalDouble.empty();
    }

    try {
      return OptionalDouble.of(Double.parseDouble(value));
    } catch (NumberFormatException ex) {
      return OptionalDouble.empty();
    }
  }
}
