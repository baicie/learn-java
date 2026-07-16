package io.aegisops.workrecord.domain.model;

import java.time.OffsetDateTime;

public record WorkRecordTemplate(
    String id,
    String tenantId,
    String code,
    String name,
    String description,
    TemplateStatus status,
    boolean enabled,
    boolean isDefault,
    String currentVersionId,
    String draftSchemaJson,
    String draftDesignerJson,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime deletedAt) {
  public WorkRecordTemplate(
      String id,
      String tenantId,
      String code,
      String name,
      String description,
      TemplateStatus status,
      boolean enabled,
      String currentVersionId,
      String draftSchemaJson,
      String draftDesignerJson,
      String createdBy,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime deletedAt) {
    this(
        id,
        tenantId,
        code,
        name,
        description,
        status,
        enabled,
        false,
        currentVersionId,
        draftSchemaJson,
        draftDesignerJson,
        createdBy,
        createdAt,
        updatedAt,
        deletedAt);
  }
}
