package io.aegisops.workrecord.application.port;

public interface WorkRecordDictionaryPort {
  void requireEnabledItem(String tenantId, String dictCode, String itemValue);
}
