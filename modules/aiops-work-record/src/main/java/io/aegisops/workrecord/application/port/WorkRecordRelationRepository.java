package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.domain.model.RelationType;
import io.aegisops.workrecord.domain.model.WorkRecordRelation;
import java.util.List;

public interface WorkRecordRelationRepository {
  List<WorkRecordRelation> list(String tenantId, String recordId);

  WorkRecordRelation create(
      String tenantId,
      String recordId,
      RelationType type,
      RelationTargetPort.ResolvedTarget target,
      String createdBy);

  boolean delete(String tenantId, String recordId, String id);
}
