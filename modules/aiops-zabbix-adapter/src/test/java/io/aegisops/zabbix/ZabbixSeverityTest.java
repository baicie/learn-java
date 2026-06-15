package io.aegisops.zabbix;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ZabbixSeverityTest {
  @Test
  void mapsLowNumericSeveritiesToInfo() {
    assertEquals("info", ZabbixSeverity.map(0));
    assertEquals("info", ZabbixSeverity.map(1));
  }

  @Test
  void mapsMediumSeveritiesToWarning() {
    assertEquals("warning", ZabbixSeverity.map(2));
    assertEquals("warning", ZabbixSeverity.map(3));
  }

  @Test
  void mapsHighToCritical() {
    assertEquals("critical", ZabbixSeverity.map(4));
  }

  @Test
  void mapsDisasterToDisaster() {
    assertEquals("disaster", ZabbixSeverity.map(5));
  }

  @Test
  void fallsBackToInfoForUnknown() {
    assertEquals("info", ZabbixSeverity.map(99));
    assertEquals("info", ZabbixSeverity.map(-1));
  }
}
