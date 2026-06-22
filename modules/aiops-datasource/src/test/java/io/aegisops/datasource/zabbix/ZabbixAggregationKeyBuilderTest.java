package io.aegisops.datasource.zabbix;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZabbixAggregationKeyBuilderTest {

  @Test
  void shouldBuildAggregationKeyWithTenMinuteBucket() {
    String key =
        ZabbixAggregationKeyBuilder.build(
            "ds_1",
            "10084",
            "order-service",
            "demo",
            OffsetDateTime.parse("2026-06-21T14:19:59+09:00"));

    assertThat(key).isEqualTo("zabbix:ds_1:10084:order-service:demo:202606210510");
  }

  @Test
  void shouldNormalizeParts() {
    String key =
        ZabbixAggregationKeyBuilder.build(
            "DS 1",
            "Host 10084",
            "Order Service",
            "Demo Env",
            OffsetDateTime.parse("2026-06-21T05:10:00Z"));

    assertThat(key).isEqualTo("zabbix:ds-1:host-10084:order-service:demo-env:202606210510");
  }

  @Test
  void shouldBuildFromLabels() {
    String key =
        ZabbixAggregationKeyBuilder.buildFromLabels(
            "ds_1",
            Map.of(
                "zabbixHostIds", List.of("10084"),
                "service", "order-service",
                "env", "demo"),
            OffsetDateTime.parse("2026-06-21T05:10:00Z"));

    assertThat(key).isEqualTo("zabbix:ds_1:10084:order-service:demo:202606210510");
  }
}
