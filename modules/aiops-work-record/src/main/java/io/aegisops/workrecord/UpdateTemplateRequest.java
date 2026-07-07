package io.aegisops.workrecord;

/** 更新模板请求；所有字段可选，仅非 null 时生效。 */
public record UpdateTemplateRequest(
    String name, String description, Boolean enabled, String schemaJson) {}
