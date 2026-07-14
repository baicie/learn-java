package io.aegisops.platform.iam.repository;

import io.aegisops.platform.iam.domain.PermissionDefinition;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PermissionDefinitionRepository {

  List<PermissionDefinition> list(String moduleCode);

  List<PermissionDefinition> listAll();

  Optional<PermissionDefinition> findByCode(String code);

  Set<String> codesForRole(String tenantId, String roleCode);
}
