package io.aegisops.workrecord.application.port;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.domain.model.RelationType;

public interface RelationTargetPort {
  ResolvedTarget resolve(
      String tenantId, RelationType type, String targetId, UserPrincipal principal);

  record ResolvedTarget(String id, String title, String status, String snapshotJson) {}
}
