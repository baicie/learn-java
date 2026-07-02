package io.aegisops.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceOrchestrationServiceTest {

  @Test
  void collectShouldRecordTaskLifecycle() {
    EvidenceCollector collector = org.mockito.Mockito.mock(EvidenceCollector.class);
    when(collector.collectorKey()).thenReturn("zabbix.metric-event");
    when(collector.supports(any())).thenReturn(true);
    when(collector.collect(any(), any(), any()))
        .thenReturn(new EvidenceCollectResponse("inc-1", 0, 0, 0, 0, 0, 0, 0, "ok"));

    EvidenceCollectionTaskRepository repository =
        org.mockito.Mockito.mock(EvidenceCollectionTaskRepository.class);
    when(repository.start(any(), any(), any(), any())).thenReturn("task-1");

    EvidenceOrchestrationService service =
        new EvidenceOrchestrationService(
            new EvidenceCollectorRegistry(List.of(collector)),
            repository,
            new ObjectMapper());

    service.collect("t1", "inc-1", null);

    verify(repository)
        .start(
            org.mockito.Mockito.eq("t1"),
            org.mockito.Mockito.eq("inc-1"),
            org.mockito.Mockito.eq("zabbix.metric-event"),
            any());
    verify(repository).complete(org.mockito.Mockito.eq("t1"), org.mockito.Mockito.eq("task-1"), any());
  }

  @Test
  void collectShouldRecordFailedTask() {
    EvidenceCollector collector = org.mockito.Mockito.mock(EvidenceCollector.class);
    when(collector.collectorKey()).thenReturn("zabbix.metric-event");
    when(collector.supports(any())).thenReturn(true);
    when(collector.collect(any(), any(), any())).thenThrow(new RuntimeException("boom"));

    EvidenceCollectionTaskRepository repository =
        org.mockito.Mockito.mock(EvidenceCollectionTaskRepository.class);
    when(repository.start(any(), any(), any(), any())).thenReturn("task-1");

    EvidenceOrchestrationService service =
        new EvidenceOrchestrationService(
            new EvidenceCollectorRegistry(List.of(collector)),
            repository,
            new ObjectMapper());

    assertThatThrownBy(() -> service.collect("t1", "inc-1", null))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("boom");

    verify(repository).fail(org.mockito.Mockito.eq("t1"), org.mockito.Mockito.eq("task-1"), any());
  }

  @Test
  void collectShouldRejectMissingTenant() {
    EvidenceOrchestrationService service =
        new EvidenceOrchestrationService(
            new EvidenceCollectorRegistry(List.of()),
            org.mockito.Mockito.mock(EvidenceCollectionTaskRepository.class),
            new ObjectMapper());

    assertThatThrownBy(() -> service.collect(null, "inc-1", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenant");
  }

  @Test
  void collectShouldRejectMissingIncident() {
    EvidenceOrchestrationService service =
        new EvidenceOrchestrationService(
            new EvidenceCollectorRegistry(List.of()),
            org.mockito.Mockito.mock(EvidenceCollectionTaskRepository.class),
            new ObjectMapper());

    assertThatThrownBy(() -> service.collect("t1", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("incident");
  }
}
