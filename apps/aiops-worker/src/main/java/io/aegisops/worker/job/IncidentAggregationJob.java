package io.aegisops.worker.job;

import io.aegisops.persistence.jooq.tables.records.AutomationOutboxRecord;
import org.springframework.stereotype.Component;

/**
 * Phase 2 entry point: roll new alert events into existing incidents or open fresh ones.
 *
 * <p>MVP skeleton. The real {@code IncidentAggregator} logic lives in {@code
 * aiops-execution.IncidentAggregationService} and is migrated here in Phase 2.
 */
@Component
public class IncidentAggregationJob implements OutboxJob {

  @Override
  public String jobName() {
    return "incident-aggregate";
  }

  @Override
  public JobResult handle(AutomationOutboxRecord row) {
    return JobResult.success();
  }
}
