package io.aegisops.platform.dictionary;

/** 更新字典类型请求；所有字段为可选，仅非 null 时生效。 */
public record UpdateDictTypeRequest(
    String dictName, String description, Boolean enabled, Integer sortOrder) {}
