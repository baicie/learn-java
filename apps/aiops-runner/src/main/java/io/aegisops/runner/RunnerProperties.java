package io.aegisops.runner;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.runner")
public class RunnerProperties {
  private boolean enabled = true;
  private String runnerId = "local-runner";
  private long pollDelayMs = 5000;
  private long timeoutSweepDelayMs = 10000;
  private int maxRunsPerTick = 1;
  private int timeoutSweepLimit = 20;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getRunnerId() {
    return runnerId;
  }

  public void setRunnerId(String runnerId) {
    this.runnerId = runnerId;
  }

  public long getPollDelayMs() {
    return pollDelayMs;
  }

  public void setPollDelayMs(long pollDelayMs) {
    this.pollDelayMs = pollDelayMs;
  }

  public long getTimeoutSweepDelayMs() {
    return timeoutSweepDelayMs;
  }

  public void setTimeoutSweepDelayMs(long timeoutSweepDelayMs) {
    this.timeoutSweepDelayMs = timeoutSweepDelayMs;
  }

  public int getMaxRunsPerTick() {
    return maxRunsPerTick;
  }

  public void setMaxRunsPerTick(int maxRunsPerTick) {
    this.maxRunsPerTick = maxRunsPerTick;
  }

  public int getTimeoutSweepLimit() {
    return timeoutSweepLimit;
  }

  public void setTimeoutSweepLimit(int timeoutSweepLimit) {
    this.timeoutSweepLimit = timeoutSweepLimit;
  }
}
