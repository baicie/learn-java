package io.aegisops.observability;

import java.time.OffsetDateTime;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

public class AegisOpsReadinessIndicator implements HealthIndicator {
  private final ObservabilityProperties properties;
  private final OffsetDateTime startedAt = OffsetDateTime.now();

  public AegisOpsReadinessIndicator(ObservabilityProperties properties) {
    this.properties = properties;
  }

  @Override
  public Health health() {
    return Health.up()
        .withDetail("service", properties.getServiceName())
        .withDetail("startedAt", startedAt.toString())
        .withDetail("observabilityEnabled", properties.isEnabled())
        .build();
  }
}
