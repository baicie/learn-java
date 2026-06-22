package io.aegisops.demo.order;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FaultModeService {

  private static final Logger log = LoggerFactory.getLogger(FaultModeService.class);

  private final AtomicBoolean slowApiEnabled = new AtomicBoolean(false);
  private final AtomicLong slowApiDelayMs = new AtomicLong(0);
  private final AtomicBoolean cpuHighEnabled = new AtomicBoolean(false);
  private final AtomicInteger cpuWorkers = new AtomicInteger(0);
  private final AtomicBoolean healthy = new AtomicBoolean(true);
  private final AtomicBoolean errorLogEnabled = new AtomicBoolean(false);
  private final AtomicLong errorCount = new AtomicLong(0);

  private final ExecutorService cpuExecutor = Executors.newCachedThreadPool();
  private final List<CompletableFuture<Void>> cpuTasks = new ArrayList<>();

  public synchronized FaultStateResponse apply(FaultMode mode) {
    if (mode == null) {
      return state();
    }

    if (mode.slowApiEnabled() != null) {
      slowApiEnabled.set(mode.slowApiEnabled());
    }

    if (mode.slowApiDelayMs() != null) {
      slowApiDelayMs.set(Math.max(0, mode.slowApiDelayMs()));
    }

    if (mode.healthy() != null) {
      healthy.set(mode.healthy());
    }

    if (mode.errorLogEnabled() != null) {
      errorLogEnabled.set(mode.errorLogEnabled());
      if (mode.errorLogEnabled()) {
        incrementErrorCount(5);
      }
    }

    if (mode.cpuHighEnabled() != null) {
      int workers = mode.cpuWorkers() == null ? 2 : Math.max(0, mode.cpuWorkers());
      if (mode.cpuHighEnabled()) {
        enableCpuHigh(workers);
      } else {
        disableCpuHigh();
      }
    }

    return state();
  }

  public FaultStateResponse state() {
    return new FaultStateResponse(
        slowApiEnabled.get(),
        slowApiDelayMs.get(),
        cpuHighEnabled.get(),
        cpuWorkers.get(),
        healthy.get(),
        errorLogEnabled.get(),
        errorCount.get(),
        simulatedCpuUtil(),
        simulatedMemoryUtil(),
        simulatedLoadAvg(),
        simulatedOrderLatencySeconds());
  }

  public void sleepIfSlowApiEnabled() {
    if (!slowApiEnabled.get()) {
      return;
    }

    long delayMs = slowApiDelayMs.get();
    if (delayMs <= 0) {
      return;
    }

    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  public boolean isHealthy() {
    return healthy.get();
  }

  public boolean isErrorLogEnabled() {
    return errorLogEnabled.get();
  }

  public long incrementErrorCount(long amount) {
    return errorCount.addAndGet(Math.max(0, amount));
  }

  public long errorCount() {
    return errorCount.get();
  }

  public double simulatedCpuUtil() {
    return cpuHighEnabled.get() ? 95.0 : 15.0;
  }

  public double simulatedMemoryUtil() {
    return cpuHighEnabled.get() ? 72.0 : 38.0;
  }

  public double simulatedLoadAvg() {
    return cpuHighEnabled.get() ? 5.8 : 0.4;
  }

  public double simulatedOrderLatencySeconds() {
    if (!slowApiEnabled.get()) {
      return 0.12;
    }

    return Math.max(0.0, slowApiDelayMs.get() / 1000.0);
  }

  public synchronized void reset() {
    slowApiEnabled.set(false);
    slowApiDelayMs.set(0);
    healthy.set(true);
    errorLogEnabled.set(false);
    errorCount.set(0);
    disableCpuHigh();
  }

  private synchronized void enableCpuHigh(int workers) {
    disableCpuHigh();

    int safeWorkers = workers <= 0 ? 1 : workers;
    cpuHighEnabled.set(true);
    cpuWorkers.set(safeWorkers);

    for (int i = 0; i < safeWorkers; i++) {
      cpuTasks.add(CompletableFuture.runAsync(this::burnCpu, cpuExecutor));
    }

    log.warn("AegisOps demo fault enabled: CPU high, workers={}", safeWorkers);
  }

  private synchronized void disableCpuHigh() {
    cpuHighEnabled.set(false);
    cpuWorkers.set(0);
    cpuTasks.clear();
  }

  private void burnCpu() {
    long value = 0;
    while (cpuHighEnabled.get() && !Thread.currentThread().isInterrupted()) {
      value += System.nanoTime() % 97;
      if (value > 1_000_000_000L) {
        value = 0;
      }
    }
  }

  @PreDestroy
  public void shutdown() {
    disableCpuHigh();
    cpuExecutor.shutdownNow();
  }
}
