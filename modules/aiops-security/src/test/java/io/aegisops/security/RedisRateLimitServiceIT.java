package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redis.testcontainers.RedisContainer;
import io.aegisops.common.exception.AppException;
import io.aegisops.common.exception.ErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 验证 {@link RedisRateLimitService} 在真实 Redis 上：
 *
 * <ol>
 *   <li>首次调用计数从 1 开始；
 *   <li>超限后拒绝并提供正 retry-after；
 *   <li>窗口到期后窗口内计数器被自动回收；
 *   <li>连接失败抛 {@link AppException}，错误码 {@code RATE_LIMIT_BACKEND_UNAVAILABLE}。
 * </ol>
 *
 * <p>需要 Docker，并通过系统属性 {@code test.include.redis=true} 显式启用，避免在 sandbox 中拖慢构建。
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "test.include.redis", matches = "true")
class RedisRateLimitServiceIT {

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate template;
  private static RedisRateLimitService service;

  @Container static final RedisContainer REDIS = new RedisContainer("redis:7-alpine");

  @BeforeAll
  static void start() {
    REDIS.start();

    RedisStandaloneConfiguration config =
        new RedisStandaloneConfiguration(requireHost(REDIS), requirePort(REDIS));

    connectionFactory = new LettuceConnectionFactory(config);
    connectionFactory.afterPropertiesSet();

    @SuppressWarnings("null")
    RedisConnectionFactory connection = connectionFactory;
    template = new StringRedisTemplate(connection);
    template.afterPropertiesSet();

    service = new RedisRateLimitService(template);
  }

  @AfterAll
  static void stop() {
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
  }

  @Test
  void firstCallShouldBeAllowedAndRemainingEqualLimit() {
    String key = "tenant:t1:public:" + System.nanoTime();

    RateLimitDecision decision = service.acquire(key, 5, Duration.ofMinutes(1));

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.remaining()).isEqualTo(4L);
  }

  @Test
  void exceedingLimitShouldRejectWithPositiveRetryAfter() {
    String key = "tenant:t2:public:" + System.nanoTime();

    for (int i = 0; i < 3; i++) {
      service.acquire(key, 3, Duration.ofMinutes(1));
    }

    RateLimitDecision rejected = service.acquire(key, 3, Duration.ofMinutes(1));

    assertThat(rejected.allowed()).isFalse();
    assertThat(rejected.retryAfterSeconds()).isPositive();
  }

  @Test
  void windowExpiryShouldResetCounter() throws Exception {
    String key = "tenant:t3:public:" + System.nanoTime();

    service.acquire(key, 1, Duration.ofSeconds(1));

    RateLimitDecision blocked = service.acquire(key, 1, Duration.ofSeconds(1));
    assertThat(blocked.allowed()).isFalse();

    Thread.sleep(1500);

    RateLimitDecision fresh = service.acquire(key, 1, Duration.ofSeconds(1));
    assertThat(fresh.allowed()).isTrue();
  }

  @Test
  void unreachableRedisShouldFailFastWithBackendUnavailableError() {
    RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("127.0.0.1", 1);
    LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
    factory.afterPropertiesSet();
    factory.getConnection();

    try {
      RedisConnectionFactory redisConnectionFactory = factory;
      StringRedisTemplate broken = new StringRedisTemplate(redisConnectionFactory);
      broken.afterPropertiesSet();
      RedisRateLimitService failing = new RedisRateLimitService(broken);

      assertThatThrownBy(() -> failing.acquire("k", 1, Duration.ofSeconds(30)))
          .isInstanceOf(AppException.class)
          .satisfies(
              throwable ->
                  assertThat(((AppException) throwable).errorCode())
                      .isEqualTo(ErrorCode.RATE_LIMIT_BACKEND_UNAVAILABLE.name()));
    } finally {
      factory.destroy();
    }
  }

  private static String requireHost(RedisContainer redis) {
    String host = redis.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalStateException("redis container host not available");
    }
    return host;
  }

  private static int requirePort(RedisContainer redis) {
    int port = redis.getFirstMappedPort();
    if (port < 0) {
      throw new IllegalStateException("redis container port not available");
    }
    return port;
  }
}
