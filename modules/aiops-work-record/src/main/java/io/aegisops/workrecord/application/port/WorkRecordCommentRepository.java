package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.domain.model.WorkRecordComment;
import java.util.List;
import java.util.Optional;

public interface WorkRecordCommentRepository {
  List<WorkRecordComment> list(String tenantId, String recordId, int limit);

  Optional<WorkRecordComment> find(String tenantId, String id);

  WorkRecordComment create(
      String tenantId,
      String recordId,
      String content,
      List<String> mentionUserIds,
      String createdBy);

  boolean update(
      String tenantId, String id, String content, List<String> mentionUserIds, int expectedVersion);

  boolean softDelete(String tenantId, String id, int expectedVersion);
}
