package io.aegisops.platform.dictionary;

public record CreateDictItemRequest(
    String itemLabel,
    String itemValue,
    String color,
    String icon,
    String description,
    Integer sortOrder,
    Boolean enabled,
    String extraJson) {}
