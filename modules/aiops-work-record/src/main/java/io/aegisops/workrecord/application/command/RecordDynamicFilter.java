package io.aegisops.workrecord.application.command;

public record RecordDynamicFilter(String fieldCode, String operator, Object value) {}