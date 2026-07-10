package io.aegisops.workrecord.application.port;

import io.aegisops.common.api.PageResult;
import io.aegisops.workrecord.application.command.CreateRecordCommand;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.command.UpdateRecordCommand;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.util.List;
import java.util.Optional;

public interface WorkRecordRepository {
  WorkRecord create(String tenantId, CreateRecordCommand command, String creatorId);

  Optional<WorkRecord> find(String tenantId, String recordId);

  WorkRecord update(String tenantId, String recordId, UpdateRecordCommand command);

  WorkRecord softDelete(String tenantId, String recordId);

  PageResult<WorkRecord> page(String tenantId, RecordQuery query);

  List<WorkRecord> listForExport(String tenantId, RecordQuery query, int maxRows);
}
