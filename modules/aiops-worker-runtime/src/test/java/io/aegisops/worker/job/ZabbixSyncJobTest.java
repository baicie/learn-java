package io.aegisops.worker.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.datasource.application.DataSourceSyncApplicationService;
import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import java.time.OffsetDateTime;
import org.jooq.JSONB;
import org.junit.jupiter.api.Test;

class ZabbixSyncJobTest {
  private final DataSourceSyncApplicationService service =
      mock(DataSourceSyncApplicationService.class);
  private final ZabbixSyncJob job = new ZabbixSyncJob(service, new ObjectMapper());

  @Test
  void delegatesValidatedPayloadToDatasourceApplicationService() {
    AutomationOutboxRecord row = row("tenant-1");

    JobResult result = job.handle(row);

    assertThat(result.isSuccess()).isTrue();
    verify(service).execute("tenant-1", "ds-1", "sync-1", "claim-a");
  }

  @Test
  void renewsDatasourceRunWithTheOutboxOwnerToken() {
    AutomationOutboxRecord row = row("tenant-1");
    OffsetDateTime leaseUntil = OffsetDateTime.parse("2026-07-27T10:20:30Z");

    job.renewLease(row, leaseUntil);

    verify(service).renewLease("tenant-1", "ds-1", "sync-1", "claim-a", leaseUntil);
  }

  @Test
  void rejectsTenantMismatch() {
    AutomationOutboxRecord row = row("another-tenant");

    JobResult result = job.handle(row);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.reason()).isEqualTo("TENANT_MISMATCH");
    verifyNoInteractions(service);
  }

  private AutomationOutboxRecord row(String tenantId) {
    AutomationOutboxRecord row = new AutomationOutboxRecord();
    row.setTenantId(tenantId);
    row.setClaimToken("claim-a");
    row.setPayload(
        JSONB.valueOf(
            "{\"tenantId\":\"tenant-1\",\"datasourceId\":\"ds-1\",\"runId\":\"sync-1\"}"));
    return row;
  }
}
