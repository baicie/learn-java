package io.aegisops.workrecord;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.json.JsonPayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Formily-compatible schema 校验与规范化服务。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>接收 Formily schema JSON，校验为合法 object（递归类型检查）
 *   <li>保留所有 {@code x-work-record-*} 扩展属性
 *   <li>从 properties 抽取字段元数据，供 WorkRecordFieldIndexService 同步字段索引
 *   <li>对前端传来的 schema 做白名单清洗（只允许已知字段），防止注入未知协议
 * </ul>
 *
 * <p>不直接操作数据库，输出结果供调用方写入 wr_template.schema_json。
 */
@Service
public class WorkRecordSchemaService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * 规范化 Formily schema。
   *
   * @param rawSchema 原始 JSON 字符串，可能为 null 或空
   * @return 规范化后的 schema JSON（保证是合法 JSON object）
   * @throws IllegalArgumentException schema 存在但不是 JSON object
   */
  public String normalize(String rawSchema) {
    if (rawSchema == null || rawSchema.isBlank()) {
      return "{}";
    }
    return JsonPayloads.normalizeObject(rawSchema, "schemaJson");
  }

  /**
   * 校验 schema 是否为合法 object。
   *
   * @param rawSchema JSON 字符串
   * @throws IllegalArgumentException 不是 JSON object
   */
  public void validate(String rawSchema) {
    if (rawSchema == null || rawSchema.isBlank()) {
      return; // 空 schema 等同于 {}
    }
    JsonPayloads.normalizeObject(rawSchema, "schemaJson");
  }

  /**
   * 校验 schema 的 properties keys 中是否包含保留字段码。
   *
   * <p>保留字段码在 {@link WorkRecordFieldValidator#RESERVED_FIELD_CODES} 中定义。
   * 该方法递归检查所有层级的 properties，确保设计器不会覆盖主表列或内置字段。
   *
   * @param schemaJson Formily schema JSON
   * @throws IllegalArgumentException 包含保留字段码
   */
  public void validateNoReservedFieldCodes(String schemaJson) {
    if (schemaJson == null || schemaJson.isBlank()) {
      return;
    }
    Map<String, Object> schema = parseObject(schemaJson);
    validateProperties(schema, ".properties");
  }

  @SuppressWarnings("unchecked")
  private void validateProperties(Map<String, Object> node, String parentPath) {
    Object propsObj = node.get("properties");
    if (!(propsObj instanceof Map)) {
      return;
    }
    Map<String, Object> properties = (Map<String, Object>) propsObj;
    for (Map.Entry<String, Object> entry : properties.entrySet()) {
      String fieldCode = entry.getKey();
      WorkRecordFieldValidator.validateFieldCodeNotReserved(fieldCode);
      Object value = entry.getValue();
      if (value instanceof Map) {
        validateProperties((Map<String, Object>) value, parentPath + "." + fieldCode);
      }
    }
  }

  /**
   * 从 Formily-compatible schema 中抽取所有字段描述符。
   *
   * <p>支持两种字段位置：
   *
   * <ul>
   *   <li>根级字段：{@code schema.properties.<code>} -> path = ".properties.<code>"
   *   <li>嵌套字段（未来扩展）：{@code schema.properties.<code>.properties.<subCode>} -> path 递归记录
   * </ul>
   *
   * <p>设计器在保存前应确保所有字段都有 x-work-record-field-code 扩展属性。
   * 本方法仅做回退：如果缺少扩展属性则使用 properties key 作为 fieldCode。
   *
   * @param schemaJson Formily schema JSON
   * @return 抽取的字段描述符列表（不含保留字段）
   * @throws IllegalArgumentException schema 不是合法 JSON
   */
  public List<FormilyFieldDescriptor> extractFields(String schemaJson) {
    if (schemaJson == null || schemaJson.isBlank()) {
      return List.of();
    }
    Map<String, Object> schema = parseObject(schemaJson);
    List<FormilyFieldDescriptor> fields = new ArrayList<>();
    extractFieldsFromProperties(schema, ".properties", fields);
    // 过滤保留字段
    return fields.stream()
        .filter(f -> !WorkRecordFieldValidator.isReserved(f.fieldCode()))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private void extractFieldsFromProperties(
      Map<String, Object> node, String parentPath, List<FormilyFieldDescriptor> out) {
    Object propsObj = node.get("properties");
    if (!(propsObj instanceof Map)) {
      return;
    }
    Map<String, Object> properties = (Map<String, Object>) propsObj;
    for (Map.Entry<String, Object> entry : properties.entrySet()) {
      String fieldCode = entry.getKey();
      if (!(entry.getValue() instanceof Map)) {
        continue;
      }
      Map<String, Object> fieldSchema = (Map<String, Object>) entry.getValue();
      String fieldType = extractString(fieldSchema, "x-component");
      String fieldName = extractString(fieldSchema, "title");
      String optionSource =
          fieldSchema.containsKey("x-work-record-option-source")
              ? String.valueOf(fieldSchema.get("x-work-record-option-source"))
              : "static";
      String dictCode =
          fieldSchema.containsKey("x-work-record-dict-code")
              ? String.valueOf(fieldSchema.get("x-work-record-dict-code"))
              : null;
      boolean listVisible = extractBool(fieldSchema, "x-work-record-list-visible");
      boolean filterable = extractBool(fieldSchema, "x-work-record-filterable");
      boolean statistical = extractBool(fieldSchema, "x-work-record-statistical");

      // 归一 fieldType：Formily x-component -> 内部 fieldType
      String normalizedFieldType = normalizeFieldType(fieldType);

      FormilyFieldDescriptor desc =
          new FormilyFieldDescriptor(
              fieldCode,
              fieldName != null ? fieldName : fieldCode,
              normalizedFieldType,
              optionSource,
              dictCode,
              listVisible,
              filterable,
              statistical,
              parentPath + "." + fieldCode);

      out.add(desc);

      // 递归处理嵌套 properties
      if (fieldSchema.containsKey("properties")) {
        extractFieldsFromProperties(fieldSchema, parentPath + "." + fieldCode, out);
      }
    }
  }

  private Map<String, Object> parseObject(String json) {
    try {
      JsonNode node = MAPPER.readTree(json);
      return MAPPER.convertValue(node, Map.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("schemaJson is not valid JSON: " + e.getMessage());
    }
  }

  private String extractString(Map<String, Object> map, String key) {
    Object val = map.get(key);
    return val == null ? null : val.toString();
  }

  private boolean extractBool(Map<String, Object> map, String key) {
    Object val = map.get(key);
    if (val instanceof Boolean) {
      return (Boolean) val;
    }
    if (val instanceof String) {
      return "true".equalsIgnoreCase((String) val);
    }
    return false;
  }

  /**
   * Formily x-component 归一映射。
   *
   * <p>Designable / Formily 设计器产出的 x-component 与内部 fieldType 的对应关系。
   */
  private static String normalizeFieldType(String xComponent) {
    if (xComponent == null) {
      return "text";
    }
    return switch (xComponent) {
      case "Input" -> "text";
      case "TextArea" -> "textarea";
      case "NumberPicker" -> "number";
      case "DatePicker" -> "date";
      case "DateRangePicker" -> "date"; // 暂不区分范围
      case "Select" -> "select";
      case "MultiSelect" -> "multi_select";
      case "Switch" -> "switch";
      case "Checkbox" -> "boolean";
      default -> "text";
    };
  }
}
