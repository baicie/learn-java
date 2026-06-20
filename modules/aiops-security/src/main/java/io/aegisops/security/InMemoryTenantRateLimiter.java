package io.aegisops.security;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryTenantRateLimiter {
  private final Clock clock;
  private final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

  public InMemoryTenantRateLimiter() {
    this(Clock.systemUTC());
  }

  InMemoryTenantRateLimiter(Clock clock) {
    this.clock = clock;
  }

  public boolean tryAcquire(String key, int limitPerMinute) {
    RateLimitBucket bucket =
        buckets.computeIfAbsent(key, ignored -> new RateLimitBucket(limitPerMinute, clock));
    return bucket.tryAcquire();
  }

  public int bucketCount() {
    return buckets.size();
  }
}
