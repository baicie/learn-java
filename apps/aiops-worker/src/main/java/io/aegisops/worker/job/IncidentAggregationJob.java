package io.aegisops.worker.job;

import io.aegisops.incident.IncidentAggregateRequest;
import io.aegisops.incident.IncidentService;
import io.aegisops.persistence.jooq.tables.records.AutomationOutboxRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Phase 2: roll new alert events into existing incidents or open fresh ones. */
@Component
public class IncidentAggregationJob implements OutboxJob {
  private static final Logger log = LoggerFactory.getLogger(IncidentAggregationJob.class);

  private final IncidentService incidentService;

  public IncidentAggregationJob(IncidentService incidentService) {
    this.incidentService = incidentService;
  }

  @Override
  public String jobName() {
    return "incident-aggregate";
  }

  @Override
  public JobResult handle(AutomationOutboxRecord row) {
    String tenantId = row.getTenantId();
    log.info("Running incident aggregation for tenant={}", tenantId);

    try {
      var result =
          incidentService.aggregateOpenAlerts(tenantId, new IncidentAggregateRequest(30, 1000));
      log.info(
          "Incident aggregation completed: scanned={}, groups={}, created={}, updated={}, linked={}",
          result.alertsScanned(),
          result.groups(),
          result.incidentsCreated(),
          result.incidentsUpdated(),
          result.alertsLinked());
      return JobResult.success();
    } catch (Exception e) {
      log.error("Incident aggregation failed for tenant={}", tenantId, e);
      return JobResult.failure(e.getMessage());
    }
  }
}
