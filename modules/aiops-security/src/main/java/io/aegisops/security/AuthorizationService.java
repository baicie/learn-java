package io.aegisops.security;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationService {
  private final AuthorizationRepository repository;

  public AuthorizationService(AuthorizationRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public AuthorizationSnapshot resolve(String tenantId, String userId) {
    AuthorizationSnapshot snapshot = repository.findSnapshot(tenantId, userId);

    if (!snapshot.roles().isEmpty()) {
      return snapshot;
    }

    repository.migrateLegacyAssignments(tenantId, userId);

    return repository.findSnapshot(tenantId, userId);
  }

  @Transactional
  public void assignDefaultRole(String tenantId, String userId, String actor) {
    repository.assignRole(tenantId, userId, BuiltInRoleCodes.NORMAL_USER, actor);
  }

  @Transactional
  public void assignRole(String tenantId, String userId, String roleCode, String actor) {
    repository.assignRole(tenantId, userId, roleCode, actor);
  }
}
