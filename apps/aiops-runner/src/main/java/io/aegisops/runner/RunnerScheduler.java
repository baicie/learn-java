package io.aegisops.runner;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "aiops.runner",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RunnerScheduler {
  private final RunnerProperties properties;
  private final RunnerExecutionService service;

  public RunnerScheduler(RunnerProperties properties, RunnerExecutionService service) {
    this.properties = properties;
    this.service = service;
  }

  @Scheduled(fixedDelayString = "${aiops.runner.poll-delay-ms:5000}")
  public void poll() {
    int max = Math.max(1, properties.getMaxRunsPerTick());
    for (int i = 0; i < max; i++) {
      boolean processed = service.processNext();
      if (!processed) {
        return;
      }
    }
  }
}
