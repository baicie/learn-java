package io.aegisops.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class AegisOpsReadinessIndicatorTest {
  @Test
  void readinessIsUp() {
    ObservabilityProperties properties = new ObservabilityProperties();
    properties.setServiceName("aiops-server");

    AegisOpsReadinessIndicator indicator = new AegisOpsReadinessIndicator(properties);

    var health = indicator.health();

    assertEquals(Status.UP, health.getStatus());
    assertEquals("aiops-server", health.getDetails().get("service"));
  }
}
