package io.aegisops.workrecord.infrastructure.job;

import io.aegisops.workrecord.application.service.WorkRecordLifecycleCoordinator;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "aiops.runtime", name = "app", havingValue = "app")
public class SlaBreachScheduler {
  private final WorkRecordLifecycleCoordinator lifecycle;
  private final Clock clock;

  public SlaBreachScheduler(
      WorkRecordLifecycleCoordinator lifecycle, @Qualifier("workRecordClock") Clock clock) {
    this.lifecycle = lifecycle;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${aiops.work-record.sla-scan-ms:60000}")
  public void scan() {
    lifecycle.markBreached(OffsetDateTime.now(clock), 500);
  }
}
