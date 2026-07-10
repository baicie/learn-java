package io.aegisops.workrecord.infrastructure.platform;

import io.aegisops.common.exception.NotFoundException;
import io.aegisops.user.UserAccount;
import io.aegisops.user.UserService;
import io.aegisops.workrecord.application.port.WorkRecordUserPort;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
    if (userIds == null || userIds.isEmpty()) {
      return Map.of();
    }

    Set<String> requested = new HashSet<>(userIds);
    Map<String, String> result = new LinkedHashMap<>();

    for (UserAccount user : userService.listByTenant(tenantId)) {
      if (!requested.contains(user.id())) {
        continue;
      }

      String displayName =
          user.displayName() == null || user.displayName().isBlank()
              ? user.username()
              : user.displayName();

      result.put(user.id(), displayName);
    }

    return Map.copyOf(result);
  }
}