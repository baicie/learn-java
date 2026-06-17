package io.aegisops.execution;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.execution")
public class ExecutionProperties {
  /** Public execution API on server. Runner should set this to false. */
  private boolean apiEnabled = true;

  /** Live execution is disabled by default. */
  private boolean liveEnabled = false;

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
}
