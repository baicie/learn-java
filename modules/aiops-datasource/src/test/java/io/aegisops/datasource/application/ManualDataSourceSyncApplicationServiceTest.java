package io.aegisops.datasource.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.aegisops.common.exception.AppException;
import io.aegisops.common.outbox.OutboxMessage;
import io.aegisops.common.outbox.OutboxWriter;
import io.aegisops.datasource.application.port.DataSourceSyncStore;
import io.aegisops.datasource.application.port.ZabbixSyncDispatchStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ManualDataSourceSyncApplicationServiceTest {
  @Test
  void rejectsZabbixManualSyncWhenAnInflightRunExists() {
    OutboxWriter outboxWriter = mock(OutboxWriter.class);
    DataSourceSyncStore syncStore = mock(DataSourceSyncStore.class);
    ZabbixSyncDispatchStore dispatchStore = mock(ZabbixSyncDispatchStore.class);
    when(syncStore.createZabbixManualRun(anyString(), anyString(), anyString())).thenReturn(false);
    ManualDataSourceSyncApplicationService service =
        new ManualDataSourceSyncApplicationService(outboxWriter, syncStore, dispatchStore);

    assertThatThrownBy(() -> service.start("tenant-1", "ds-1", "zabbix"))
        .isInstanceOfSatisfying(
            AppException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo("DATASOURCE_SYNC_ALREADY_RUNNING"));

    verify(dispatchStore).failPendingRunsWithTerminalDispatchFailure("tenant-1", "ds-1");
    verify(syncStore).createZabbixManualRun(anyString(), anyString(), anyString());
    verifyNoInteractions(outboxWriter);
  }

  @Test
  void createsKubernetesManualSyncWithoutApplyingZabbixLeaseRules() {
    OutboxWriter outboxWriter = mock(OutboxWriter.class);
    DataSourceSyncStore syncStore = mock(DataSourceSyncStore.class);
    ZabbixSyncDispatchStore dispatchStore = mock(ZabbixSyncDispatchStore.class);
    when(syncStore.createLegacyManualRun(anyString(), anyString(), anyString())).thenReturn(true);
    ManualDataSourceSyncApplicationService service =
        new ManualDataSourceSyncApplicationService(outboxWriter, syncStore, dispatchStore);

    var response = service.start("tenant-1", "ds-1", "kubernetes");

    assertThat(response.status()).isEqualTo("pending");
    verify(syncStore).createLegacyManualRun("tenant-1", "ds-1", response.runId());
    verify(dispatchStore, never())
        .failPendingRunsWithTerminalDispatchFailure(anyString(), anyString());
    ArgumentCaptor<OutboxMessage> message = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outboxWriter).enqueue(message.capture());
    assertThat(message.getValue().jobName()).isEqualTo("kubernetes-sync");
    assertThat(message.getValue().payload()).containsEntry("runId", response.runId());
  }
}
