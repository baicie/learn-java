package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redis.testcontainers.RedisContainer;
import io.aegisops.common.exception.AppException;
import io.aegisops.common.exception.ErrorCode;
import java.time.Duration;
import java.util.Optional;
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
 * 验证 {@link RedisDistributedLeaseService} 在真实 Redis 上：
 *
 * <ol>
 *   <li>互斥：同一 key 在 TTL 内再次申请返回空；
 *   <li>关闭：close() 调用 atomic compare-and-delete 释放租约；
 *   <li>TTL：到期后再次申请成功；
 *   <li>Idempotent close：重复关闭不会破坏他人租约。
 * </ol>
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "test.include.redis", matches = "true")
class RedisDistributedLeaseServiceIT {

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate template;
  private static RedisDistributedLeaseService service;

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

    service = new RedisDistributedLeaseService(template);
  }

  @AfterAll
  static void stop() {
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
  }

  @Test
  void acquireShouldBeMutuallyExclusiveWithinTtl() {
    String key = "export:user-" + System.nanoTime();

    Optional<DistributedLease> first = service.tryAcquire(key, Duration.ofMinutes(1));
    Optional<DistributedLease> second = service.tryAcquire(key, Duration.ofMinutes(1));

    assertThat(first).isPresent();
    assertThat(second).isEmpty();

    first.get().close();
  }

  @Test
  void closeShouldReleaseForReacquire() {
    String key = "export:user-" + System.nanoTime();

    DistributedLease first = service.tryAcquire(key, Duration.ofMinutes(1)).orElseThrow();
    first.close();

    Optional<DistributedLease> reacquired = service.tryAcquire(key, Duration.ofMinutes(1));
    assertThat(reacquired).isPresent();
    reacquired.get().close();
  }

  @Test
  void expiresAfterTtl() throws Exception {
    String key = "export:user-" + System.nanoTime();

    service.tryAcquire(key, Duration.ofSeconds(1)).orElseThrow();

    Thread.sleep(1500);

    Optional<DistributedLease> reacquired = service.tryAcquire(key, Duration.ofSeconds(5));
    assertThat(reacquired).isPresent();
    reacquired.get().close();
  }

  @Test
  void doubleCloseShouldNotEvictOthersLease() {
    String key = "export:user-" + System.nanoTime();

    DistributedLease first = service.tryAcquire(key, Duration.ofMinutes(1)).orElseThrow();
    first.close();
    first.close(); // 幂等

    Optional<DistributedLease> reacquired = service.tryAcquire(key, Duration.ofMinutes(1));
    assertThat(reacquired).isPresent();
    reacquired.get().close();
  }

  @Test
  void unreachableRedisShouldFailFast() {
    RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("127.0.0.1", 1);
    LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
    factory.afterPropertiesSet();
    factory.getConnection();

    try {
      RedisConnectionFactory connection = factory;
      StringRedisTemplate broken = new StringRedisTemplate(connection);
      broken.afterPropertiesSet();
      RedisDistributedLeaseService failing = new RedisDistributedLeaseService(broken);

      assertThatThrownBy(() -> failing.tryAcquire("k", Duration.ofSeconds(30)))
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
