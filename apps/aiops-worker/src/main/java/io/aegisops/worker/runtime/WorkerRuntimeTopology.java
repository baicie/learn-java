package io.aegisops.worker.runtime;

import io.aegisops.worker.job.ZabbixSyncScheduleJob;
import io.aegisops.worker.job.ZabbixSyncScheduleProperties;
import io.aegisops.worker.outbox.OutboxPoller;
import io.aegisops.worker.outbox.OutboxProperties;
import java.time.Duration;
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
    scheduler.setPoolSize(2);
    scheduler.setThreadNamePrefix("worker-outbox-");
    scheduler.setDaemon(true);
    scheduler.initialize();
    return scheduler;
  }

  @Bean
  public ApplicationRunner workerRuntimeBootstrap(
      OutboxPoller outboxPoller,
      OutboxProperties outboxProperties,
      ZabbixSyncScheduleJob zabbixSyncScheduleJob,
      ZabbixSyncScheduleProperties zabbixSyncProperties,
      TaskScheduler scheduler) {
    return args -> {
      if (outboxProperties.enabled()) {
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
            Duration.ofMillis(outboxProperties.pollDelayMs()));
      } else {
        LOGGER.info("Outbox poller disabled by aiops.outbox.enabled=false; skipping bootstrap");
      }

      if (zabbixSyncProperties.enabled()) {
        LOGGER.info(
            "Zabbix sync dispatcher scheduled: delayMs={} cadenceMs={} batch={}",
            zabbixSyncProperties.pollDelayMs(),
            zabbixSyncProperties.cadenceMs(),
            zabbixSyncProperties.batchSize());
        scheduler.scheduleWithFixedDelay(
            () -> {
              try {
                int queued = zabbixSyncScheduleJob.tick();
                if (queued > 0) {
                  LOGGER.debug("Zabbix sync dispatcher queued {} rows", queued);
                }
              } catch (RuntimeException ex) {
                LOGGER.warn("Zabbix sync dispatcher tick failed", ex);
              }
            },
            Duration.ofMillis(zabbixSyncProperties.pollDelayMs()));
        try {
          zabbixSyncScheduleJob.tick();
        } catch (RuntimeException ex) {
          LOGGER.warn("Initial Zabbix sync dispatcher tick failed", ex);
        }
      } else {
        LOGGER.info("Zabbix sync dispatcher disabled; skipping bootstrap");
      }

      if (outboxProperties.enabled()) {
        try {
          outboxPoller.tick();
        } catch (RuntimeException ex) {
          LOGGER.warn("Initial outbox tick failed", ex);
        }
      }
    };
  }

  /** Visible for tests that need to enumerate registered jobs. */
  public static List<String> registeredJobs(OutboxPoller poller) {
    return List.copyOf(poller.jobsByName().keySet());
  }
}
