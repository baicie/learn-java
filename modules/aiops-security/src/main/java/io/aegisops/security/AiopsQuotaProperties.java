package io.aegisops.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 限流与配额相关配置。
 *
 * <p>生产 Profile 必须显式将 {@code backend} 设为 {@link RateLimitBackend#REDIS}， 否则会因为缺少 {@code
 * StringRedisTemplate} Bean 导致启动失败（fail-fast）。
 *
 * <p>本地或测试场景默认使用 {@link RateLimitBackend#MEMORY}，无需任何额外依赖即可启动。
 */
@Validated
@ConfigurationProperties(prefix = "aiops.quota")
public class AiopsQuotaProperties {

  @Min(1)
  private int publicApiRequestsPerMinute = 600;

  @Min(1)
  private int internalAgentRequestsPerMinute = 1200;

  @Min(1)
  private int anonymousRequestsPerMinute = 30;

  private boolean rateLimitEnabled = true;

  @NotNull private RateLimitBackend backend = RateLimitBackend.MEMORY;

  public int getPublicApiRequestsPerMinute() {
    return publicApiRequestsPerMinute;
  }

  public void setPublicApiRequestsPerMinute(int value) {
    this.publicApiRequestsPerMinute = value;
  }

  public int getInternalAgentRequestsPerMinute() {
    return internalAgentRequestsPerMinute;
  }

  public void setInternalAgentRequestsPerMinute(int value) {
    this.internalAgentRequestsPerMinute = value;
  }

  public int getAnonymousRequestsPerMinute() {
    return anonymousRequestsPerMinute;
  }

  public void setAnonymousRequestsPerMinute(int value) {
    this.anonymousRequestsPerMinute = value;
  }

  public boolean isRateLimitEnabled() {
    return rateLimitEnabled;
  }

  public void setRateLimitEnabled(boolean value) {
    this.rateLimitEnabled = value;
  }

  public RateLimitBackend getBackend() {
    return backend;
  }

  public void setBackend(RateLimitBackend backend) {
    this.backend = backend;
  }

  public enum RateLimitBackend {
    MEMORY,
    REDIS
  }
}
