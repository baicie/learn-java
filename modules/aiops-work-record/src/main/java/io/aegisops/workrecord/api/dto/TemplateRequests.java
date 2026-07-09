package io.aegisops.workrecord.api.dto;

public final class TemplateRequests {
  private TemplateRequests() {}

  public record CreateTemplateRequest(
      String code, String name, String description, String schemaJson, String designerJson) {}

  public record UpdateTemplateDraftRequest(
      String name, String description, String schemaJson, String designerJson) {}

  public record PublishTemplateRequest(String versionName) {}
}
