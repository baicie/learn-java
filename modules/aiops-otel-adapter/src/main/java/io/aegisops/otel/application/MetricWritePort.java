package io.aegisops.otel.application;

import io.aegisops.otel.domain.model.OtelSignal;

public interface MetricWritePort {
  void write(String tenantId, String assetId, OtelSignal signal);
}
