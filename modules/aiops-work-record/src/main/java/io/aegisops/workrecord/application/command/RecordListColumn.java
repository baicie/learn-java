package io.aegisops.workrecord.application.command;

public record RecordListColumn(
    String key,
    String title,
    String source,
    String fieldCode,
    String fieldType,
    boolean visibleByDefault,
    boolean sortable,
    int sortOrder) {}