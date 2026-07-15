package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.domain.model.FieldPolicy;
import java.util.List;

public interface FieldPolicyRepository {
  void replaceForVersion(String tenantId, String versionId, List<FieldPolicy> policies);

  List<FieldPolicy> listByVersion(String tenantId, String versionId);
}
