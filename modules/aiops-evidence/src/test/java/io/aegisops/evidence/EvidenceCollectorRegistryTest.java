package io.aegisops.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceCollectorRegistryTest {

  @Test
  void getShouldReturnCollectorByKey() {
    EvidenceCollector collector = new FakeCollector("zabbix.metric-event");
    EvidenceCollectorRegistry registry = new EvidenceCollectorRegistry(List.of(collector));

    assertThat(registry.get("zabbix.metric-event")).isSameAs(collector);
  }

  @Test
  void getShouldThrowWhenCollectorMissing() {
    EvidenceCollectorRegistry registry = new EvidenceCollectorRegistry(List.of());

    assertThatThrownBy(() -> registry.get("missing"))
        .isInstanceOf(io.aegisops.common.exception.AppException.class)
        .hasMessageContaining("missing");
  }

  @Test
  void constructorShouldRejectDuplicateCollectorKeys() {
    EvidenceCollector left = new FakeCollector("zabbix.metric-event");
    EvidenceCollector right = new FakeCollector("zabbix.metric-event");

    assertThatThrownBy(() -> new EvidenceCollectorRegistry(List.of(left, right)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate EvidenceCollector key");
  }

  @Test
  void getSupportedShouldThrowWhenCollectorDoesNotSupportRequest() {
    EvidenceCollector collector = new FakeCollector("zabbix.metric-event");
    EvidenceCollectorRegistry registry = new EvidenceCollectorRegistry(List.of(collector));

    EvidenceCollectRequest request = new EvidenceCollectRequest(30, null, null, "other.collector");
    assertThatThrownBy(() -> registry.getSupported("zabbix.metric-event", request))
        .isInstanceOf(io.aegisops.common.exception.AppException.class)
        .hasMessageContaining("does not support");
  }

  record FakeCollector(String collectorKey) implements EvidenceCollector {
    @Override
    public boolean supports(EvidenceCollectRequest request) {
      return request == null
          || request.collectorKey() == null
          || request.collectorKey().isBlank()
          || collectorKey().equals(request.collectorKey());
    }

    @Override
    public EvidenceCollectResponse collect(
        String tenantId, String incidentId, EvidenceCollectRequest request) {
      return new EvidenceCollectResponse(incidentId, 0, 0, 0, 0, 0, 0, 0, "ok");
    }
  }
}
