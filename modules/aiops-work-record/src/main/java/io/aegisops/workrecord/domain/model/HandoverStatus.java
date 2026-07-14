package io.aegisops.workrecord.domain.model;

public enum HandoverStatus {
  DRAFT,
  SUBMITTED,
  ACCEPTED,
  COMPLETED,
  CANCELLED;

  public String value() {
    return name().toLowerCase();
  }

  public static HandoverStatus from(String value) {
    return valueOf(value.toUpperCase());
  }
}
