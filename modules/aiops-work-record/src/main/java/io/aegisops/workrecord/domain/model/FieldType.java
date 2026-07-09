package io.aegisops.workrecord.domain.model;

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

  public String value() {
    return value;
  }

  public static FieldType from(String value) {
    for (FieldType type : values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("unsupported fieldType: " + value);
  }
}
