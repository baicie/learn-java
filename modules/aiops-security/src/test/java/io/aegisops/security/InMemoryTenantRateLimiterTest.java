package io.aegisops.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InMemoryTenantRateLimiterTest {
  @Test
  void allowsWithinLimitAndRejectsAfterLimit() {
    InMemoryTenantRateLimiter limiter =
        new InMemoryTenantRateLimiter(
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    assertTrue(limiter.tryAcquire("tenant_1:api", 2));
    assertTrue(limiter.tryAcquire("tenant_1:api", 2));
    assertFalse(limiter.tryAcquire("tenant_1:api", 2));
  }

  @Test
  void separatesTenantBuckets() {
    InMemoryTenantRateLimiter limiter =
        new InMemoryTenantRateLimiter(
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    assertTrue(limiter.tryAcquire("tenant_1:api", 1));
    assertFalse(limiter.tryAcquire("tenant_1:api", 1));

    assertTrue(limiter.tryAcquire("tenant_2:api", 1));
  }
}
