package io.aegisops.platform.iam.domain;

public enum PlatformUserStatus {
  ACTIVE,
  DISABLED,
  LOCKED,
  PENDING;

  public String value() {
    return name().toLowerCase();
  }

  public static PlatformUserStatus from(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("user status is required");
    }
    return PlatformUserStatus.valueOf(value.trim().toUpperCase());
  }
}
