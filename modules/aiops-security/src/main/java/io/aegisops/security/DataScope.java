package io.aegisops.security;

import java.util.Locale;

public enum DataScope {
  SELF(10),
  ALL(100);

  private final int priority;

  DataScope(int priority) {
    this.priority = priority;
  }

  public int priority() {
    return priority;
  }

  public static DataScope from(String raw) {
    if (raw == null || raw.isBlank()) {
      return SELF;
    }

    return valueOf(raw.trim().toUpperCase(Locale.ROOT));
  }

  public static DataScope max(DataScope left, DataScope right) {
    if (left == null) {
      return right == null ? SELF : right;
    }
    if (right == null) {
      return left;
    }

    return left.priority >= right.priority ? left : right;
  }
}
