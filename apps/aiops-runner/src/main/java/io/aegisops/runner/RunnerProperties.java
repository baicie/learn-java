package io.aegisops.runner;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.runner")
public class RunnerProperties {
  private boolean enabled = true;
  private String runnerId = "local-runner";
  private long pollDelayMs = 5000;
  private int maxRunsPerTick = 1;

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

  public int getMaxRunsPerTick() {
    return maxRunsPerTick;
  }

  public void setMaxRunsPerTick(int maxRunsPerTick) {
    this.maxRunsPerTick = maxRunsPerTick;
  }
}
