package io.aegisops.security;

import io.aegisops.common.exception.AppException;
import io.aegisops.common.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public class RedisDistributedLeaseService implements DistributedLeaseService {

  private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
      new DefaultRedisScript<>(
          """
          if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
          end

          return 0
          """,
          Long.class);

  private final StringRedisTemplate redis;

  public RedisDistributedLeaseService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public Optional<DistributedLease> tryAcquire(String key, Duration ttl) {
    String redisKey = "aegisops:lease:" + key;
    String token = UUID.randomUUID().toString();

    try {
      Boolean acquired = redis.opsForValue().setIfAbsent(redisKey, token, ttl);

      if (!Boolean.TRUE.equals(acquired)) {
        return Optional.empty();
      }

      return Optional.of(new RedisLease(redisKey, token));
    } catch (DataAccessException ex) {
      throw new AppException(
          ErrorCode.RATE_LIMIT_BACKEND_UNAVAILABLE, "distributed lease backend is unavailable", ex);
    }
  }

  private final class RedisLease implements DistributedLease {

    private final String key;
    private final String token;
    private final AtomicBoolean closed = new AtomicBoolean();

    private RedisLease(String key, String token) {
      this.key = key;
      this.token = token;
    }

    @Override
    public String key() {
      return key;
    }

    @Override
    public void close() {
      if (!closed.compareAndSet(false, true)) {
        return;
      }

      try {
        redis.execute(RELEASE_SCRIPT, List.of(key), token);
      } catch (DataAccessException ignored) {
        // TTL 会保证异常时租约最终释放。
      }
    }
  }
}
