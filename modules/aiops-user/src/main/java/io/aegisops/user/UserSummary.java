package io.aegisops.user;

import java.time.OffsetDateTime;

public record UserSummary(
    String id,
    String tenantId,
    String username,
    String displayName,
    String email,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static UserSummary from(UserAccount user) {
    return new UserSummary(
        user.id(),
        user.tenantId(),
        user.username(),
        user.displayName(),
        user.email(),
        user.status(),
        user.createdAt(),
        user.updatedAt());
  }
}
