package io.aegisops.security;

public record RateLimitDecision(boolean allowed, long remaining, long retryAfterSeconds) {

  public static RateLimitDecision allowed(long remaining, long retryAfterSeconds) {
    return new RateLimitDecision(
        true, Math.max(0, remaining), Math.max(0, retryAfterSeconds));
  }

  public static RateLimitDecision rejected(long retryAfterSeconds) {
    return new RateLimitDecision(false, 0, Math.max(1, retryAfterSeconds));
  }
}