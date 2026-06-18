package io.aegisops.execution;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.execution")
public class ExecutionProperties {
  private boolean apiEnabled = true;
  private boolean liveEnabled = false;
  private int leaseSeconds = 60;
  private int runTimeoutSeconds = 1800;
  private int stepTimeoutSeconds = 300;
  private int maxRetryAttempts = 3;

  public boolean isApiEnabled() {
    return apiEnabled;
  }

  public void setApiEnabled(boolean apiEnabled) {
    this.apiEnabled = apiEnabled;
  }

  public boolean isLiveEnabled() {
    return liveEnabled;
  }

  public void setLiveEnabled(boolean liveEnabled) {
    this.liveEnabled = liveEnabled;
  }

  public int getLeaseSeconds() {
    return leaseSeconds;
  }

  public void setLeaseSeconds(int leaseSeconds) {
    this.leaseSeconds = leaseSeconds;
  }

  public int getRunTimeoutSeconds() {
    return runTimeoutSeconds;
  }

  public void setRunTimeoutSeconds(int runTimeoutSeconds) {
    this.runTimeoutSeconds = runTimeoutSeconds;
  }

  public int getStepTimeoutSeconds() {
    return stepTimeoutSeconds;
  }

  public void setStepTimeoutSeconds(int stepTimeoutSeconds) {
    this.stepTimeoutSeconds = stepTimeoutSeconds;
  }

  public int getMaxRetryAttempts() {
    return maxRetryAttempts;
  }

  public void setMaxRetryAttempts(int maxRetryAttempts) {
    this.maxRetryAttempts = maxRetryAttempts;
  }

  public int normalizedLeaseSeconds() {
    return Math.max(10, Math.min(leaseSeconds, 3600));
  }

  public int normalizedRunTimeoutSeconds() {
    return Math.max(30, Math.min(runTimeoutSeconds, 86400));
  }

  public int normalizedStepTimeoutSeconds() {
    return Math.max(1, Math.min(stepTimeoutSeconds, 86400));
  }

  public int normalizedMaxRetryAttempts() {
    return Math.max(1, Math.min(maxRetryAttempts, 5));
  }
}
