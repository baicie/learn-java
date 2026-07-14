package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiopsQuotaPropertiesTest {

  @Test
  void defaultBackendMustBeMemory() {
    AiopsQuotaProperties properties = new AiopsQuotaProperties();
    assertThat(properties.getBackend()).isEqualTo(AiopsQuotaProperties.RateLimitBackend.MEMORY);
  }

  @Test
  void defaultAnonymousRequestsPerMinuteIsPositive() {
    AiopsQuotaProperties properties = new AiopsQuotaProperties();
    assertThat(properties.getAnonymousRequestsPerMinute()).isPositive();
  }

  @Test
  void publicAndInternalLimitsMustRemainPositive() {
    AiopsQuotaProperties properties = new AiopsQuotaProperties();
    assertThat(properties.getPublicApiRequestsPerMinute()).isPositive();
    assertThat(properties.getInternalAgentRequestsPerMinute()).isPositive();
  }

  @Test
  void rateLimitDefaultsToEnabled() {
    AiopsQuotaProperties properties = new AiopsQuotaProperties();
    assertThat(properties.isRateLimitEnabled()).isTrue();
  }

  @Test
  void redisBackendValueIsAccepted() {
    AiopsQuotaProperties properties = new AiopsQuotaProperties();
    properties.setBackend(AiopsQuotaProperties.RateLimitBackend.REDIS);
    assertThat(properties.getBackend()).isEqualTo(AiopsQuotaProperties.RateLimitBackend.REDIS);
  }
}
