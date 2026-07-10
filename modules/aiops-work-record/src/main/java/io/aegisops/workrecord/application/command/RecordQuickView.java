package io.aegisops.workrecord.application.command;

public enum RecordQuickView {
  MINE("mine"),
  ALL("all"),
  TODAY("today"),
  THIS_WEEK("this_week"),
  THIS_MONTH("this_month"),
  RECENT_WORKDAYS("recent_workdays");

  private final String value;

  RecordQuickView(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static RecordQuickView from(String value) {
    if (value == null || value.isBlank()) {
      return ALL;
    }
    for (RecordQuickView item : values()) {
      if (item.value.equalsIgnoreCase(value) || item.name().equalsIgnoreCase(value)) {
        return item;
      }
    }
    throw new IllegalArgumentException("unsupported quickView: " + value);
  }
}
