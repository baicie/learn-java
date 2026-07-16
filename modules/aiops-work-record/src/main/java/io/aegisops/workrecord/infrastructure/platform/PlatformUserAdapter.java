package io.aegisops.workrecord.infrastructure.platform;

import io.aegisops.common.exception.NotFoundException;
import io.aegisops.user.UserAccount;
import io.aegisops.user.UserService;
import io.aegisops.workrecord.application.command.WorkRecordUserOption;
import io.aegisops.workrecord.application.port.WorkRecordUserPort;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PlatformUserAdapter implements WorkRecordUserPort {
  private final UserService userService;

  public PlatformUserAdapter(UserService userService) {
    this.userService = userService;
  }

  @Override
  public void requireActiveUser(String tenantId, String userId) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("userId is required");
    }

    UserAccount user;
    try {
      user = userService.getById(userId);
    } catch (NotFoundException ex) {
      throw new IllegalArgumentException("user not found: " + userId, ex);
    }

    if (!tenantId.equals(user.tenantId())) {
      throw new IllegalArgumentException("user does not belong to tenant: " + userId);
    }
    if (!"active".equalsIgnoreCase(user.status())) {
      throw new IllegalArgumentException("user is not active: " + userId);
    }
  }

  @Override
  public Map<String, String> displayNames(String tenantId, Collection<String> userIds) {
    return userService.displayNames(tenantId, userIds);
  }

  @Override
  public List<WorkRecordUserOption> activeOptions(String tenantId) {
    return userService.listByTenant(tenantId).stream()
        .filter(user -> tenantId.equals(user.tenantId()))
        .filter(user -> "active".equalsIgnoreCase(user.status()))
        .map(
            user ->
                new WorkRecordUserOption(
                    user.id(),
                    user.displayName() == null || user.displayName().isBlank()
                        ? user.username()
                        : user.displayName()))
        .sorted(Comparator.comparing(WorkRecordUserOption::label))
        .toList();
  }
}
