package io.aegisops.worker.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.aegisops.common.time.TimeProvider;
import io.aegisops.datasource.application.ZabbixSyncDispatchApplicationService;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ZabbixSyncScheduleJobTest {
  @Test
  void dispatchesDueSourcesUsingConfiguredCadenceAndBatchSize() {
    ZabbixSyncDispatchApplicationService service = mock(ZabbixSyncDispatchApplicationService.class);
    TimeProvider timeProvider = mock(TimeProvider.class);
    OffsetDateTime now = OffsetDateTime.parse("2026-07-27T10:15:30Z");
    when(timeProvider.now()).thenReturn(now);
    when(service.dispatchDue(now, Duration.ofMinutes(1), 25)).thenReturn(2);
    ZabbixSyncScheduleJob job =
        new ZabbixSyncScheduleJob(
            service, timeProvider, new ZabbixSyncScheduleProperties(true, 5000L, 60000L, 25));

    assertThat(job.tick()).isEqualTo(2);

    verify(service).dispatchDue(now, Duration.ofMinutes(1), 25);
  }

  @Test
  void disabledJobDoesNotReadTimeOrDispatch() {
    ZabbixSyncDispatchApplicationService service = mock(ZabbixSyncDispatchApplicationService.class);
    TimeProvider timeProvider = mock(TimeProvider.class);
    ZabbixSyncScheduleJob job =
        new ZabbixSyncScheduleJob(
            service, timeProvider, new ZabbixSyncScheduleProperties(false, 5000L, 60000L, 25));

    assertThat(job.tick()).isZero();

    verifyNoInteractions(service, timeProvider);
  }
}
