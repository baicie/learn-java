package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.application.command.DynamicFilterOperator;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 动态筛选策略服务。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>校验 templateId 必填且存在
 *   <li>校验 fieldCode 符合命名规则
 *   <li>校验字段存在于模板且 enabled=true、filterable=true
 *   <li>校验 operator 是否在字段类型允许的白名单内
 *   <li>按字段类型标准化 value（number 转为字符串化 numeric、datetime 补 offset 等）
 *   <li>拒绝超过上限的 filter 数量和列表值数量
 * </ul>
 *
 * <p>标准化后的 filter 携带 {@link DynamicFilterOperator} enum 和 {@link FieldType} enum，
 * 交由 {@link WorkRecordJsonbFilterSqlBuilder} 生成参数化 SQL。
 */
@Service
public class WorkRecordDynamicFilterPolicyService {
  private static final int MAX_FILTERS = 20;
  private static final int MAX_LIST_VALUES = 100;
  private static final int MAX_TEXT_LENGTH = 500;

  private final WorkRecordTemplateRepository templateRepository;
  private final WorkRecordFieldIndexRepository fieldRepository;

  public WorkRecordDynamicFilterPolicyService(
      WorkRecordTemplateRepository templateRepository,
      WorkRecordFieldIndexRepository fieldRepository) {
    this.templateRepository = templateRepository;
    this.fieldRepository = fieldRepository;
  }

  /**
   * 校验并标准化动态筛选列表。
   *
   * @param tenantId 租户 ID
   * @param templateId 模板 ID（必填）
   * @param rawFilters 原始筛选列表（可能为 null）
   * @return 标准化后的筛选列表
   */
  public List<RecordDynamicFilter> normalize(
      String tenantId,
      String templateId,
      List<RecordDynamicFilter> rawFilters) {
    if (rawFilters == null || rawFilters.isEmpty()) {
      return List.of();
    }

    if (rawFilters.size() > MAX_FILTERS) {
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
      throw new IllegalArgumentException("template has no current version");
    }

    Map<String, WorkRecordField> filterableFields =
        loadFilterableFields(tenantId, template);

    List<RecordDynamicFilter> normalized = new ArrayList<>();
    for (RecordDynamicFilter raw : rawFilters) {
      if (raw == null) {
        throw new IllegalArgumentException("dynamic filter must not be null");
      }

      FieldCodeRules.validate(raw.fieldCode());

      WorkRecordField field = filterableFields.get(raw.fieldCode());
      if (field == null) {
        throw new IllegalArgumentException(
            "field is not filterable: " + raw.fieldCode());
      }

      DynamicFilterOperator operator =
          (raw.operator() == null) ? DynamicFilterOperator.EQ : raw.operator();
      requireOperatorAllowed(field.fieldType(), operator);

      // 存在性操作符不校验值
      if (operator == DynamicFilterOperator.EXISTS
          || operator == DynamicFilterOperator.NOT_EXISTS) {
        normalized.add(
            RecordDynamicFilter.normalized(
                field.fieldCode(),
                operator,
                field.fieldType(),
                null,
                List.of()));
        continue;
      }

      NormalizedValue normalizedValue =
          normalizeValue(field.fieldType(), operator, raw);
      normalized.add(
          RecordDynamicFilter.normalized(
              field.fieldCode(),
              operator,
              field.fieldType(),
              normalizedValue.value(),
              normalizedValue.values()));
    }

    return List.copyOf(normalized);
  }

  private Map<String, WorkRecordField> loadFilterableFields(
      String tenantId,
      WorkRecordTemplate template) {
    List<WorkRecordField> fields =
        fieldRepository.listFilterableByVersions(
            tenantId, List.of(template.currentVersionId()));

    Map<String, WorkRecordField> byCode = new HashMap<>();
    for (WorkRecordField field : fields) {
      FieldCodeRules.validate(field.fieldCode());
      if (field.enabled() && field.filterable()) {
        byCode.put(field.fieldCode(), field);
      }
    }
    return byCode;
  }

  private void requireOperatorAllowed(FieldType fieldType, DynamicFilterOperator operator) {
    EnumSet<DynamicFilterOperator> allowed =
        switch (fieldType) {
          case TEXT, TEXTAREA ->
              EnumSet.of(
                  DynamicFilterOperator.CONTAINS,
                  DynamicFilterOperator.EQ,
                  DynamicFilterOperator.EXISTS,
                  DynamicFilterOperator.NOT_EXISTS);
          case NUMBER, DATE, DATETIME ->
              EnumSet.of(
                  DynamicFilterOperator.EQ,
                  DynamicFilterOperator.GTE,
                  DynamicFilterOperator.LTE,
                  DynamicFilterOperator.BETWEEN,
                  DynamicFilterOperator.EXISTS,
                  DynamicFilterOperator.NOT_EXISTS);
          case SELECT ->
              EnumSet.of(
                  DynamicFilterOperator.EQ,
                  DynamicFilterOperator.IN,
                  DynamicFilterOperator.EXISTS,
                  DynamicFilterOperator.NOT_EXISTS);
          case MULTI_SELECT ->
              EnumSet.of(
                  DynamicFilterOperator.IN,
                  DynamicFilterOperator.CONTAINS_ANY,
                  DynamicFilterOperator.CONTAINS_ALL,
                  DynamicFilterOperator.EXISTS,
                  DynamicFilterOperator.NOT_EXISTS);
          case BOOLEAN ->
              EnumSet.of(
                  DynamicFilterOperator.EQ,
                  DynamicFilterOperator.EXISTS,
                  DynamicFilterOperator.NOT_EXISTS);
          case USER ->
              EnumSet.of(
                  DynamicFilterOperator.EQ,
                  DynamicFilterOperator.IN,
                  DynamicFilterOperator.EXISTS,
                  DynamicFilterOperator.NOT_EXISTS);
        };

    if (!allowed.contains(operator)) {
      throw new IllegalArgumentException(
          "operator "
              + operator.value()
              + " is not allowed for "
              + fieldType.value());
    }
  }

  private NormalizedValue normalizeValue(
      FieldType fieldType,
      DynamicFilterOperator operator,
      RecordDynamicFilter raw) {
    return switch (operator) {
      case EQ, CONTAINS, GTE, LTE ->
          new NormalizedValue(normalizeScalar(fieldType, raw.value()), List.of());
      case IN, CONTAINS_ANY, CONTAINS_ALL ->
          new NormalizedValue(null, normalizeList(fieldType, firstList(raw)));
      case BETWEEN -> {
        List<Object> values = normalizeList(fieldType, firstList(raw));
        if (values.size() != 2) {
          throw new IllegalArgumentException("between requires exactly two values");
        }
        yield new NormalizedValue(null, values);
      }
      case EXISTS, NOT_EXISTS -> new NormalizedValue(null, List.of());
    };
  }

  private Collection<?> firstList(RecordDynamicFilter raw) {
    if (raw.values() != null) {
      return raw.values();
    }
    if (raw.value() instanceof Collection<?> collection) {
      return collection;
    }
    throw new IllegalArgumentException(
        "operator " + raw.operator().value() + " requires array values");
  }

  private List<Object> normalizeList(FieldType fieldType, Collection<?> rawValues) {
    if (rawValues == null || rawValues.isEmpty()) {
      throw new IllegalArgumentException("dynamic filter values must not be empty");
    }

    if (rawValues.size() > MAX_LIST_VALUES) {
      throw new IllegalArgumentException("too many dynamic filter values");
    }

    List<Object> normalized = new ArrayList<>();
    for (Object value : rawValues) {
      normalized.add(normalizeScalar(fieldType, value));
    }
    return normalized;
  }

  private Object normalizeScalar(FieldType fieldType, Object raw) {
    if (raw == null) {
      throw new IllegalArgumentException("dynamic filter value is required");
    }

    return switch (fieldType) {
      case NUMBER -> normalizeNumber(raw);
      case DATE -> LocalDate.parse(requireText(raw, "date filter value")).toString();
      case DATETIME -> OffsetDateTime.parse(requireText(raw, "datetime filter value")).toString();
      case BOOLEAN -> normalizeBoolean(raw);
      case TEXT, TEXTAREA, SELECT, MULTI_SELECT, USER -> normalizeText(raw);
    };
  }

  private String normalizeNumber(Object raw) {
    try {
      return new BigDecimal(String.valueOf(raw)).stripTrailingZeros().toPlainString();
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("invalid number filter value: " + raw, ex);
    }
  }

  private String normalizeBoolean(Object raw) {
    if (raw instanceof Boolean bool) {
      return Boolean.toString(bool);
    }
    String text = String.valueOf(raw).trim();
    if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
      return text.toLowerCase();
    }
    throw new IllegalArgumentException("invalid boolean filter value: " + raw);
  }

  private String normalizeText(Object raw) {
    String text = requireText(raw, "dynamic filter value").trim();
    if (text.isBlank()) {
      throw new IllegalArgumentException("dynamic filter value must not be blank");
    }
    if (text.length() > MAX_TEXT_LENGTH) {
      throw new IllegalArgumentException("dynamic filter value is too long");
    }
    return text;
  }

  private String requireText(Object raw, String message) {
    if (!(raw instanceof String text)) {
      return String.valueOf(raw);
    }
    if (text.isBlank()) {
      throw new IllegalArgumentException(message + " must not be blank");
    }
    return text;
  }

  private record NormalizedValue(Object value, List<Object> values) {}
}
