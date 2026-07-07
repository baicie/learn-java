package io.aegisops.platform.dictionary;

public record CreateDictTypeRequest(
    String dictCode, String dictName, String description, Integer sortOrder, Boolean enabled) {}
