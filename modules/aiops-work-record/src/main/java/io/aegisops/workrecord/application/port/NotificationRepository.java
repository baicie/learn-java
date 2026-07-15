package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.domain.model.WorkRecordNotification;
import java.util.List;

public interface NotificationRepository {
  boolean insertIfAbsent(WorkRecordNotification notification);

  List<WorkRecordNotification> listUnread(String tenantId, String userId, int limit);

  boolean markRead(String tenantId, String userId, String notificationId);
}
