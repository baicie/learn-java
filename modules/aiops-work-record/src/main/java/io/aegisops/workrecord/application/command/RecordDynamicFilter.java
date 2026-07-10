package io.aegisops.workrecord.application.command;

public record RecordDynamicFilter(
    String fieldCode,
    String operator,
    Object value,
    String fieldType) {

  public RecordDynamicFilter(String fieldCode, String operator, Object value) {
    this(fieldCode, operator, value, null);
  }

  public RecordDynamicFilter normalized(String normalizedOperator, Object normalizedValue, String type) {
    return new RecordDynamicFilter(fieldCode, normalizedOperator, normalizedValue, type);
  }
}