package io.aegisops.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceCollectorRegistryTest {

  @Test
  void get_shouldReturnCollectorByKey() {
    EvidenceCollector collector = new FakeCollector("zabbix.metric-event");
    EvidenceCollectorRegistry registry = new EvidenceCollectorRegistry(List.of(collector));
    assertThat(registry.get("zabbix.metric-event")).isSameAs(collector);
  }

  @Test
  void get_shouldThrowWhenCollectorMissing() {
    EvidenceCollectorRegistry registry = new EvidenceCollectorRegistry(List.of());
    assertThatThrownBy(() -> registry.get("missing"))
        .isInstanceOf(io.aegisops.common.exception.AppException.class)
        .hasMessageContaining("missing");
  }

  record FakeCollector(String collectorKey) implements EvidenceCollector {
    @Override
    public boolean supports(EvidenceCollectRequest request) {
      return true;
    }

    @Override
    public EvidenceCollectResponse collect(
        String tenantId, String incidentId, EvidenceCollectRequest request) {
      return new EvidenceCollectResponse(incidentId, 0, 0, 0, 0, 0, 0, 0, "ok");
    }
  }
}
