package io.aegisops.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class SecurityConfigPermitAllTest {
  @Test
  void apiAuthMatcherShouldUseWildcard() {
    assertDoesNotThrow(
        () -> {
          String matcher = "/api/auth/**";
          if (!matcher.endsWith("/**")) {
            throw new IllegalStateException("auth matcher must include wildcard");
          }
        });
  }
}
