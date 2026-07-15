package io.aegisops.workrecord.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum HandoverStatus {
  DRAFT,
  SUBMITTED,
  ACCEPTED,
  COMPLETED,
  CANCELLED;

  @JsonValue
  public String value() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static HandoverStatus from(String value) {
    return valueOf(value.toUpperCase());
  }
}
