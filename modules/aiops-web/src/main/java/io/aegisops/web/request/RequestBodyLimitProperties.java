package io.aegisops.web.request;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.web.request")
public class RequestBodyLimitProperties {

  private long maxBodyBytes = 2L * 1024L * 1024L;

  public long getMaxBodyBytes() {
    return maxBodyBytes;
  }

  public void setMaxBodyBytes(long maxBodyBytes) {
    if (maxBodyBytes < 1024) {
      throw new IllegalArgumentException("maxBodyBytes must be at least 1024");
    }
    this.maxBodyBytes = maxBodyBytes;
  }
}
