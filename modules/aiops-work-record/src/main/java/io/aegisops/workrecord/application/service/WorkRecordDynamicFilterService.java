package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateRepository;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import io.aegisops.workrecord.domain.rule.FieldCodeRules;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordDynamicFilterService {
  private static final int MAX_FILTERS = 20;
  private static final int MAX_IN_VALUES = 50;

  private final WorkRecordTemplateRepository templateRepository;
  private final WorkRecordFieldIndexRepository fieldRepository;

  public WorkRecordDynamicFilterService(
      WorkRecordTemplateRepository templateRepository,
      WorkRecordFieldIndexRepository fieldRepository) {
    this.templateRepository = templateRepository;
    this.fieldRepository = fieldRepository;
  }

  public List<RecordDynamicFilter> validateAndNormalize(
      String tenantId,
      String templateId,
      List<RecordDynamicFilter> filters) {
    if (filters == null || filters.isEmpty()) {
      return List.of();
    }

    if (filters.size() > MAX_FILTERS) {
      throw new IllegalArgumentException("too many dynamic filters");
    }

    if (templateId == null || templateId.isBlank()) {
      throw new IllegalArgumentException(
          "templateId is required when dynamic filters are used");
    }

    WorkRecordTemplate template =
        templateRepository
            .find(tenantId, templateId)
            .orElseThrow(() -> new IllegalArgumentException("template not found"));

    if (template.currentVersionId() == null || template.currentVersionId().isBlank()) {
      throw new IllegalArgumentException("template has no published version");
    }

    List<WorkRecordField> fields =
        fieldRepository.listFilterableByVersions(
            tenantId, List.of(template.currentVersionId()));

    Map<String, WorkRecordField> fieldMap = new HashMap<>();
    for (WorkRecordField field : fields) {
      if (field.enabled() && field.filterable()) {
        fieldMap.put(field.fieldCode(), field);
      }
    }

    List<RecordDynamicFilter> normalized = new ArrayList<>();

    for (RecordDynamicFilter filter : filters) {
      if (filter == null) {
        throw new IllegalArgumentException("dynamic filter must not be null");
      }

      FieldCodeRules.validate(filter.fieldCode());

      WorkRecordField field = fieldMap.get(filter.fieldCode());
      if (field == null) {
        throw new IllegalArgumentException(
            "field is not filterable: " + filter.fieldCode());
      }

      String operator = normalizeOperator(filter.operator());
      requireOperatorSupported(field.fieldType(), operator);

      Object value =
          "in".equals(operator)
              ? normalizeListValue(field, filter.value())
              : normalizeSingleValue(field, operator, filter.value());

      normalized.add(
          filter.normalized(operator, value, field.fieldType().value()));
    }

    return List.copyOf(normalized);
  }

  private String normalizeOperator(String value) {
    if (value == null || value.isBlank()) {
      return "eq";
    }

    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (!Set.of("eq", "in", "contains", "gte", "lte").contains(normalized)) {
      throw new IllegalArgumentException("unsupported dynamic filter operator: " + value);
    }
    return normalized;
  }

  private void requireOperatorSupported(FieldType type, String operator) {
    Set<String> supported =
        switch (type) {
          case TEXT, TEXTAREA -> Set.of("eq", "contains");
          case USER -> Set.of("eq", "in");
          case NUMBER, DATE, DATETIME -> Set.of("eq", "gte", "lte");
          case SELECT -> Set.of("eq", "in");
          case MULTI_SELECT -> Set.of("contains", "in");
          case BOOLEAN -> Set.of("eq");
        };

    if (!supported.contains(operator)) {
      throw new IllegalArgumentException(
          "operator " + operator + " is not supported for " + type.value());
    }
  }

  private Object normalizeListValue(WorkRecordField field, Object raw) {
    if (!(raw instanceof Collection<?> values)) {
      throw new IllegalArgumentException(
          "operator in requires array value: " + field.fieldCode());
    }

    if (values.isEmpty() || values.size() > MAX_IN_VALUES) {
      throw new IllegalArgumentException(
          "invalid in values size: " + field.fieldCode());
    }

    return values.stream()
        .map(value -> normalizeScalar(field.fieldType(), value))
        .toList();
  }

  private Object normalizeSingleValue(
      WorkRecordField field,
      String operator,
      Object raw) {
    if (raw instanceof Collection<?>) {
      throw new IllegalArgumentException(
          "operator " + operator + " requires scalar value: " + field.fieldCode());
    }

    Object normalized = normalizeScalar(field.fieldType(), raw);

    if ("contains".equals(operator)
        && normalized instanceof String text
        && text.isBlank()) {
      throw new IllegalArgumentException(
          "contains value must not be blank: " + field.fieldCode());
    }

    return normalized;
  }

  private Object normalizeScalar(FieldType type, Object raw) {
    if (raw == null) {
      throw new IllegalArgumentException("dynamic filter value is required");
    }

    return switch (type) {
      case NUMBER -> new BigDecimal(String.valueOf(raw)).stripTrailingZeros().toPlainString();
      case BOOLEAN -> normalizeBoolean(raw);
      case DATE -> LocalDate.parse(String.valueOf(raw)).toString();
      case DATETIME -> OffsetDateTime.parse(String.valueOf(raw)).toString();
      case TEXT, TEXTAREA, SELECT, MULTI_SELECT, USER -> {
        String value = String.valueOf(raw).trim();
        if (value.isBlank()) {
          throw new IllegalArgumentException("dynamic filter value must not be blank");
        }
        yield value;
      }
    };
  }

  private String normalizeBoolean(Object raw) {
    if (raw instanceof Boolean bool) {
      return Boolean.toString(bool);
    }

    String value = String.valueOf(raw);
    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
      throw new IllegalArgumentException("invalid boolean filter value");
    }
    return value.toLowerCase(Locale.ROOT);
  }
}