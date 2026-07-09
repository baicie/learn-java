package io.aegisops.workrecord.api.dto;

public final class TemplateRequests {
  private TemplateRequests() {}

  public record CreateTemplateRequest(
      String code,
      String name,
      String description,
      String schemaJson,
      String designerJson) {}

  public record UpdateTemplateRequest(String name, String description) {}

  public record UpdateTemplateDraftRequest(
      String name,
      String description,
      String schemaJson,
      String designerJson) {}

  public record CopyTemplateRequest(
      String targetCode, String targetName, String description) {}

  public record PublishTemplateRequest(String versionName) {}
}