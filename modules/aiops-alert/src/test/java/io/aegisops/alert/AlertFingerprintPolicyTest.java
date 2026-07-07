package io.aegisops.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AlertFingerprintPolicyTest {

  @Test
  void fingerprint_shouldUseSourceAssetEventAndTitle() {
    AlertFingerprintPolicy policy = new AlertFingerprintPolicy();
    AlertIngestRequest request =
        new AlertIngestRequest(
            "ZABBIX",
            "10001",
            "high",
            "CPU > 90%",
            null,
            "host-1",
            "HOST",
            "order-service",
            Map.of(),
            null,
            null,
            "open",
            Map.of());

    assertThat(policy.fingerprint(request)).isEqualTo("zabbix:host-1:10001:cpu-90");
    assertThat(policy.aggregationKey(request)).isEqualTo("zabbix:host-1:order-service");
  }

  @Test
  void fingerprint_shouldNormalizeChineseCharacters() {
    AlertFingerprintPolicy policy = new AlertFingerprintPolicy();
    AlertIngestRequest request =
        new AlertIngestRequest(
            "ZABBIX",
            "10002",
            "high",
            "磁盘使用率 > 90%",
            null,
            "host-2",
            "HOST",
            "order-service",
            Map.of(),
            null,
            null,
            "open",
            Map.of());

    assertThat(policy.fingerprint(request)).isEqualTo("zabbix:host-2:10002:磁盘使用率-90");
  }

  @Test
  void fingerprint_shouldHandleNullValues() {
    AlertFingerprintPolicy policy = new AlertFingerprintPolicy();
    AlertIngestRequest request =
        new AlertIngestRequest(
            null,
            null,
            null,
            "Disk Full",
            null,
            null,
            null,
            null,
            Map.of(),
            null,
            null,
            null,
            Map.of());

    assertThat(policy.fingerprint(request)).isEqualTo("unknown:no-asset:no-event:disk-full");
    assertThat(policy.aggregationKey(request)).isEqualTo("unknown:no-asset:unknown-service");
  }
}
