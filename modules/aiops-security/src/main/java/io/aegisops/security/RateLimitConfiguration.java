package io.aegisops.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 限流与分布式租约 Bean 装配。
 *
 * <p>使用 {@link ConditionalOnProperty} 避免 Redis 配置错误时静默回退到内存实现。
 *
 * <p>约定：
 *
 * <ul>
 *   <li>{@code aiops.quota.backend=memory}（默认）：装配内存限流与内存租约。
 *   <li>{@code aiops.quota.backend=redis}：装配 Redis 限流与 Redis 租约；
 *       如果 Redis 自动配置不可用或缺少 {@link StringRedisTemplate} Bean，
 *       Spring 上下文会以 NoSuchBeanDefinitionException 失败，
 *       阻止应用以错误的限流配置启动。
 * </ul>
 */
@Configuration
@EnableConfigurationProperties({AiopsSecurityProperties.class, AiopsQuotaProperties.class})
public class RateLimitConfiguration {

  @Bean
  @ConditionalOnProperty(
      prefix = "aiops.quota",
      name = "backend",
      havingValue = "redis")
  public RateLimitService redisRateLimitService(StringRedisTemplate redis) {
    return new RedisRateLimitService(redis);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "aiops.quota",
      name = "backend",
      havingValue = "redis")
  public DistributedLeaseService redisDistributedLeaseService(StringRedisTemplate redis) {
    return new RedisDistributedLeaseService(redis);
  }

  @Bean
  @ConditionalOnMissingBean(RateLimitService.class)
  @ConditionalOnProperty(
      prefix = "aiops.quota",
      name = "backend",
      havingValue = "memory",
      matchIfMissing = true)
  public RateLimitService memoryRateLimitService() {
    return new InMemoryTenantRateLimiter();
  }

  @Bean
  @ConditionalOnMissingBean(DistributedLeaseService.class)
  @ConditionalOnProperty(
      prefix = "aiops.quota",
      name = "backend",
      havingValue = "memory",
      matchIfMissing = true)
  public DistributedLeaseService memoryDistributedLeaseService() {
    return new InMemoryDistributedLeaseService();
  }
}
