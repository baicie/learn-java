package io.aegisops.workrecord.domain.model;

public enum OptionSource {
  STATIC("static"),
  DICT("dict");

  private final String value;

  OptionSource(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static OptionSource from(String value) {
    if (value == null || value.isBlank()) {
      return STATIC;
    }
    for (OptionSource source : values()) {
      if (source.value.equals(value)) {
        return source;
      }
    }
    throw new IllegalArgumentException("unsupported optionSource: " + value);
  }
}
