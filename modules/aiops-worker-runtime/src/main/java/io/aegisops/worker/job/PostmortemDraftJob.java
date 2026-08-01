package io.aegisops.worker.job;

import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import org.springframework.stereotype.Component;

/**
 * Phase 6 entry point: generate the postmortem draft after an execution run completes.
 *
 * <p>MVP skeleton. The real {@code PostmortemDraftBuilder} lives in {@code
 * aiops-execution.PostmortemDraftService} and is migrated here in Phase 6.
 */
@Component
public class PostmortemDraftJob implements OutboxJob {

  @Override
  public String jobName() {
    return "postmortem-draft";
  }

  @Override
  public JobResult handle(AutomationOutboxRecord row) {
    return JobResult.success();
  }
}
