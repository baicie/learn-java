package io.aegisops.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AiopsSecurityProperties.class, AiopsQuotaProperties.class})
public class RateLimitConfiguration {

  /**
   * 多副本部署时使用 Redis 作为限流后端；单机部署时退回为内存限流。
   * 通过 aiops.quota.backend 配置项切换，默认 redis。
   */
  @Bean
  public RateLimitService rateLimitService(
      AiopsQuotaProperties quotaProperties,
      org.springframework.beans.factory.ObjectProvider<
              org.springframework.data.redis.core.StringRedisTemplate>
          redisProvider) {
    String backend = backendOrDefault(quotaProperties);
    if ("redis".equalsIgnoreCase(backend) && redisProvider.getIfAvailable() != null) {
      return new RedisRateLimitService(redisProvider.getIfAvailable());
    }
    return new InMemoryTenantRateLimiter();
  }

  @Bean
  public DistributedLeaseService distributedLeaseService(
      AiopsQuotaProperties quotaProperties,
      org.springframework.beans.factory.ObjectProvider<
              org.springframework.data.redis.core.StringRedisTemplate>
          redisProvider) {
    String backend = backendOrDefault(quotaProperties);
    if ("redis".equalsIgnoreCase(backend) && redisProvider.getIfAvailable() != null) {
      return new RedisDistributedLeaseService(redisProvider.getIfAvailable());
    }
    return new InMemoryDistributedLeaseService();
  }

  private String backendOrDefault(AiopsQuotaProperties quotaProperties) {
    return quotaProperties.getBackend() == null
        ? "redis"
        : quotaProperties.getBackend();
  }
}