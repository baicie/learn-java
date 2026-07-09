package io.aegisops.workrecord.domain.rule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作记录保存时的业务校验：
 *
 * <ul>
 *   <li>所有 JSON key 必须属于模板字段定义，未知字段被拒绝
 *   <li>必填字段必须存在且类型合法
 *   <li>禁用字段不能被写入
 *   <li>select/multi_select 必须为 string/array，且值必须在 options_json 定义中（static）或对应字典启用项（dict）
 *   <li>date 必须为 ISO LocalDate 格式，datetime 必须为 ISO OffsetDateTime 格式
 * </ul>
 */
public final class WorkRecordFieldValidator {

  /** 保留字段编码：与主表列、内置字段、动态校验内部键冲突，禁止作为 fieldCode 使用。 */
  public static final Set<String> RESERVED_FIELD_CODES =
      Set.of(
          "id",
          "tenant_id",
          "template_id",
          "title",
          "status",
          "owner_id",
          "creator_id",
          "record_time",
          "builtin_data_json",
          "custom_data_json",
          "created_at",
          "updated_at",
          "deleted_at");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private WorkRecordFieldValidator() {}

  /** 校验保留字段冲突。 */
  public static void validateFieldCodeNotReserved(String fieldCode) {
    if (isReserved(fieldCode)) {
      throw new IllegalArgumentException(
          "fieldCode '" + fieldCode + "' is reserved and cannot be used");
    }
  }

  /** 判断 fieldCode 是否为保留编码。 */
  public static boolean isReserved(String fieldCode) {
    return fieldCode != null && RESERVED_FIELD_CODES.contains(fieldCode);
  }

  /**
   * 校验 customDataJson 是否符合模板字段定义。
   *
   * <ol>
   *   <li>未知字段拒绝：输入 JSON 的每个 key 必须属于模板字段定义（__builtin__ 除外）
   *   <li>必填字段：enabled + required 的字段必须有值
   *   <li>禁用字段：enabled=false 的字段不能写入
   *   <li>类型校验：number/boolean/string/date/datetime 格式，select/multi_select 值在 options 中
   * </ol>
   *
   * @param templateFields 当前模板下所有字段（含禁用）
   * @param customDataJson 用户填写的 custom_data_json，已确保为合法 JSON 对象
   */
  public static void validateAgainstTemplate(
      List<WorkRecordField> templateFields, String customDataJson) {
    if (templateFields == null || templateFields.isEmpty()) {
      if (customDataJson == null || customDataJson.isBlank() || "{}".equals(customDataJson)) {
        return;
      }
      throw new IllegalArgumentException(
          "custom_data_json is not allowed when template has no fields");
    }

    Map<String, WorkRecordField> fieldByCode = new HashMap<>();
    for (WorkRecordField field : templateFields) {
      fieldByCode.put(field.fieldCode(), field);
    }

    JsonNode node;
    try {
      node = MAPPER.readTree(customDataJson == null ? "{}" : customDataJson);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("custom_data_json is not valid JSON: " + e.getMessage());
    }
    if (!node.isObject()) {
      throw new IllegalArgumentException("custom_data_json must be a JSON object");
    }

    // 1. 未知字段拒绝：遍历输入的 key，要求每个 key 都属于模板定义
    Set<String> knownCodes = fieldByCode.keySet();
    java.util.Iterator<String> fieldNames = node.fieldNames();
    while (fieldNames.hasNext()) {
      String inputKey = fieldNames.next();
      if (inputKey.equals("__builtin__")) {
        continue; // 内部容器暂不校验
      }
      if (!knownCodes.contains(inputKey)) {
        throw new IllegalArgumentException("field '" + inputKey + "' is not defined in template");
      }
    }

    // 2. 遍历已定义字段，逐字段校验
    for (WorkRecordField field : templateFields) {
      // 优先从 __builtin__ 读（兼容），否则从顶层读
      JsonNode value = node.get("__builtin__");
      value = (value != null) ? value.get(field.fieldCode()) : node.get(field.fieldCode());

      // 禁用字段不能写入
      if (!field.enabled() && value != null && !value.isNull()) {
        throw new IllegalArgumentException(
            "field '" + field.fieldCode() + "' is disabled and cannot accept values");
      }

      // 必填字段必须有值
      if (field.enabled()
          && field.required()
          && (value == null || value.isNull() || isBlankText(value))) {
        throw new IllegalArgumentException("field '" + field.fieldCode() + "' is required");
      }

      // 有值时才做类型和格式校验
      if (value != null && !value.isNull()) {
        validateValueAndFormat(field, value);
      }
    }
  }

  private static boolean isBlankText(JsonNode node) {
    return node.isTextual() && node.asText().isBlank();
  }

  /**
   * 校验值类型和格式：
   *
   * <ul>
   *   <li>number: 必须是 JSON number
   *   <li>switch: 必须是 JSON boolean
   *   <li>select: 必须是 string，值必须在 options_json（static）或字典启用项（dict）中
   *   <li>multi_select: 必须是 array，数组每个元素必须是 string 且在 options 中
   *   <li>date: 必须是 string 且为 ISO LocalDate 格式（YYYY-MM-DD）
   *   <li>datetime: 必须是 string 且为 ISO OffsetDateTime 格式
   *   <li>text/textarea/user: 必须是 string（user 暂只做类型校验，ID 格式由业务层保证）
   * </ul>
   */
  private static void validateValueAndFormat(WorkRecordField field, JsonNode value) {
    String code = field.fieldCode();
    String type = field.fieldType().value();

    switch (type) {
      case "number":
        if (!value.isNumber()) {
          throw new IllegalArgumentException("field '" + code + "' expects number");
        }
        break;

      case "switch":
      case "boolean":
        if (!value.isBoolean()) {
          throw new IllegalArgumentException("field '" + code + "' expects boolean");
        }
        break;

      case "select":
        if (!value.isTextual()) {
          throw new IllegalArgumentException("field '" + code + "' expects string value");
        }
        validateSelectValue(field, value.asText());
        break;

      case "multi_select":
        if (!value.isArray()) {
          throw new IllegalArgumentException("field '" + code + "' expects array value");
        }
        Set<String> allowed = allowedOptions(field);
        for (JsonNode item : value) {
          if (!item.isTextual()) {
            throw new IllegalArgumentException(
                "field '" + code + "' expects all elements to be strings");
          }
          String itemVal = item.asText();
          if (!allowed.isEmpty() && !allowed.contains(itemVal)) {
            throw new IllegalArgumentException(
                "field '" + code + "' value '" + itemVal + "' is not in allowed options");
          }
        }
        break;

      case "date":
        if (!value.isTextual()) {
          throw new IllegalArgumentException(
              "field '" + code + "' expects ISO date string (YYYY-MM-DD)");
        }
        try {
          LocalDate.parse(value.asText());
        } catch (Exception e) {
          throw new IllegalArgumentException(
              "field '" + code + "' expects ISO date string (YYYY-MM-DD), got: " + value.asText());
        }
        break;

      case "datetime":
        if (!value.isTextual()) {
          throw new IllegalArgumentException("field '" + code + "' expects ISO datetime string");
        }
        try {
          OffsetDateTime.parse(value.asText());
        } catch (Exception e) {
          throw new IllegalArgumentException(
              "field '"
                  + code
                  + "' expects ISO datetime string (e.g. 2026-07-07T10:30:00+08:00), got: "
                  + value.asText());
        }
        break;

      case "user":
        if (!value.isTextual()) {
          throw new IllegalArgumentException("field '" + code + "' expects user id (string)");
        }
        break;

      case "text":
      case "textarea":
        if (!value.isTextual() && !value.isNumber() && !value.isBoolean()) {
          throw new IllegalArgumentException("field '" + code + "' expects scalar value");
        }
        break;

      default:
        // 未知类型不校验
        break;
    }
  }

  /**
   * 校验 select 字段值在允许选项中。option_source=static 时解析 options_json 得到允许值集合； option_source=dict
   * 时暂只做非空校验（字典启用项由字典服务在保存时注入校验，后续完善）。
   */
  private static void validateSelectValue(WorkRecordField field, String value) {
    Set<String> allowed = allowedOptions(field);
    if (!allowed.isEmpty() && !allowed.contains(value)) {
      throw new IllegalArgumentException(
          "field '" + field.fieldCode() + "' value '" + value + "' is not in allowed options");
    }
  }

  /**
   * 解析 options_json，返回允许的值集合。若 options_json 为空/null/非法 JSON，返回空集合（放行）。目前仅支持 option_source=static
   * 的静态选项校验；option_source=dict 暂不做值校验（由字典服务在保存时注入约束）。
   */
  private static Set<String> allowedOptions(WorkRecordField field) {
    String optionsJson = field.optionsJson();
    if (optionsJson == null || optionsJson.isBlank()) {
      return Set.of();
    }
    try {
      JsonNode arr = MAPPER.readTree(optionsJson);
      if (!arr.isArray()) {
        return Set.of();
      }
      Set<String> allowed = new HashSet<>();
      for (JsonNode item : arr) {
        JsonNode valNode = item.get("value");
        if (valNode != null && valNode.isTextual()) {
          allowed.add(valNode.asText());
        }
      }
      return allowed;
    } catch (Exception e) {
      return Set.of(); // options_json 解析失败，放行（避免误伤）
    }
  }
}
