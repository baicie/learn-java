package io.aegisops.integration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.otel.domain.model.OtelSignal;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VictoriaMetricsWriteAdapterTest {
  @Test
  void formatsTenantScopedPrometheusImportLine() {
    var signal =
        new OtelSignal(
            "metric",
            "metric-1",
            "checkout",
            "instance-1",
            null,
            "prod",
            null,
            null,
            null,
            null,
            "http.server.duration",
            new BigDecimal("12.5"),
            OffsetDateTime.parse("2026-07-17T10:00:00Z"),
            Map.of());

    assertThat(VictoriaMetricsWriteAdapter.prometheusLine("tenant-a", "asset-a", signal))
        .isEqualTo(
            "http_server_duration{tenant_id=\"tenant-a\",asset_id=\"asset-a\",service_name=\"checkout\"} 12.5 1784282400000\n");
  }
}
