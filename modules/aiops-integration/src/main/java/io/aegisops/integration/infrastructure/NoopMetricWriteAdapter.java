package io.aegisops.integration.infrastructure;

import io.aegisops.otel.application.MetricWritePort;
import io.aegisops.otel.domain.model.OtelSignal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "aiops.evidence.victoria",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class NoopMetricWriteAdapter implements MetricWritePort {
  @Override
  public void write(String tenantId, String assetId, OtelSignal signal) {
    // PostgreSQL keeps the bounded evidence index when VictoriaMetrics is disabled.
  }
}
