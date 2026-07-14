package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class InMemoryTenantRateLimiterWindowTest {

  @Test
  void rejectsAfterLimitAndResetsNextWindow() {
    MutableClock clock = new MutableClock(Instant.parse("2026-07-11T00:00:00Z"));
    InMemoryTenantRateLimiter limiter = new InMemoryTenantRateLimiter(clock);

    assertThat(limiter.acquire("tenant-1", 2, Duration.ofMinutes(1)).allowed()).isTrue();

    assertThat(limiter.acquire("tenant-1", 2, Duration.ofMinutes(1)).allowed()).isTrue();

    assertThat(limiter.acquire("tenant-1", 2, Duration.ofMinutes(1)).allowed()).isFalse();

    clock.advance(Duration.ofMinutes(1));

    assertThat(limiter.acquire("tenant-1", 2, Duration.ofMinutes(1)).allowed()).isTrue();
  }

  private static final class MutableClock extends java.time.Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public java.time.Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
