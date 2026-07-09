package io.aegisops.workrecord.domain.model;

public enum TemplateStatus {
  DRAFT,
  PUBLISHED,
  DISABLED,
  ARCHIVED;

  public String value() {
    return name().toLowerCase();
  }

  public static TemplateStatus from(String value) {
    if (value == null || value.isBlank()) {
      return DRAFT;
    }
    return TemplateStatus.valueOf(value.trim().toUpperCase());
  }
}
