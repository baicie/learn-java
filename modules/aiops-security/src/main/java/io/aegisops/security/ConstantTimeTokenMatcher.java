package io.aegisops.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class ConstantTimeTokenMatcher {
  private ConstantTimeTokenMatcher() {}

  public static boolean matches(String expected, String actual) {
    if (expected == null || actual == null) {
      return false;
    }

    byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
    byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);

    return MessageDigest.isEqual(expectedBytes, actualBytes);
  }
}
