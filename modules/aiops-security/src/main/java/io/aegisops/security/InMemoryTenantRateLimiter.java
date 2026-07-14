package io.aegisops.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTenantRateLimiter implements RateLimitService {

  private final Clock clock;

  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  private long operations;

  public InMemoryTenantRateLimiter() {
    this(Clock.systemUTC());
  }

  InMemoryTenantRateLimiter(Clock clock) {
    this.clock = clock;
  }

  /** 保留旧测试兼容入口。 */
  public boolean tryAcquire(String key, int limitPerMinute) {
    return acquire(key, limitPerMinute, Duration.ofMinutes(1)).allowed();
  }

  @Override
  public RateLimitDecision acquire(String key, int limit, Duration window) {
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be positive");
    }

    long now = clock.millis();
    long windowMillis = window.toMillis();

    Window current =
        windows.compute(
            key,
            (ignored, existing) -> {
              if (existing == null || now - existing.windowStartMillis >= windowMillis) {
                return new Window(now, 1);
              }
              existing.count++;
              return existing;
            });

    cleanupOccasionally(now, windowMillis);

    long retryAfter = Math.max(1, (current.windowStartMillis + windowMillis - now + 999) / 1000);

    if (current.count > limit) {
      return RateLimitDecision.rejected(retryAfter);
    }

    return RateLimitDecision.allowed(limit - current.count, retryAfter);
  }

  public int bucketCount() {
    return windows.size();
  }

  private void cleanupOccasionally(long now, long windowMillis) {
    operations++;
    if ((operations & 1023) != 0) {
      return;
    }
    long expiredBefore = now - windowMillis * 2;
    windows.entrySet().removeIf(entry -> entry.getValue().windowStartMillis < expiredBefore);
  }

  private static final class Window {
    private final long windowStartMillis;
    private int count;

    private Window(long windowStartMillis, int count) {
      this.windowStartMillis = windowStartMillis;
      this.count = count;
    }
  }
}
