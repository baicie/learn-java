package io.aegisops.security;

import java.time.Duration;

public interface RateLimitService {

  RateLimitDecision acquire(String key, int limit, Duration window);
}