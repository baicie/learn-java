package io.aegisops.worker.job;

import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import org.springframework.stereotype.Component;

/**
 * Phase 3/4 entry point: evaluate RCA rules and trigger AI diagnosis.
 *
 * <p>MVP skeleton. Phase 3 plugs in the rule-based {@code RcaEngine}, Phase 4 the {@code
 * DiagnosisOrchestrator}.
 */
@Component
public class RcaAndDiagnosisJob implements OutboxJob {

  @Override
  public String jobName() {
    return "rca-diagnosis";
  }

  @Override
  public JobResult handle(AutomationOutboxRecord row) {
    return JobResult.success();
  }
}
