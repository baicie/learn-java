package io.aegisops.datasource.zabbix;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZabbixSeverityMapperTest {
  @Test
  void shouldMapNumericSeverity() {
    assertThat(ZabbixSeverityMapper.map("0")).isEqualTo("info");
    assertThat(ZabbixSeverityMapper.map("1")).isEqualTo("warning");
    assertThat(ZabbixSeverityMapper.map("2")).isEqualTo("warning");
    assertThat(ZabbixSeverityMapper.map("3")).isEqualTo("medium");
    assertThat(ZabbixSeverityMapper.map("4")).isEqualTo("high");
    assertThat(ZabbixSeverityMapper.map("5")).isEqualTo("critical");
  }

  @Test
  void shouldMapTextSeverity() {
    assertThat(ZabbixSeverityMapper.map("information")).isEqualTo("info");
    assertThat(ZabbixSeverityMapper.map("warning")).isEqualTo("warning");
    assertThat(ZabbixSeverityMapper.map("average")).isEqualTo("medium");
    assertThat(ZabbixSeverityMapper.map("high")).isEqualTo("high");
    assertThat(ZabbixSeverityMapper.map("disaster")).isEqualTo("critical");
  }

  @Test
  void shouldFallbackToWarning() {
    assertThat(ZabbixSeverityMapper.map(null)).isEqualTo("warning");
    assertThat(ZabbixSeverityMapper.map("unknown")).isEqualTo("warning");
  }
}
