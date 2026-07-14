package io.aegisops.platform.iam.service;

import io.aegisops.platform.iam.domain.PermissionDefinition;
import io.aegisops.platform.iam.repository.PermissionDefinitionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformPermissionService {

  private final PermissionDefinitionRepository permissions;

  public PlatformPermissionService(PermissionDefinitionRepository permissions) {
    this.permissions = permissions;
  }

  @Transactional(readOnly = true)
  public List<PermissionDefinition> list() {
    return permissions.list(null);
  }
}
