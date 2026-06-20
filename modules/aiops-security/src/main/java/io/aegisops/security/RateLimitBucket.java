package io.aegisops.security;

import java.time.Clock;

final class RateLimitBucket {
  private final int limit;
  private final Clock clock;
  private long windowStartMillis;
  private int count;

  RateLimitBucket(int limit, Clock clock) {
    this.limit = Math.max(1, limit);
    this.clock = clock;
    this.windowStartMillis = now();
    this.count = 0;
  }

  synchronized boolean tryAcquire() {
    long current = now();
    if (current - windowStartMillis >= 60_000L) {
      windowStartMillis = current;
      count = 0;
    }

    if (count >= limit) {
      return false;
    }

    count++;
    return true;
  }

  private long now() {
    return clock.millis();
  }
}
