package io.aegisops.workrecord.application.service;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.NotificationRepository;
import io.aegisops.workrecord.domain.model.WorkRecordNotification;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordNotificationService {
  private final NotificationRepository notifications;

  public WorkRecordNotificationService(NotificationRepository notifications) {
    this.notifications = notifications;
  }

  public List<WorkRecordNotification> unread(String tenantId, int limit, UserPrincipal principal) {
    requireTenant(tenantId, principal);
    return notifications.listUnread(tenantId, principal.id(), Math.min(Math.max(limit, 1), 200));
  }

  public boolean markRead(String tenantId, String notificationId, UserPrincipal principal) {
    requireTenant(tenantId, principal);
    return notifications.markRead(tenantId, principal.id(), notificationId);
  }

  private static void requireTenant(String tenantId, UserPrincipal principal) {
    if (principal == null || !tenantId.equals(principal.tenantId())) {
      throw new AccessDeniedException("notification is outside current tenant");
    }
  }
}
