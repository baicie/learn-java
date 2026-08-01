package io.aegisops.workrecord.application.service;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.CreateRecordCommand;
import io.aegisops.workrecord.application.port.AsyncJobItemRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "aiops.runtime", name = "app", havingValue = "app")
public class IdempotentImportRowService {
  private final AsyncJobItemRepository items;
  private final WorkRecordService records;
  private final Clock clock;

  public IdempotentImportRowService(
      AsyncJobItemRepository items,
      WorkRecordService records,
      @Qualifier("workRecordClock") Clock clock) {
    this.items = items;
    this.records = records;
    this.clock = clock;
  }

  @Transactional
  public void importOnce(
      String tenantId,
      String jobId,
      int rowNumber,
      CreateRecordCommand command,
      UserPrincipal principal) {
    String itemKey = "row:" + rowNumber;
    OffsetDateTime now = OffsetDateTime.now(clock);
    if (!items.begin(tenantId, jobId, itemKey, rowNumber, now)) {
      return;
    }
    var record = records.create(tenantId, command, principal);
    if (!items.succeed(tenantId, jobId, itemKey, record.id(), now)) {
      throw new IllegalStateException("import row idempotency state changed");
    }
  }
}
