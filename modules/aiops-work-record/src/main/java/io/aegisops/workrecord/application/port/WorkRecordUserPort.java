package io.aegisops.workrecord.application.port;

public interface WorkRecordUserPort {
  void requireActiveUser(String tenantId, String userId);
}