package io.aegisops.workrecord.infrastructure.job;

import io.aegisops.workrecord.application.service.MissingDailyReminderService;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "aiops.runtime", name = "app", havingValue = "app")
public class MissingDailyReminderScheduler {
  private final MissingDailyReminderService service;
  private final Clock clock;

  public MissingDailyReminderScheduler(
      MissingDailyReminderService service, @Qualifier("workRecordClock") Clock clock) {
    this.service = service;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${aiops.work-record.reminder-scan-ms:300000}")
  public void scan() {
    service.scanDueRules(clock.instant());
  }
}
