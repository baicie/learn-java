package io.aegisops.workrecord.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TemplateStatus {
  DRAFT,
  PUBLISHED,
  DISABLED,
  ARCHIVED;

  @JsonValue
  public String value() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static TemplateStatus from(String value) {
    if (value == null || value.isBlank()) {
      return DRAFT;
    }
    return TemplateStatus.valueOf(value.trim().toUpperCase());
  }
}
