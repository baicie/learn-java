package io.aegisops.workrecord.application.service;

import io.aegisops.common.exception.AppException;
import io.aegisops.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class WorkRecordPayloadPolicy {

  private final WorkRecordProductionProperties properties;

  public WorkRecordPayloadPolicy(WorkRecordProductionProperties properties) {
    this.properties = properties;
  }

  public void requireSchema(String json) {
    requireBytes(
        json,
        properties.getPayload().getSchemaMaxBytes(),
        ErrorCode.SCHEMA_JSON_TOO_LARGE,
        "schemaJson");
  }

  public void requireDesigner(String json) {
    requireBytes(
        json,
        properties.getPayload().getDesignerMaxBytes(),
        ErrorCode.DESIGNER_JSON_TOO_LARGE,
        "designerJson");
  }

  public void requireCustomData(String json) {
    requireBytes(
        json,
        properties.getPayload().getCustomDataMaxBytes(),
        ErrorCode.CUSTOM_DATA_JSON_TOO_LARGE,
        "customDataJson");
  }

  public void requireBuiltinData(String json) {
    requireBytes(
        json,
        properties.getPayload().getBuiltinDataMaxBytes(),
        ErrorCode.BUILTIN_DATA_JSON_TOO_LARGE,
        "builtinDataJson");
  }

  private void requireBytes(String value, int maxBytes, ErrorCode errorCode, String fieldName) {
    if (value == null) {
      return;
    }

    int bytes = value.getBytes(StandardCharsets.UTF_8).length;

    if (bytes > maxBytes) {
      throw new AppException(errorCode, fieldName + " exceeds " + maxBytes + " bytes");
    }
  }
}