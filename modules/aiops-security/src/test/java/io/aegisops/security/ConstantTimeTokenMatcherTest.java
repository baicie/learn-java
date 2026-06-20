package io.aegisops.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConstantTimeTokenMatcherTest {
  @Test
  void matchesSameToken() {
    assertTrue(ConstantTimeTokenMatcher.matches("abc", "abc"));
  }

  @Test
  void rejectsDifferentToken() {
    assertFalse(ConstantTimeTokenMatcher.matches("abc", "def"));
  }

  @Test
  void rejectsNull() {
    assertFalse(ConstantTimeTokenMatcher.matches("abc", null));
    assertFalse(ConstantTimeTokenMatcher.matches(null, "abc"));
  }
}
