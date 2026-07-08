package io.aegisops.workrecord.api.dto;

/**
 * 更新模板请求；所有字段可选，仅非 null 时生效。
 *
 * <p>{@code designerJson} 允许为 null：portal 设计器在保存 schema 时一并覆盖设计器 UI 状态， 但若只改名称/描述/启用状态则不更新
 * designerJson。
 */
public record UpdateTemplateRequest(
    String name, String description, Boolean enabled, String schemaJson, String designerJson) {}
