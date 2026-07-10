package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.application.command.DynamicFilterOperator;
import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateVersionRepository;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import io.aegisops.workrecord.domain.rule.FieldCodeRules;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
 *   <li>严格按字段类型校验值：text/select/user/multi_select 必须为字符串，number 必须为数字或数字字符串，date 必须为 ISO
 *       日期字符串，datetime 必须为 ISO offset datetime 字符串，boolean 必须为布尔或 "true"/"false"
 *   <li>拒绝 value 和 values 同时存在的歧义 DTO
 *   <li>拒绝 exists/not_exists 携带 value 或 values
 *   <li>拒绝 scalar 操作符携带 values
 *   <li>拒绝 between 上下界顺序颠倒
 *   <li>拒绝超过上限的 filter 数量和列表值数量
 * </ul>
 *
 * <p>标准化后的 filter 携带 {@link DynamicFilterOperator} enum 和 {@link FieldType} enum， 交由 {@link
 * WorkRecordJsonbFilterSqlBuilder} 生成参数化 SQL。
 */
@Service
public class WorkRecordDynamicFilterPolicyService {
  private static final int MAX_FILTERS = 20;
  private static final int MAX_LIST_VALUES = 100;
  private static final int MAX_TEXT_LENGTH = 500;

  private final WorkRecordTemplateRepository templateRepository;
  private final WorkRecordTemplateVersionRepository versionRepository;
  private final WorkRecordFieldIndexRepository fieldRepository;

  public WorkRecordDynamicFilterPolicyService(
      WorkRecordTemplateRepository templateRepository,
      WorkRecordTemplateVersionRepository versionRepository,
      WorkRecordFieldIndexRepository fieldRepository) {
    this.templateRepository = templateRepository;
    this.versionRepository = versionRepository;
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
      String tenantId, String templateId, List<RecordDynamicFilter> rawFilters) {
    return normalize(tenantId, templateId, null, rawFilters);
  }

  /**
   * 校验并标准化动态筛选列表，支持按历史模板版本白名单校验。
   *
   * @param tenantId 租户 ID
   * @param templateId 模板 ID（必填）
   * @param templateVersionId 模板版本 ID（可选，为 null 时使用当前发布版本）
   * @param rawFilters 原始筛选列表（可能为 null）
   * @return 标准化后的筛选列表
   */
  public List<RecordDynamicFilter> normalize(
      String tenantId,
      String templateId,
      String templateVersionId,
      List<RecordDynamicFilter> rawFilters) {
    if (rawFilters == null || rawFilters.isEmpty()) {
      return List.of();
    }

    if (rawFilters.size() > MAX_FILTERS) {
      throw new IllegalArgumentException("too many dynamic filters");
    }

    if (templateId == null || templateId.isBlank()) {
      throw new IllegalArgumentException("templateId is required when dynamic filters are used");
    }

    WorkRecordTemplate template =
        templateRepository
            .find(tenantId, templateId)
            .orElseThrow(() -> new IllegalArgumentException("template not found"));

    String effectiveVersionId = resolveVersionId(tenantId, template, templateVersionId);

    Map<String, WorkRecordField> filterableFields =
        loadFilterableFields(tenantId, effectiveVersionId);

    List<RecordDynamicFilter> normalized = new ArrayList<>();
    for (RecordDynamicFilter raw : rawFilters) {
      if (raw == null) {
        throw new IllegalArgumentException("dynamic filter must not be null");
      }

      FieldCodeRules.validate(raw.fieldCode());

      WorkRecordField field = filterableFields.get(raw.fieldCode());
      if (field == null) {
        throw new IllegalArgumentException("field is not filterable: " + raw.fieldCode());
      }

      DynamicFilterOperator operator =
          (raw.operator() == null) ? DynamicFilterOperator.EQ : raw.operator();

      requireOperatorAllowed(field.fieldType(), operator);
      validateDtoShape(raw, operator);

      if (operator == DynamicFilterOperator.EXISTS
          || operator == DynamicFilterOperator.NOT_EXISTS) {
        normalized.add(
            RecordDynamicFilter.normalized(
                field.fieldCode(), operator, field.fieldType(), null, List.of()));
        continue;
      }

      NormalizedValue normalizedValue = normalizeValue(field.fieldType(), operator, raw);

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

  private Map<String, WorkRecordField> loadFilterableFields(String tenantId, String versionId) {
    List<WorkRecordField> fields =
        fieldRepository.listFilterableByVersions(tenantId, List.of(versionId));

    Map<String, WorkRecordField> byCode = new HashMap<>();
    for (WorkRecordField field : fields) {
      FieldCodeRules.validate(field.fieldCode());
      if (field.enabled() && field.filterable()) {
        byCode.put(field.fieldCode(), field);
      }
    }
    return byCode;
  }

  private String resolveVersionId(
      String tenantId, WorkRecordTemplate template, String requestedVersionId) {
    if (requestedVersionId != null && !requestedVersionId.isBlank()) {
      return versionRepository
          .findByTemplateAndVersion(tenantId, template.id(), requestedVersionId)
          .orElseThrow(() -> new IllegalArgumentException("template version not found"))
          .id();
    }

    if (template.currentVersionId() == null || template.currentVersionId().isBlank()) {
      throw new IllegalArgumentException("template has no current version");
    }

    return template.currentVersionId();
  }

  private void validateDtoShape(RecordDynamicFilter raw, DynamicFilterOperator operator) {
    boolean hasValue = raw.value() != null;
    boolean hasValues = raw.values() != null;

    if (operator == DynamicFilterOperator.EXISTS || operator == DynamicFilterOperator.NOT_EXISTS) {
      if (hasValue || hasValues) {
        throw new IllegalArgumentException(operator.value() + " must not contain value or values");
      }
      return;
    }

    if (isListOperator(operator)) {
      boolean valueIsCollection = raw.value() instanceof Collection<?>;

      if (hasValues && valueIsCollection) {
        throw new IllegalArgumentException(
            "dynamic filter must not contain both value array and values");
      }

      if (!hasValues && !valueIsCollection) {
        throw new IllegalArgumentException(operator.value() + " requires array values");
      }
      return;
    }

    if (hasValues) {
      throw new IllegalArgumentException(operator.value() + " must not contain values");
    }

    if (!hasValue || raw.value() instanceof Collection<?>) {
      throw new IllegalArgumentException(operator.value() + " requires scalar value");
    }
  }

  private boolean isListOperator(DynamicFilterOperator operator) {
    return operator == DynamicFilterOperator.IN
        || operator == DynamicFilterOperator.BETWEEN
        || operator == DynamicFilterOperator.CONTAINS_ANY
        || operator == DynamicFilterOperator.CONTAINS_ALL;
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
          "operator " + operator.value() + " is not allowed for " + fieldType.value());
    }
  }

  private NormalizedValue normalizeValue(
      FieldType fieldType, DynamicFilterOperator operator, RecordDynamicFilter raw) {
    return switch (operator) {
      case EQ, CONTAINS, GTE, LTE ->
          new NormalizedValue(normalizeScalar(fieldType, raw.value()), List.of());

      case IN, CONTAINS_ANY, CONTAINS_ALL ->
          new NormalizedValue(null, normalizeList(fieldType, extractList(raw)));

      case BETWEEN -> {
        List<Object> values = normalizeList(fieldType, extractList(raw));
        if (values.size() != 2) {
          throw new IllegalArgumentException("between requires exactly two values");
        }
        requireAscendingRange(fieldType, values);
        yield new NormalizedValue(null, values);
      }

      case EXISTS, NOT_EXISTS -> new NormalizedValue(null, List.of());
    };
  }

  private Collection<?> extractList(RecordDynamicFilter raw) {
    if (raw.values() != null) {
      return raw.values();
    }
    if (raw.value() instanceof Collection<?> collection) {
      return collection;
    }
    throw new IllegalArgumentException(raw.operator().value() + " requires array values");
  }

  private List<Object> normalizeList(FieldType fieldType, Collection<?> rawValues) {
    if (rawValues == null || rawValues.isEmpty()) {
      throw new IllegalArgumentException("dynamic filter values must not be empty");
    }

    if (rawValues.size() > MAX_LIST_VALUES) {
      throw new IllegalArgumentException("too many dynamic filter values");
    }

    LinkedHashSet<Object> deduplicated = new LinkedHashSet<>();
    for (Object value : rawValues) {
      deduplicated.add(normalizeScalar(fieldType, value));
    }
    return List.copyOf(deduplicated);
  }

  private Object normalizeScalar(FieldType fieldType, Object raw) {
    if (raw == null) {
      throw new IllegalArgumentException("dynamic filter value is required");
    }

    return switch (fieldType) {
      case NUMBER -> normalizeNumber(raw);
      case DATE -> normalizeDate(raw);
      case DATETIME -> normalizeDatetime(raw);
      case BOOLEAN -> normalizeBoolean(raw);
      case TEXT, TEXTAREA, SELECT, MULTI_SELECT, USER -> normalizeStrictText(raw);
    };
  }

  private String normalizeNumber(Object raw) {
    if (!(raw instanceof Number) && !(raw instanceof String)) {
      throw new IllegalArgumentException("number filter value must be number or numeric string");
    }

    String value = String.valueOf(raw).trim();
    if (value.isBlank()) {
      throw new IllegalArgumentException("number filter value must not be blank");
    }

    try {
      return new BigDecimal(value).stripTrailingZeros().toPlainString();
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("invalid number filter value: " + raw, ex);
    }
  }

  private String normalizeDate(Object raw) {
    if (!(raw instanceof String text)) {
      throw new IllegalArgumentException("date filter value must be string");
    }

    try {
      return LocalDate.parse(text).toString();
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException(
          "date filter value must be ISO date (e.g. 2026-01-15)", ex);
    }
  }

  private String normalizeDatetime(Object raw) {
    if (!(raw instanceof String text)) {
      throw new IllegalArgumentException("datetime filter value must be string");
    }

    try {
      return OffsetDateTime.parse(text).toString();
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException(
          "datetime filter value must be ISO offset datetime (e.g. 2026-01-15T08:30:00+08:00)", ex);
    }
  }

  private String normalizeBoolean(Object raw) {
    if (raw instanceof Boolean bool) {
      return Boolean.toString(bool);
    }

    if (raw instanceof String text
        && ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text))) {
      return text.toLowerCase();
    }

    throw new IllegalArgumentException("boolean filter value must be boolean");
  }

  private String normalizeStrictText(Object raw) {
    if (!(raw instanceof String text)) {
      throw new IllegalArgumentException(
          "text filter value must be string, got: " + raw.getClass().getSimpleName());
    }

    String normalized = text.trim();
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("dynamic filter value must not be blank");
    }
    if (normalized.length() > MAX_TEXT_LENGTH) {
      throw new IllegalArgumentException("dynamic filter value is too long");
    }
    return normalized;
  }

  private void requireAscendingRange(FieldType type, List<Object> values) {
    String first = String.valueOf(values.get(0));
    String second = String.valueOf(values.get(1));

    boolean descending =
        switch (type) {
          case NUMBER -> new BigDecimal(first).compareTo(new BigDecimal(second)) > 0;
          case DATE -> LocalDate.parse(first).isAfter(LocalDate.parse(second));
          case DATETIME ->
              OffsetDateTime.parse(first)
                  .toInstant()
                  .isAfter(OffsetDateTime.parse(second).toInstant());
          default ->
              throw new IllegalArgumentException("between is not supported for " + type.value());
        };

    if (descending) {
      throw new IllegalArgumentException("between lower bound must not exceed upper bound");
    }
  }

  private record NormalizedValue(Object value, List<Object> values) {}
}
