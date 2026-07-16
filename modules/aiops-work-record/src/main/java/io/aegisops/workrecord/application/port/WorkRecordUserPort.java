package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.application.command.WorkRecordUserOption;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface WorkRecordUserPort {
  void requireActiveUser(String tenantId, String userId);

  Map<String, String> displayNames(String tenantId, Collection<String> userIds);

  List<WorkRecordUserOption> activeOptions(String tenantId);
}
