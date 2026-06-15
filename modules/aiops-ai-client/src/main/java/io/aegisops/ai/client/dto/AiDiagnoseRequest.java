package io.aegisops.ai.client.dto;

public record AiDiagnoseRequest(Boolean force, String locale) {
  public boolean forceEnabled() {
    return Boolean.TRUE.equals(force);
  }

  public String normalizedLocale() {
    return locale == null || locale.isBlank() ? "zh-CN" : locale.trim();
  }
}
