package io.aegisops.workrecord.domain.model;

import java.util.List;

public record FieldPolicy(
    String templateVersionId,
    String fieldCode,
    List<String> readRoles,
    List<String> writeRoles,
    MaskMode maskMode) {
  public FieldPolicy {
    readRoles = readRoles == null ? List.of() : List.copyOf(readRoles);
    writeRoles = writeRoles == null ? List.of() : List.copyOf(writeRoles);
    maskMode = maskMode == null ? MaskMode.NONE : maskMode;
  }

  public enum MaskMode {
    NONE,
    FULL,
    PARTIAL;

    public static MaskMode from(String value) {
      return value == null ? NONE : valueOf(value.toUpperCase());
    }

    public String value() {
      return name().toLowerCase();
    }
  }
}
