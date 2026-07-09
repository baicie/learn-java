package io.aegisops.workrecord.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FieldType {
  TEXT("text"),
  TEXTAREA("textarea"),
  NUMBER("number"),
  DATE("date"),
  DATETIME("datetime"),
  SELECT("select"),
  MULTI_SELECT("multi_select"),
  USER("user"),
  BOOLEAN("boolean");

  private final String value;

  FieldType(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static FieldType from(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("fieldType is required");
    }
    for (FieldType type : values()) {
      if (type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("unsupported fieldType: " + value);
  }
}