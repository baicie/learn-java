package io.aegisops.platform.iam.repository;

import io.aegisops.platform.iam.domain.PlatformUser;
import io.aegisops.platform.iam.domain.PlatformUserPage;
import io.aegisops.platform.iam.domain.PlatformUserQuery;
import io.aegisops.platform.iam.domain.PlatformUserStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PlatformUserRepository {

  PlatformUserPage search(PlatformUserQuery query);

  Optional<PlatformUser> findById(String id);

  boolean existsByUsername(String tenantId, String username);

  Optional<PlatformUser> insert(
      String id,
      String tenantId,
      String username,
      String displayName,
      String email,
      String passwordHash,
      PlatformUserStatus status,
      OffsetDateTime now);

  void replaceRoles(String tenantId, String userId, List<String> roleCodes, OffsetDateTime now);

  void update(
      String userId,
      String displayName,
      String email,
      OffsetDateTime now);

  int updateStatus(
      String userId,
      PlatformUserStatus status,
      OffsetDateTime lockedUntil,
      OffsetDateTime now,
      int expectedVersion);

  void recordLogin(
      String userId, OffsetDateTime lastLoginAt, int success, OffsetDateTime now);

  int deleteRolesForRole(String tenantId, String roleCode);
}