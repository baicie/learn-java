package io.aegisops.report;

public record GenerateIncidentReportRequest(Boolean force, String locale, String createdBy) {
  public boolean shouldGenerate() {
    return force == null || force;
  }

  public String normalizedLocale() {
    return locale == null || locale.isBlank() ? "zh-CN" : locale.trim();
  }

  public String normalizedCreatedBy() {
    return createdBy == null || createdBy.isBlank() ? "system" : createdBy.trim();
  }
}
