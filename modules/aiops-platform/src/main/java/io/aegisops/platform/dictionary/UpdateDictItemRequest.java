package io.aegisops.platform.dictionary;

/** 更新字典项请求；所有字段为可选，仅非 null 时生效。 */
public record UpdateDictItemRequest(
    String itemLabel,
    String color,
    String icon,
    String description,
    Boolean enabled,
    Integer sortOrder) {}
