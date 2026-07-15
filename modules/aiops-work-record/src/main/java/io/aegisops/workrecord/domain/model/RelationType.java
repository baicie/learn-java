package io.aegisops.workrecord.domain.model;

import java.util.Locale;

public enum RelationType {
  ALERT,
  INSPECTION,
  INCIDENT;

  public static RelationType from(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("relation type is required");
    }
    return valueOf(value.trim().toUpperCase(Locale.ROOT));
  }

  public String storageValue() {
    return name().toLowerCase(Locale.ROOT);
  }
}
