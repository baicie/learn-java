package io.aegisops.datasource.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.common.outbox.OutboxMessage;
import io.aegisops.common.outbox.OutboxWriter;
import io.aegisops.datasource.application.port.ZabbixSyncDispatchStore;
import io.aegisops.datasource.application.port.ZabbixSyncDispatchStore.SyncTarget;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ZabbixSyncDispatchApplicationServiceTest {
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-27T10:15:30Z");

  @Test
  void dispatchesDueTargetsWithTenantSafePayloadsAndBoundedTimeBucketKeys() {
    ZabbixSyncDispatchStore store = mock(ZabbixSyncDispatchStore.class);
    OutboxWriter outbox = mock(OutboxWriter.class);
    when(store.lockDueTargets(NOW.minusMinutes(1), 10))
        .thenReturn(
            List.of(new SyncTarget("tenant-a", "ds-a"), new SyncTarget("tenant-b", "ds-b")));
    when(store.createScheduledRun(any(), any(), any(), eq(NOW))).thenReturn(true);
    ZabbixSyncDispatchApplicationService service =
        new ZabbixSyncDispatchApplicationService(store, outbox);

    int dispatched = service.dispatchDue(NOW, Duration.ofMinutes(1), 10);

    assertThat(dispatched).isEqualTo(2);
    ArgumentCaptor<OutboxMessage> messages = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox, org.mockito.Mockito.times(2)).enqueue(messages.capture());
    assertThat(messages.getAllValues())
        .allSatisfy(
            message -> {
              assertThat(message.targetApp()).isEqualTo("worker");
              assertThat(message.jobName()).isEqualTo("zabbix-sync");
              assertThat(message.payload().get("tenantId")).isEqualTo(message.tenantId());
              assertThat(message.payload().get("runId")).isNotNull();
              assertThat(message.idempotencyKey()).startsWith("zabbix-sync-scheduled:");
              assertThat(message.idempotencyKey()).hasSizeLessThanOrEqualTo(128);
            });
    assertThat(messages.getAllValues())
        .extracting(message -> message.payload().get("datasourceId"))
        .containsExactly("ds-a", "ds-b");
    verify(store).failPendingRunsWithTerminalDispatchFailure("tenant-a", "ds-a");
    verify(store).failPendingRunsWithTerminalDispatchFailure("tenant-b", "ds-b");
  }

  @Test
  void skipsOutboxWhenTheTimeBucketRunAlreadyExists() {
    ZabbixSyncDispatchStore store = mock(ZabbixSyncDispatchStore.class);
    OutboxWriter outbox = mock(OutboxWriter.class);
    when(store.lockDueTargets(NOW.minusMinutes(1), 10))
        .thenReturn(List.of(new SyncTarget("tenant-a", "ds-a")));
    when(store.createScheduledRun(any(), eq("tenant-a"), eq("ds-a"), eq(NOW))).thenReturn(false);
    ZabbixSyncDispatchApplicationService service =
        new ZabbixSyncDispatchApplicationService(store, outbox);

    assertThat(service.dispatchDue(NOW, Duration.ofMinutes(1), 10)).isZero();

    verify(store).failPendingRunsWithTerminalDispatchFailure("tenant-a", "ds-a");
    verify(outbox, never()).enqueue(any(OutboxMessage.class));
  }

  @Test
  void changesIdempotencyKeyAfterTheCadenceBucketChanges() {
    ZabbixSyncDispatchStore store = mock(ZabbixSyncDispatchStore.class);
    OutboxWriter outbox = mock(OutboxWriter.class);
    when(store.lockDueTargets(any(), eq(10)))
        .thenReturn(List.of(new SyncTarget("tenant-a", "ds-a")));
    when(store.createScheduledRun(any(), any(), any(), any())).thenReturn(true);
    ZabbixSyncDispatchApplicationService service =
        new ZabbixSyncDispatchApplicationService(store, outbox);

    service.dispatchDue(NOW, Duration.ofMinutes(1), 10);
    service.dispatchDue(NOW.plusMinutes(1), Duration.ofMinutes(1), 10);

    ArgumentCaptor<OutboxMessage> messages = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox, org.mockito.Mockito.times(2)).enqueue(messages.capture());
    assertThat(messages.getAllValues().get(0).idempotencyKey())
        .isNotEqualTo(messages.getAllValues().get(1).idempotencyKey());
    assertThat(messages.getAllValues().get(0).payload().get("runId"))
        .isNotEqualTo(messages.getAllValues().get(1).payload().get("runId"));
  }

  @Test
  void keepsDifferentCadencesInDifferentIdempotencyNamespaces() {
    ZabbixSyncDispatchStore store = mock(ZabbixSyncDispatchStore.class);
    OutboxWriter outbox = mock(OutboxWriter.class);
    OffsetDateTime sameBucketOrdinal = OffsetDateTime.parse("1970-01-01T00:01:00.001Z");
    when(store.lockDueTargets(any(), eq(10)))
        .thenReturn(List.of(new SyncTarget("tenant-a", "ds-a")));
    when(store.createScheduledRun(any(), any(), any(), any())).thenReturn(true);
    ZabbixSyncDispatchApplicationService service =
        new ZabbixSyncDispatchApplicationService(store, outbox);

    service.dispatchDue(sameBucketOrdinal, Duration.ofMillis(60000), 10);
    service.dispatchDue(sameBucketOrdinal, Duration.ofMillis(60001), 10);

    ArgumentCaptor<OutboxMessage> messages = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox, org.mockito.Mockito.times(2)).enqueue(messages.capture());
    assertThat(messages.getAllValues().get(0).idempotencyKey())
        .isNotEqualTo(messages.getAllValues().get(1).idempotencyKey());
    assertThat(messages.getAllValues().get(0).payload().get("runId"))
        .isNotEqualTo(messages.getAllValues().get(1).payload().get("runId"));
  }
}
