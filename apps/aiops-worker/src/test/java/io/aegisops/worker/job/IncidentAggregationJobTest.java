package io.aegisops.worker.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import io.aegisops.incident.IncidentAggregateRequest;
import io.aegisops.incident.IncidentService;
import io.aegisops.incident.IncidentAggregationResponse;
import io.aegisops.persistence.jooq.tables.records.AutomationOutboxRecord;
import org.junit.jupiter.api.Test;

class IncidentAggregationJobTest {

  @Test
  void handle_shouldDelegateToIncidentService() {
    IncidentService service = org.mockito.Mockito.mock(IncidentService.class);
    when(service.aggregateOpenAlerts(any(), any()))
        .thenReturn(new IncidentAggregationResponse(0, 0, 0, 0, 0));

    IncidentAggregationJob job = new IncidentAggregationJob(service);
    AutomationOutboxRecord row = new AutomationOutboxRecord();
    row.setTenantId("t1");

    job.handle(row);

    verify(service).aggregateOpenAlerts(eq("t1"), any(IncidentAggregateRequest.class));
  }
}
