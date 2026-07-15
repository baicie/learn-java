package io.aegisops.workrecord.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RecordStatus {
  DRAFT,
  PROCESSING,
  PENDING_APPROVAL,
  REJECTED,
  DONE,
  ARCHIVED;

  @JsonValue
  public String value() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static RecordStatus from(String value) {
    if (value == null || value.isBlank()) {
      return DRAFT;
    }
    return RecordStatus.valueOf(value.trim().toUpperCase());
  }
}
