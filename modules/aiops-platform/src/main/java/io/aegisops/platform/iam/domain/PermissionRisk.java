package io.aegisops.platform.iam.domain;

public enum PermissionRisk {
  NORMAL,
  SENSITIVE,
  HIGH,
  CRITICAL;

  public String value() {
    return name().toLowerCase();
  }

  public static PermissionRisk from(String value) {
    if (value == null || value.isBlank()) {
      return NORMAL;
    }
    return PermissionRisk.valueOf(value.trim().toUpperCase());
  }
}
