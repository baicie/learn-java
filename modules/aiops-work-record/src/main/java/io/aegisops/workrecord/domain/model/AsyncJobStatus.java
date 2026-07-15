package io.aegisops.workrecord.domain.model;

public enum AsyncJobStatus {
  QUEUED,
  PROCESSING,
  SUCCEEDED,
  PARTIALLY_SUCCEEDED,
  FAILED,
  CANCELLED,
  EXPIRED;

  public static AsyncJobStatus fromStorage(String value) {
    return valueOf(value.toUpperCase(java.util.Locale.ROOT));
  }

  public String storageValue() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}
