package io.aegisops.workrecord.domain.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.port.WorkRecordDictionaryPort;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkRecordValueValidator {
  private final ObjectMapper objectMapper;
  private final WorkRecordDictionaryPort dictionaryPort;

  public WorkRecordValueValidator(ObjectMapper objectMapper, WorkRecordDictionaryPort dictionaryPort) {
    this.objectMapper = objectMapper;
    this.dictionaryPort = dictionaryPort;
  }

  public void validate(String tenantId, List<WorkRecordField> fields, String customDataJson) {
    try {
      JsonNode root =
          objectMapper.readTree(
              customDataJson == null || customDataJson.isBlank() ? "{}" : customDataJson);
      if (!root.isObject()) {
        throw new IllegalArgumentException("customDataJson must be object");
      }

      Map<String, WorkRecordField> fieldMap = new HashMap<>();
      for (WorkRecordField field : fields) {
        fieldMap.put(field.fieldCode(), field);
      }

      root.fieldNames()
          .forEachRemaining(
              code -> {
                WorkRecordField field = fieldMap.get(code);
                if (field == null) {
                  throw new IllegalArgumentException("unknown field: " + code);
                }
                if (!field.enabled()) {
                  throw new IllegalArgumentException("field is disabled: " + code);
                }
                validateValue(tenantId, field, root.get(code));
              });

      for (WorkRecordField field : fields) {
        if (field.enabled() && field.required()) {
          JsonNode value = root.get(field.fieldCode());
          if (value == null
              || value.isNull()
              || (value.isTextual() && value.asText().isBlank())) {
            throw new IllegalArgumentException(
                "required field is missing: " + field.fieldCode());
          }
        }
      }
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid customDataJson", ex);
    }
  }

  private void validateValue(String tenantId, WorkRecordField field, JsonNode value) {
    if (value == null || value.isNull()) {
      return;
    }

    FieldType type = field.fieldType();
    switch (type) {
      case TEXT, TEXTAREA, SELECT, USER -> requireText(field, value);
      case NUMBER -> {
        if (!value.isNumber()) {
          throw new IllegalArgumentException("field must be number: " + field.fieldCode());
        }
      }
      case BOOLEAN -> {
        if (!value.isBoolean()) {
          throw new IllegalArgumentException("field must be boolean: " + field.fieldCode());
        }
      }
      case DATE -> {
        requireText(field, value);
        LocalDate.parse(value.asText());
      }
      case DATETIME -> {
        requireText(field, value);
        OffsetDateTime.parse(value.asText());
      }
      case MULTI_SELECT -> {
        if (!value.isArray()) {
          throw new IllegalArgumentException("field must be array: " + field.fieldCode());
        }
        value.forEach(
            item -> {
              if (!item.isTextual()) {
                throw new IllegalArgumentException(
                    "multi_select item must be string: " + field.fieldCode());
              }
            });
      }
    }

    if (field.optionSource() == OptionSource.DICT) {
      validateDictValue(tenantId, field, value);
    }
  }

  private void validateDictValue(String tenantId, WorkRecordField field, JsonNode value) {
    if (field.fieldType() == FieldType.MULTI_SELECT) {
      value.forEach(
          item -> dictionaryPort.requireEnabledItem(tenantId, field.dictCode(), item.asText()));
      return;
    }
    dictionaryPort.requireEnabledItem(tenantId, field.dictCode(), value.asText());
  }

  private void requireText(WorkRecordField field, JsonNode value) {
    if (!value.isTextual()) {
      throw new IllegalArgumentException("field must be string: " + field.fieldCode());
    }
  }
}
