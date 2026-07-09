package io.aegisops.workrecord.application.command;

import java.util.List;

public record TemplatePublishValidationResult(
    boolean valid,
    int schemaVersion,
    int fieldCount,
    long referencedRecordCount,
    List<String> errors,
    List<String> warnings) {
  public static TemplatePublishValidationResult ok(
      int schemaVersion, int fieldCount, long referencedRecordCount, List<String> warnings) {
    return new TemplatePublishValidationResult(
        true, schemaVersion, fieldCount, referencedRecordCount, List.of(), warnings);
  }

  public static TemplatePublishValidationResult failed(List<String> errors) {
    return new TemplatePublishValidationResult(false, 0, 0, 0L, errors, List.of());
  }
}