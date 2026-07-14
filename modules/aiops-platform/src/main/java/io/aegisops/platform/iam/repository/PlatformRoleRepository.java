package io.aegisops.platform.iam.repository;

import io.aegisops.platform.iam.domain.PlatformRole;
import io.aegisops.platform.iam.domain.PlatformRoleDetail;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PlatformRoleRepository {

  List<PlatformRole> list(boolean includeSystem);

  Optional<PlatformRoleDetail> findByCode(String code);

  int userCount(String code);

  Optional<PlatformRole> insert(
      String code, String name, String description, boolean system, boolean enabled);

  boolean exists(String code);

  int update(String code, String name, String description, boolean enabled, int expectedVersion);

  int replacePermissions(String code, Set<String> permissionCodes);

  int replaceDataScopes(String code, List<PlatformRole.RoleDataScope> scopes);

  void seedDefaultRolePermissions();
}
