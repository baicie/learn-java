package io.aegisops.worker.runtime;

import io.aegisops.worker.outbox.OutboxPoller;
import io.aegisops.worker.outbox.OutboxProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Declares the worker-process scheduling topology: a {@link ThreadPoolTaskScheduler} that drives
 * the {@link OutboxPoller#tick()} loop on the configured {@link OutboxProperties#pollDelayMs()}
 * interval.
 *
 * <p>The {@code ApplicationRunner} triggers one immediate tick at startup so freshly enqueued
 * outbox rows do not have to wait for the first scheduled fire. Subsequent ticks come from the
 * scheduler.
 */
@Configuration
@EnableScheduling
public class WorkerRuntimeTopology {

  private static final Logger LOGGER = LoggerFactory.getLogger(WorkerRuntimeTopology.class);

  @Bean
  public TaskScheduler workerOutboxScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("worker-outbox-");
    scheduler.setDaemon(true);
    scheduler.initialize();
    return scheduler;
  }

  @Bean
  public ApplicationRunner workerOutboxBootstrap(
      OutboxPoller outboxPoller, OutboxProperties outboxProperties, TaskScheduler scheduler) {
    return args -> {
      if (!outboxProperties.enabled()) {
        LOGGER.info("Outbox poller disabled by aiops.outbox.enabled=false; skipping bootstrap");
        return;
      }
      LOGGER.info(
          "Worker outbox poller scheduled: targetApp={} delayMs={} batch={}",
          outboxProperties.targetApp(),
          outboxProperties.pollDelayMs(),
          outboxProperties.batchSize());
      scheduler.scheduleAtFixedRate(
          () -> {
            try {
              int done = outboxPoller.tick();
              if (done > 0) {
                LOGGER.debug("Outbox tick completed {} rows", done);
              }
            } catch (RuntimeException ex) {
              LOGGER.warn("Outbox tick failed", ex);
            }
          },
          outboxProperties.pollDelayMs());
      // fire one tick right away so the very first batch lands on startup
      try {
        outboxPoller.tick();
      } catch (RuntimeException ex) {
        LOGGER.warn("Initial outbox tick failed", ex);
      }
    };
  }

  /** Visible for tests that need to enumerate registered jobs. */
  public static List<String> registeredJobs(OutboxPoller poller) {
    return List.copyOf(poller.jobsByName().keySet());
  }
}
