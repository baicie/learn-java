package io.aegisops.worker.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.aegisops.worker.job.ZabbixSyncScheduleJob;
import io.aegisops.worker.job.ZabbixSyncScheduleProperties;
import io.aegisops.worker.outbox.OutboxPoller;
import io.aegisops.worker.outbox.OutboxProperties;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class WorkerRuntimeTopologyTest {
  @Test
  void schedulerRunsAnotherTaskWhileOneWorkerTaskIsBlocked() throws Exception {
    WorkerRuntimeTopology topology = new WorkerRuntimeTopology();
    ThreadPoolTaskScheduler scheduler = (ThreadPoolTaskScheduler) topology.workerOutboxScheduler();
    CountDownLatch blockingTaskStarted = new CountDownLatch(1);
    CountDownLatch releaseBlockingTask = new CountDownLatch(1);
    CountDownLatch secondTaskRan = new CountDownLatch(1);

    try {
      scheduler.execute(
          () -> {
            blockingTaskStarted.countDown();
            try {
              releaseBlockingTask.await();
            } catch (InterruptedException exception) {
              Thread.currentThread().interrupt();
            }
          });
      assertTrue(blockingTaskStarted.await(1, TimeUnit.SECONDS));

      scheduler.execute(secondTaskRan::countDown);

      assertTrue(secondTaskRan.await(1, TimeUnit.SECONDS));
    } finally {
      releaseBlockingTask.countDown();
      scheduler.destroy();
    }
  }

  @Test
  void startsBothTicksImmediatelyAndSchedulesTheirIndependentIntervals() throws Exception {
    OutboxPoller outboxPoller = mock(OutboxPoller.class);
    ZabbixSyncScheduleJob zabbixScheduleJob = mock(ZabbixSyncScheduleJob.class);
    TaskScheduler scheduler = mock(TaskScheduler.class);
    WorkerRuntimeTopology topology = new WorkerRuntimeTopology();

    ApplicationRunner runner =
        topology.workerRuntimeBootstrap(
            outboxPoller,
            new OutboxProperties(true, 1000L, 10, "worker", 60000L),
            zabbixScheduleJob,
            new ZabbixSyncScheduleProperties(true, 5000L, 60000L, 25),
            scheduler);
    runner.run(null);

    verify(zabbixScheduleJob).tick();
    verify(outboxPoller).tick();
    verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofSeconds(1)));
    verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(5)));
  }
}
