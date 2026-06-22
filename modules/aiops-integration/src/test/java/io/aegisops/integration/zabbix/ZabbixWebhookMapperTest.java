package io.aegisops.integration.zabbix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aegisops.common.exception.AppException;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZabbixWebhookMapperTest {

  private final ZabbixWebhookMapper mapper = new ZabbixWebhookMapper();

  @Test
  void shouldMapProblemEventToOpenAlert() {
    ZabbixWebhookPayload payload =
        new ZabbixWebhookPayload(
            "ds_1",
            "20001",
            null,
            null,
            "30001",
            null,
            "1",
            "PROBLEM",
            "High",
            "AegisOps Demo CPU High",
            "CPU is high",
            "10084",
            "aiops-demo-host",
            null,
            "mall",
            "demo",
            null,
            "order-service",
            null,
            null,
            null,
            OffsetDateTime.parse("2026-06-21T05:10:00Z"),
            null,
            null,
            Map.of("service", "order-service"));

    ZabbixWebhookAlertMapping mapping = mapper.map(null, payload);

    assertThat(mapping.datasourceId()).isEqualTo("ds_1");
    assertThat(mapping.sourceEventId()).isEqualTo("ds_1:20001");
    assertThat(mapping.hostIds()).containsExactly("10084");
    assertThat(mapping.severity()).isEqualTo("high");
    assertThat(mapping.title()).isEqualTo("AegisOps Demo CPU High");
    assertThat(mapping.entityType()).isEqualTo("service");
    assertThat(mapping.entityName()).isEqualTo("order-service");
    assertThat(mapping.status()).isEqualTo("open");
    assertThat(mapping.endsAt()).isNull();
    assertThat(mapping.fingerprint()).isEqualTo("zabbix:ds_1:30001");
    assertThat(mapping.labels())
        .containsEntry("datasourceId", "ds_1")
        .containsEntry("zabbixEventId", "20001")
        .containsEntry("zabbixTriggerId", "30001")
        .containsEntry("service", "order-service");
  }

  @Test
  void shouldPreferRequestParamDatasourceId() {
    ZabbixWebhookPayload payload =
        new ZabbixWebhookPayload(
            "payload_ds",
            "20001",
            null,
            null,
            "30001",
            null,
            "1",
            "PROBLEM",
            "Warning",
            "API Slow",
            "API slow",
            "10084",
            "host",
            null,
            null,
            null,
            null,
            "order-service",
            null,
            null,
            null,
            null,
            null,
            null,
            Map.of());

    ZabbixWebhookAlertMapping mapping = mapper.map("query_ds", payload);

    assertThat(mapping.datasourceId()).isEqualTo("query_ds");
    assertThat(mapping.sourceEventId()).isEqualTo("query_ds:20001");
  }

  @Test
  void shouldMapResolvedEvent() {
    ZabbixWebhookPayload payload =
        new ZabbixWebhookPayload(
            "ds_1",
            "20001",
            "20001",
            "20002",
            "30001",
            null,
            "0",
            "RESOLVED",
            "High",
            "AegisOps Demo CPU High",
            "CPU recovered",
            "10084",
            "aiops-demo-host",
            null,
            "mall",
            "demo",
            null,
            "order-service",
            null,
            null,
            null,
            OffsetDateTime.parse("2026-06-21T05:10:00Z"),
            OffsetDateTime.parse("2026-06-21T05:20:00Z"),
            null,
            Map.of());

    ZabbixWebhookAlertMapping mapping = mapper.map(null, payload);

    assertThat(mapping.sourceEventId()).isEqualTo("ds_1:20001");
    assertThat(mapping.status()).isEqualTo("resolved");
    assertThat(mapping.endsAt()).isEqualTo(OffsetDateTime.parse("2026-06-21T05:20:00Z"));
  }

  @Test
  void shouldInferEndpointEntity() {
    ZabbixWebhookPayload payload =
        new ZabbixWebhookPayload(
            "ds_1",
            "20001",
            null,
            null,
            "30001",
            null,
            "1",
            "PROBLEM",
            "Average",
            "Order API Slow",
            "Order API slow",
            "10084",
            "host",
            null,
            "mall",
            "demo",
            null,
            "order-service",
            null,
            "/api/order/create",
            null,
            null,
            null,
            1_782_000_000L,
            Map.of());

    ZabbixWebhookAlertMapping mapping = mapper.map(null, payload);

    assertThat(mapping.entityType()).isEqualTo("endpoint");
    assertThat(mapping.entityName()).isEqualTo("/api/order/create");
    assertThat(mapping.startsAt()).isNotNull();
  }

  @Test
  void shouldRejectMissingDatasourceId() {
    ZabbixWebhookPayload payload =
        new ZabbixWebhookPayload(
            null,
            "20001",
            null,
            null,
            "30001",
            null,
            "1",
            "PROBLEM",
            "High",
            "CPU High",
            "CPU high",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Map.of());

    assertThatThrownBy(() -> mapper.map(null, payload))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("datasourceId");
  }

  @Test
  void shouldRejectMissingEventId() {
    ZabbixWebhookPayload payload =
        new ZabbixWebhookPayload(
            "ds_1",
            null,
            null,
            null,
            null,
            null,
            "1",
            "PROBLEM",
            "High",
            "CPU High",
            "CPU high",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Map.of());

    assertThatThrownBy(() -> mapper.map(null, payload))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("eventId");
  }
}
