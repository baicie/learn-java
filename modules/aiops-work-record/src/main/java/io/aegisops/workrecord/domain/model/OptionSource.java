package io.aegisops.workrecord.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OptionSource {
  STATIC("static"),
  DICT("dict");

  private final String value;

  OptionSource(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static OptionSource from(String value) {
    if (value == null || value.isBlank()) {
      return STATIC;
    }
    for (OptionSource source : values()) {
      if (source.value.equalsIgnoreCase(value) || source.name().equalsIgnoreCase(value)) {
        return source;
      }
    }
    throw new IllegalArgumentException("unsupported optionSource: " + value);
  }
}