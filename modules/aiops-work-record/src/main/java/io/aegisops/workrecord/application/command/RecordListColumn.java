package io.aegisops.workrecord.application.command;

public record RecordListColumn(
    String key,
    String title,
    String source,
    String fieldCode,
    String fieldType,
    String optionSource,
    String dictCode,
    String optionsJson,
    boolean visibleByDefault,
    boolean sortable,
    boolean exportable,
    int sortOrder) {}