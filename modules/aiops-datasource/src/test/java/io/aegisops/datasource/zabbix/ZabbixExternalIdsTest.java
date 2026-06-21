package io.aegisops.datasource.zabbix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ZabbixExternalIdsTest {
  @Test
  void shouldBuildSourceId() {
    assertThat(ZabbixExternalIds.sourceId("ds_1", "10084")).isEqualTo("ds_1:10084");
  }

  @Test
  void shouldBuildFingerprint() {
    assertThat(ZabbixExternalIds.fingerprint("ds_1", "trigger_1"))
        .isEqualTo("zabbix:ds_1:trigger_1");
  }

  @Test
  void shouldRejectBlankDatasourceId() {
    assertThatThrownBy(() -> ZabbixExternalIds.sourceId(" ", "10084"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("datasourceId");
  }

  @Test
  void shouldNormalizeEntityType() {
    assertThat(ZabbixExternalIds.normalizeEntityType("SERVICE")).isEqualTo("service");
    assertThat(ZabbixExternalIds.normalizeEntityType("unknown")).isEqualTo("host");
    assertThat(ZabbixExternalIds.normalizeEntityType(null)).isEqualTo("host");
  }
}
