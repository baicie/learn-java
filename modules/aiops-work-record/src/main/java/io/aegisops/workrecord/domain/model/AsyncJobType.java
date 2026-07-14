package io.aegisops.workrecord.domain.model;

public enum AsyncJobType {
  EXCEL_IMPORT,
  EXCEL_EXPORT,
  REPORT_EXPORT,
  AI_SUMMARY,
  AI_MONTHLY_REPORT;

  public static AsyncJobType fromStorage(String value) {
    return valueOf(value.toUpperCase(java.util.Locale.ROOT));
  }

  public String storageValue() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}
