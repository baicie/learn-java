package io.aegisops.worker.job;

import io.aegisops.common.time.TimeProvider;
import io.aegisops.datasource.application.ZabbixSyncDispatchApplicationService;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class ZabbixSyncScheduleJob {
  private final ZabbixSyncDispatchApplicationService dispatchService;
  private final TimeProvider timeProvider;
  private final ZabbixSyncScheduleProperties properties;

  public ZabbixSyncScheduleJob(
      ZabbixSyncDispatchApplicationService dispatchService,
      TimeProvider timeProvider,
      ZabbixSyncScheduleProperties properties) {
    this.dispatchService = dispatchService;
    this.timeProvider = timeProvider;
    this.properties = properties;
  }

  public int tick() {
    if (!properties.enabled()) {
      return 0;
    }
    return dispatchService.dispatchDue(
        timeProvider.now(), Duration.ofMillis(properties.cadenceMs()), properties.batchSize());
  }
}
