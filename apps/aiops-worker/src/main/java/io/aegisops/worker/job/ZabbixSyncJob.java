package io.aegisops.worker.job;

import io.aegisops.persistence.jooq.tables.records.AutomationOutboxRecord;
import org.springframework.stereotype.Component;

/**
 * Phase 1 entry point: pull Zabbix hosts / triggers / problems and reconcile assets and alert
 * events.
 *
 * <p>MVP skeleton: the real implementation lives behind the {@code aiops-zabbix-adapter} module and
 * is wired in once Phase 1 begins. For now the job is a no-op so the {@code OutboxPoller} can
 * validate the dispatch plumbing end-to-end.
 */
@Component
public class ZabbixSyncJob implements OutboxJob {

  @Override
  public String jobName() {
    return "zabbix-sync";
  }

  @Override
  public JobResult handle(AutomationOutboxRecord row) {
    // Real implementation arrives with Phase 1 (ZabbixSyncService inside aiops-zabbix-adapter).
    return JobResult.success();
  }
}
