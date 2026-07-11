package io.aegisops.workrecord.infrastructure.metrics;

import io.aegisops.workrecord.application.port.WorkRecordTelemetry;
import io.aegisops.workrecord.application.service.WorkRecordProductionProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MicrometerWorkRecordTelemetry implements WorkRecordTelemetry {

  private static final Logger log =
      LoggerFactory.getLogger(MicrometerWorkRecordTelemetry.class);

  private final MeterRegistry registry;
  private final WorkRecordProductionProperties properties;

  private final Map<String, Timer> queryTimers = new ConcurrentHashMap<>();

  private final Map<String, Counter> counters = new ConcurrentHashMap<>();

  public MicrometerWorkRecordTelemetry(
      MeterRegistry registry, WorkRecordProductionProperties properties) {
    this.registry = registry;
    this.properties = properties;
  }

  @Override
  public void recordQuery(String operation, Duration duration) {
    queryTimer(operation).record(duration);

    long threshold = properties.getQuery().getSlowThresholdMillis();

    if (duration.toMillis() >= threshold) {
      counter("aegisops_work_record_slow_queries_total", "operation", operation).increment();

      log.warn(
          "slow work-record query: operation={}, durationMs={}, thresholdMs={}",
          operation,
          duration.toMillis(),
          threshold);
    }
  }

  @Override
  public void recordExport(String result) {
    counter("aegisops_work_record_exports_total", "result", result).increment();
  }

  @Override
  public void recordPermissionDenied(String action) {
    counter("aegisops_work_record_permission_denied_total", "action", action).increment();
  }

  private Timer queryTimer(String operation) {
    return queryTimers.computeIfAbsent(
        operation,
        ignored ->
            Timer.builder("aegisops_work_record_query_duration")
                .description("Work-record database query duration")
                .tag("operation", operation)
                .publishPercentileHistogram()
                .register(registry));
  }

  private Counter counter(String name, String tagKey, String tagValue) {
    String key = name + "|" + tagKey + "|" + tagValue;

    return counters.computeIfAbsent(
        key,
        ignored -> Counter.builder(name).tag(tagKey, tagValue).register(registry));
  }
}