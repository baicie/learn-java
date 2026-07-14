package io.aegisops.platform.iam.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record PlatformUser(
    String id,
    String tenantId,
    String username,
    String displayName,
    String email,
    PlatformUserStatus status,
    List<RoleRef> roles,
    Map<String, String> dataScopes,
    OffsetDateTime lastLoginAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    int rowVersion) {

  public record RoleRef(String code, String name) {}
}
