package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.application.service.WorkRecordHandoverService.CreateHandoverCommand;
import io.aegisops.workrecord.domain.model.HandoverStatus;
import io.aegisops.workrecord.domain.model.WorkRecordHandover;
import java.util.List;
import java.util.Optional;

public interface HandoverRepository {
  WorkRecordHandover create(String tenantId, CreateHandoverCommand command, String actorId);

  Optional<WorkRecordHandover> find(String tenantId, String handoverId);

  List<WorkRecordHandover> listForUser(String tenantId, String userId, int limit);

  boolean transition(
      String tenantId,
      String handoverId,
      HandoverStatus expected,
      HandoverStatus target,
      int expectedVersion);
}
