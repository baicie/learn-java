package io.aegisops.datasource.zabbix;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.zabbix.ZabbixHost;
import io.aegisops.zabbix.ZabbixProblem;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZabbixSyncMapperTest {
  private final ZabbixSyncMapper mapper = new ZabbixSyncMapper();

  @Test
  void shouldMapHostToAssetMapping() {
    ZabbixHost host =
        new ZabbixHost(
            "10084",
            "aiops-demo-host",
            "AI Ops Demo Host",
            "0",
            "10.0.0.10",
            List.of("Linux servers"),
            null);

    ZabbixHostAssetMapping mapping = mapper.mapHost("ds_1", host);

    assertThat(mapping.sourceId()).isEqualTo("ds_1:10084");
    assertThat(mapping.name()).isEqualTo("aiops-demo-host");
    assertThat(mapping.displayName()).isEqualTo("AI Ops Demo Host");
    assertThat(mapping.ip()).isEqualTo("10.0.0.10");
    assertThat(mapping.status()).isEqualTo("active");
    assertThat(mapping.tags())
        .containsEntry("datasourceId", "ds_1")
        .containsEntry("zabbixHostId", "10084");
  }

  @Test
  void shouldMapDisabledHost() {
    ZabbixHost host =
        new ZabbixHost(
            "10084", "aiops-demo-host", "AI Ops Demo Host", "1", "10.0.0.10", List.of(), null);

    ZabbixHostAssetMapping mapping = mapper.mapHost("ds_1", host);

    assertThat(mapping.status()).isEqualTo("disabled");
  }

  @Test
  void shouldMapProblemToServiceAlertWhenServiceTagExists() {
    ZabbixProblem problem =
        new ZabbixProblem(
            "20001",
            "30001",
            "order-service API Slow",
            4,
            Instant.parse("2026-06-21T05:10:00Z"),
            List.of("10084"),
            Map.of("app", "mall", "env", "demo", "service", "order-service"),
            null);

    ZabbixAlertEventMapping mapping = mapper.mapProblem("ds_1", problem);

    assertThat(mapping.sourceEventId()).isEqualTo("ds_1:20001");
    assertThat(mapping.hostIds()).containsExactly("10084");
    assertThat(mapping.severity()).isEqualTo("high");
    assertThat(mapping.title()).isEqualTo("order-service API Slow");
    assertThat(mapping.entityType()).isEqualTo("service");
    assertThat(mapping.entityName()).isEqualTo("order-service");
    assertThat(mapping.status()).isEqualTo("open");
    assertThat(mapping.fingerprint()).isEqualTo("zabbix:ds_1:30001");
    assertThat(mapping.labels())
        .containsEntry("datasourceId", "ds_1")
        .containsEntry("zabbixEventId", "20001")
        .containsEntry("zabbixObjectId", "30001")
        .containsEntry("app", "mall")
        .containsEntry("env", "demo")
        .containsEntry("service", "order-service");
  }

  @Test
  void shouldMapProblemToEndpointAlertWhenEndpointTagExists() {
    ZabbixProblem problem =
        new ZabbixProblem(
            "20001",
            "30001",
            "order API slow",
            3,
            Instant.parse("2026-06-21T05:10:00Z"),
            List.of("10084"),
            Map.of("service", "order-service", "endpoint", "/api/order/create"),
            null);

    ZabbixAlertEventMapping mapping = mapper.mapProblem("ds_1", problem);

    assertThat(mapping.entityType()).isEqualTo("endpoint");
    assertThat(mapping.entityName()).isEqualTo("/api/order/create");
    assertThat(mapping.severity()).isEqualTo("medium");
    assertThat(mapping.labels()).containsEntry("endpoint", "/api/order/create");
  }

  @Test
  void shouldReturnNullWhenHostIdMissing() {
    ZabbixHost host = new ZabbixHost(null, "host", "host", "0", "127.0.0.1", List.of(), null);

    assertThat(mapper.mapHost("ds_1", host)).isNull();
  }

  @Test
  void shouldReturnNullWhenProblemEventIdMissing() {
    ZabbixProblem problem =
        new ZabbixProblem(
            null,
            "30001",
            "problem",
            2,
            Instant.parse("2026-06-21T05:10:00Z"),
            List.of("10084"),
            Map.of(),
            null);

    assertThat(mapper.mapProblem("ds_1", problem)).isNull();
  }
}
