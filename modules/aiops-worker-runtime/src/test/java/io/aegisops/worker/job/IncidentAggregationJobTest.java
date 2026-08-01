package io.aegisops.worker.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.incident.IncidentAggregationResponse;
import io.aegisops.incident.IncidentService;
import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import org.junit.jupiter.api.Test;

class IncidentAggregationJobTest {

  @Test
  void handle_shouldDelegateToIncidentService() {
    IncidentService service = org.mockito.Mockito.mock(IncidentService.class);
    when(service.aggregateUnlinkedAlerts(anyString(), anyInt()))
        .thenReturn(new IncidentAggregationResponse(0, 0, 0, 0, 0));

    IncidentAggregationJob job = new IncidentAggregationJob(service);
    AutomationOutboxRecord row = new AutomationOutboxRecord();
    row.setTenantId("t1");

    JobResult result = job.handle(row);

    verify(service).aggregateUnlinkedAlerts("t1", 1000);
    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  void handle_shouldReturnFailureWhenTenantMissing() {
    IncidentService service = org.mockito.Mockito.mock(IncidentService.class);
    IncidentAggregationJob job = new IncidentAggregationJob(service);
    AutomationOutboxRecord row = new AutomationOutboxRecord();

    JobResult result = job.handle(row);

    assertThat(result.isSuccess()).isFalse();
  }
}
