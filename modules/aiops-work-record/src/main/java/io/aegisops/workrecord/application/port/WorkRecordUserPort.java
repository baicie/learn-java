package io.aegisops.workrecord.application.port;

import java.util.Collection;
import java.util.Map;

public interface WorkRecordUserPort {
  void requireActiveUser(String tenantId, String userId);

  Map<String, String> displayNames(String tenantId, Collection<String> userIds);
}
