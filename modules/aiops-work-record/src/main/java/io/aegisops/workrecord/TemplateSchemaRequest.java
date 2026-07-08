package io.aegisops.workrecord;

import java.util.List;

/**
 * 保存设计器 schema 的请求。
 *
 * @param schemaJson Formily-compatible schema JSON
 * @param designerJson portal 原生设计器的 UI 状态（面板展开、选中状态等），运行态不消费
 * @param fields 字段元数据列表（由设计器生成，供后端同步字段索引；运行时以 schemaJson 为准）
 */
public record TemplateSchemaRequest(
    String schemaJson,
    String designerJson,
    List<CreateFieldRequest> fields) {}
