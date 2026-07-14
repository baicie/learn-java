package io.aegisops.security;

import io.aegisops.common.exception.AppException;
import io.aegisops.common.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public class RedisRateLimitService implements RateLimitService {

  private static final DefaultRedisScript<List> SCRIPT =
      new DefaultRedisScript<>(
          """
          local current = redis.call('INCR', KEYS[1])

          if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
          end

          local ttl = redis.call('PTTL', KEYS[1])

          return { current, ttl }
          """,
          List.class);

  private final StringRedisTemplate redis;

  public RedisRateLimitService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public RateLimitDecision acquire(String key, int limit, Duration window) {
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be positive");
    }

    if (window == null || window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("window must be positive");
    }

    try {
      List<?> result =
          redis.execute(SCRIPT, List.of(namespaced(key)), String.valueOf(window.toMillis()));

      if (result == null || result.size() < 2) {
        throw new AppException(
            ErrorCode.RATE_LIMIT_BACKEND_UNAVAILABLE, "rate limit backend returned no result");
      }

      long current = ((Number) result.get(0)).longValue();
      long ttlMillis = ((Number) result.get(1)).longValue();

      long retryAfter = Math.max(1, (ttlMillis + 999) / 1000);

      if (current > limit) {
        return RateLimitDecision.rejected(retryAfter);
      }

      return RateLimitDecision.allowed(limit - current, retryAfter);
    } catch (DataAccessException ex) {
      throw new AppException(
          ErrorCode.RATE_LIMIT_BACKEND_UNAVAILABLE, "rate limit backend is unavailable", ex);
    }
  }

  private String namespaced(String key) {
    return "aegisops:rate-limit:" + key;
  }
}
