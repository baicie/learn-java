package io.aegisops.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.aegisops.zabbix.ZabbixClient;
import io.aegisops.zabbix.ZabbixHost;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ZabbixEvidenceCollectorScopeTest {
  @Test
  void shouldResolveApiHostIdFromHostName() {
    ZabbixClient client = Mockito.mock(ZabbixClient.class);
    when(client.getHosts(5000))
        .thenReturn(
            List.of(
                new ZabbixHost(
                    "10084",
                    "aiops-demo-host",
                    "AegisOps Demo Host",
                    "0",
                    "127.0.0.1",
                    List.of("AegisOps Demo"),
                    null)));

    ZabbixEvidenceDao.AlertContext alert =
        new ZabbixEvidenceDao.AlertContext(
            "alert_1",
            "zabbix",
            "ds_1:20001",
            "asset_1",
            "order-service",
            Map.of(
                "zabbixHostName", "aiops-demo-host",
                "service", "order-service",
                "env", "demo",
                "zabbixEventId", "20001",
                "zabbixObjectId", "30001"),
            OffsetDateTime.parse("2026-06-21T05:10:00Z"),
            null);

    ZabbixEvidenceCollectorService.ZabbixEvidenceScope scope =
        new ZabbixEvidenceScopeBuilder(List.of(alert), client).build();

    assertThat(scope.apiHostId()).isEqualTo("10084");
    assertThat(scope.displayHostKey()).isEqualTo("aiops-demo-host");
    assertThat(scope.eventIds()).containsExactly("20001");
    assertThat(scope.objectIds()).containsExactly("30001");
    assertThat(scope.service()).isEqualTo("order-service");
    assertThat(scope.env()).isEqualTo("demo");
  }

  @Test
  void shouldPreferExplicitHostIdOverNameResolution() {
    ZabbixClient client = Mockito.mock(ZabbixClient.class);
    // Should not be called when an explicit hostId is present
    when(client.getHosts(Mockito.anyInt()))
        .thenReturn(
            List.of(
                new ZabbixHost(
                    "99999", "other-host", "Other Host", "0", "127.0.0.1", List.of(), null)));

    ZabbixEvidenceDao.AlertContext alert =
        new ZabbixEvidenceDao.AlertContext(
            "alert_1",
            "zabbix",
            "ds_1:20001",
            "asset_1",
            "order-service",
            Map.of(
                "zabbixHostId", "10084",
                "zabbixHostName", "aiops-demo-host",
                "zabbixEventId", "20001",
                "zabbixTriggerId", "30001",
                "zabbixObjectId", "30001"),
            OffsetDateTime.parse("2026-06-21T05:10:00Z"),
            null);

    ZabbixEvidenceCollectorService.ZabbixEvidenceScope scope =
        new ZabbixEvidenceScopeBuilder(List.of(alert), client).build();

    assertThat(scope.apiHostId()).isEqualTo("10084");
    assertThat(scope.displayHostKey()).isEqualTo("10084");
    assertThat(scope.eventIds()).containsExactly("20001");
    assertThat(scope.triggerIds()).containsExactly("30001");
    assertThat(scope.objectIds()).containsExactly("30001");
    Mockito.verify(client, Mockito.never()).getHosts(Mockito.anyInt());
  }

  @Test
  void shouldReturnNullApiHostIdWhenNameResolutionFails() {
    ZabbixClient client = Mockito.mock(ZabbixClient.class);
    when(client.getHosts(5000)).thenReturn(List.of());

    ZabbixEvidenceDao.AlertContext alert =
        new ZabbixEvidenceDao.AlertContext(
            "alert_1",
            "zabbix",
            "ds_1:20001",
            "asset_1",
            "order-service",
            Map.of("zabbixHostName", "unknown-host"),
            OffsetDateTime.parse("2026-06-21T05:10:00Z"),
            null);

    ZabbixEvidenceCollectorService.ZabbixEvidenceScope scope =
        new ZabbixEvidenceScopeBuilder(List.of(alert), client).build();

    assertThat(scope.apiHostId()).isNull();
    assertThat(scope.displayHostKey()).isEqualTo("unknown-host");
  }

  @Test
  void shouldCollectLabelValuesAcrossAlerts() {
    ZabbixClient client = Mockito.mock(ZabbixClient.class);
    when(client.getHosts(5000)).thenReturn(List.of());

    ZabbixEvidenceDao.AlertContext alert1 =
        new ZabbixEvidenceDao.AlertContext(
            "alert_1",
            "zabbix",
            "ds_1:20001",
            "asset_1",
            "order-service",
            Map.of(
                "zabbixHostName", "demo-host",
                "zabbixEventId", "20001",
                "zabbixObjectId", "30001"),
            OffsetDateTime.parse("2026-06-21T05:10:00Z"),
            null);
    ZabbixEvidenceDao.AlertContext alert2 =
        new ZabbixEvidenceDao.AlertContext(
            "alert_2",
            "zabbix",
            "ds_1:20002",
            "asset_1",
            "order-service",
            Map.of(
                "zabbixEventId", "20002",
                "zabbixObjectId", "30002"),
            OffsetDateTime.parse("2026-06-21T05:11:00Z"),
            null);

    ZabbixEvidenceCollectorService.ZabbixEvidenceScope scope =
        new ZabbixEvidenceScopeBuilder(List.of(alert1, alert2), client).build();

    assertThat(scope.eventIds()).containsExactly("20001", "20002");
    assertThat(scope.objectIds()).containsExactly("30001", "30002");
  }

  @Test
  void shouldSwallowClientFailuresDuringNameResolution() {
    ZabbixClient client = Mockito.mock(ZabbixClient.class);
    when(client.getHosts(Mockito.anyInt())).thenThrow(new RuntimeException("zabbix unavailable"));

    ZabbixEvidenceDao.AlertContext alert =
        new ZabbixEvidenceDao.AlertContext(
            "alert_1",
            "zabbix",
            "ds_1:20001",
            "asset_1",
            "order-service",
            Map.of("zabbixHostName", "aiops-demo-host"),
            OffsetDateTime.parse("2026-06-21T05:10:00Z"),
            null);

    ZabbixEvidenceCollectorService.ZabbixEvidenceScope scope =
        new ZabbixEvidenceScopeBuilder(List.of(alert), client).build();

    assertThat(scope.apiHostId()).isNull();
    assertThat(scope.displayHostKey()).isEqualTo("aiops-demo-host");
  }
}
