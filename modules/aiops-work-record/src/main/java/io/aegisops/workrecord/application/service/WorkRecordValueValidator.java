package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.port.WorkRecordDictionaryPort;
import io.aegisops.workrecord.application.port.WorkRecordUserPort;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.rule.FieldCodeRules;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordValueValidator {
  private final ObjectMapper objectMapper;
  private final WorkRecordDictionaryPort dictionaryPort;
  private final WorkRecordUserPort userPort;

  public WorkRecordValueValidator(
      ObjectMapper objectMapper,
      WorkRecordDictionaryPort dictionaryPort,
      WorkRecordUserPort userPort) {
    this.objectMapper = objectMapper;
    this.dictionaryPort = dictionaryPort;
    this.userPort = userPort;
  }

  public void validate(
      String tenantId,
      String templateVersionId,
      List<WorkRecordField> fields,
      String customDataJson) {
    try {
      if (tenantId == null || tenantId.isBlank()) {
        throw new IllegalArgumentException("tenantId is required");
      }
      if (templateVersionId == null || templateVersionId.isBlank()) {
        throw new IllegalArgumentException("templateVersionId is required");
      }

      JsonNode root =
          objectMapper.readTree(
              customDataJson == null || customDataJson.isBlank() ? "{}" : customDataJson);
      if (!root.isObject()) {
        throw new IllegalArgumentException("customDataJson must be object");
      }

      Map<String, WorkRecordField> fieldMap = indexFields(tenantId, templateVersionId, fields);

      var names = root.fieldNames();
      while (names.hasNext()) {
        String code = names.next();
        FieldCodeRules.validate(code);

        WorkRecordField field = fieldMap.get(code);
        if (field == null) {
          throw new IllegalArgumentException("unknown field: " + code);
        }
        if (!field.enabled()) {
          throw new IllegalArgumentException("field is disabled: " + code);
        }

        validateValue(tenantId, field, root.get(code));
      }

      for (WorkRecordField field : fields) {
        validateFieldDefinition(tenantId, templateVersionId, field);

        if (field.enabled() && field.required()) {
          JsonNode value = root.get(field.fieldCode());
          if (isMissingRequiredValue(value)) {
            throw new IllegalArgumentException("required field is missing: " + field.fieldCode());
          }
        }
      }
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid customDataJson", ex);
    }
  }

  private Map<String, WorkRecordField> indexFields(
      String tenantId, String templateVersionId, List<WorkRecordField> fields) {
    Map<String, WorkRecordField> fieldMap = new HashMap<>();
    Set<String> seen = new HashSet<>();

    for (WorkRecordField field : fields) {
      validateFieldDefinition(tenantId, templateVersionId, field);

      if (!seen.add(field.fieldCode())) {
        throw new IllegalArgumentException("duplicated fieldCode: " + field.fieldCode());
      }

      fieldMap.put(field.fieldCode(), field);
    }

    return fieldMap;
  }

  private void validateFieldDefinition(
      String tenantId, String templateVersionId, WorkRecordField field) {
    if (!tenantId.equals(field.tenantId())) {
      throw new IllegalArgumentException("field tenant mismatch: " + field.fieldCode());
    }
    if (!templateVersionId.equals(field.templateVersionId())) {
      throw new IllegalArgumentException("field templateVersion mismatch: " + field.fieldCode());
    }

    FieldCodeRules.validate(field.fieldCode());

    boolean optionField =
        field.fieldType() == FieldType.SELECT || field.fieldType() == FieldType.MULTI_SELECT;

    if (field.optionSource() == OptionSource.DICT && !optionField) {
      throw new IllegalArgumentException(
          "dict optionSource is only allowed for select/multi_select: " + field.fieldCode());
    }

    if (field.dictCode() != null && !field.dictCode().isBlank() && !optionField) {
      throw new IllegalArgumentException(
          "dictCode is only allowed for select/multi_select: " + field.fieldCode());
    }
  }

  private void validateValue(String tenantId, WorkRecordField field, JsonNode value) {
    if (value == null || value.isNull()) {
      return;
    }

    switch (field.fieldType()) {
      case TEXT, TEXTAREA -> requireText(field, value);
      case USER -> validateUserValue(tenantId, field, value);
      case NUMBER -> validateNumber(field, value);
      case BOOLEAN -> validateBoolean(field, value);
      case DATE -> validateDate(field, value);
      case DATETIME -> validateDatetime(field, value);
      case SELECT -> validateSelect(tenantId, field, value);
      case MULTI_SELECT -> validateMultiSelect(tenantId, field, value);
    }
  }

  private void validateNumber(WorkRecordField field, JsonNode value) {
    if (!value.isNumber()) {
      throw new IllegalArgumentException("field must be number: " + field.fieldCode());
    }
  }

  private void validateBoolean(WorkRecordField field, JsonNode value) {
    if (!value.isBoolean()) {
      throw new IllegalArgumentException("field must be boolean: " + field.fieldCode());
    }
  }

  private void validateDate(WorkRecordField field, JsonNode value) {
    requireText(field, value);
    try {
      LocalDate.parse(value.asText());
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException("field must be ISO date: " + field.fieldCode(), ex);
    }
  }

  private void validateDatetime(WorkRecordField field, JsonNode value) {
    requireText(field, value);
    try {
      OffsetDateTime.parse(value.asText());
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException(
          "field must be ISO offset datetime: " + field.fieldCode(), ex);
    }
  }

  private void validateSelect(String tenantId, WorkRecordField field, JsonNode value) {
    requireText(field, value);

    if (value.asText().isBlank()) {
      throw new IllegalArgumentException("field must not be blank: " + field.fieldCode());
    }

    if (field.optionSource() == OptionSource.DICT) {
      validateDictValue(tenantId, field, value.asText());
      return;
    }

    requireStaticOption(field, value.asText());
  }

  private void validateMultiSelect(String tenantId, WorkRecordField field, JsonNode value) {
    if (!value.isArray()) {
      throw new IllegalArgumentException("field must be array: " + field.fieldCode());
    }

    for (JsonNode item : value) {
      if (!item.isTextual()) {
        throw new IllegalArgumentException(
            "multi_select item must be string: " + field.fieldCode());
      }

      if (item.asText().isBlank()) {
        throw new IllegalArgumentException(
            "multi_select item must not be blank: " + field.fieldCode());
      }

      if (field.optionSource() == OptionSource.DICT) {
        validateDictValue(tenantId, field, item.asText());
      } else {
        requireStaticOption(field, item.asText());
      }
    }
  }

  private void validateUserValue(String tenantId, WorkRecordField field, JsonNode value) {
    requireText(field, value);
    if (value.asText().isBlank()) {
      throw new IllegalArgumentException("field must not be blank: " + field.fieldCode());
    }
    userPort.requireActiveUser(tenantId, value.asText());
  }

  private void validateDictValue(String tenantId, WorkRecordField field, String itemValue) {
    if (field.dictCode() == null || field.dictCode().isBlank()) {
      throw new IllegalArgumentException("dictCode is required: " + field.fieldCode());
    }
    dictionaryPort.requireEnabledItem(tenantId, field.dictCode(), itemValue);
  }

  private void requireStaticOption(WorkRecordField field, String value) {
    Set<String> allowed = parseStaticOptions(field);
    if (allowed.isEmpty()) {
      throw new IllegalArgumentException("static options are required: " + field.fieldCode());
    }
    if (!allowed.contains(value)) {
      throw new IllegalArgumentException(
          "field option is not allowed: " + field.fieldCode() + "/" + value);
    }
  }

  private Set<String> parseStaticOptions(WorkRecordField field) {
    try {
      JsonNode root =
          objectMapper.readTree(
              field.optionsJson() == null || field.optionsJson().isBlank()
                  ? "[]"
                  : field.optionsJson());

      if (!root.isArray()) {
        throw new IllegalArgumentException("optionsJson must be array: " + field.fieldCode());
      }

      Set<String> values = new HashSet<>();
      for (JsonNode item : root) {
        if (item.isTextual() || item.isNumber() || item.isBoolean()) {
          values.add(item.asText());
          continue;
        }

        if (item.isObject()) {
          JsonNode value = item.path("value");
          if (value.isMissingNode() || value.isNull()) {
            value = item.path("itemValue");
          }
          if (value.isMissingNode() || value.isNull()) {
            value = item.path("label");
          }
          if (!value.isMissingNode() && !value.isNull()) {
            values.add(value.asText());
          }
        }
      }
      return values;
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid optionsJson: " + field.fieldCode(), ex);
    }
  }

  private void requireText(WorkRecordField field, JsonNode value) {
    if (!value.isTextual()) {
      throw new IllegalArgumentException("field must be string: " + field.fieldCode());
    }
  }

  private boolean isMissingRequiredValue(JsonNode value) {
    return value == null
        || value.isNull()
        || (value.isTextual() && value.asText().isBlank())
        || (value.isArray() && value.isEmpty());
  }
}
