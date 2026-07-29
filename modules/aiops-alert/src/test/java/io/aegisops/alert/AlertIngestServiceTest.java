package io.aegisops.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.outbox.OutboxMessage;
import io.aegisops.common.outbox.OutboxWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AlertIngestServiceTest {

  @Test
  void ingest_shouldPersistAlertAndEnqueueAggregation() {
    AlertIngestRepository repository = org.mockito.Mockito.mock(AlertIngestRepository.class);
    OutboxWriter outboxWriter = org.mockito.Mockito.mock(OutboxWriter.class);

    AlertIngestResult expected = new AlertIngestResult("al-1", true, "f", "g");
    when(repository.upsert(eq("t1"), any(), any(), any(), any(), any())).thenReturn(expected);

    AlertIngestService service =
        new AlertIngestService(
            new AlertFingerprintPolicy(), repository, new ObjectMapper(), outboxWriter);

    AlertIngestResult result =
        service.ingest(
            "t1",
            new AlertIngestRequest(
                "zabbix",
                "10001",
                "high",
                "CPU",
                null,
                "host-1",
                "HOST",
                "order-service",
                Map.of("env", "prod"),
                null,
                null,
                "open",
                Map.of("raw", true)));

    assertThat(result).isEqualTo(expected);
    verify(repository).upsert(eq("t1"), any(), any(), any(), any(), any());
    verify(outboxWriter).enqueueOrRequeueFailed(any(OutboxMessage.class));
  }

  @Test
  void ingest_shouldUseTrustedSourceIdentityWhenProvided() {
    AlertIngestRepository repository = org.mockito.Mockito.mock(AlertIngestRepository.class);
    OutboxWriter outboxWriter = org.mockito.Mockito.mock(OutboxWriter.class);
    AlertIngestRequest request =
        new AlertIngestRequest(
            "zabbix",
            "ds-1:event-1",
            "high",
            "CPU",
            null,
            "host-1",
            "service",
            "order-service",
            Map.of("env", "demo"),
            null,
            null,
            "open",
            Map.of());
    when(repository.upsert(
            eq("t1"),
            eq(request),
            eq("zabbix:ds-1:trigger-1"),
            eq("zabbix:ds-1:10084:order-service:demo:202607270100"),
            any(),
            any()))
        .thenReturn(new AlertIngestResult("al-1", true, "source-fp", "source-group"));
    AlertIngestService service =
        new AlertIngestService(
            new AlertFingerprintPolicy(), repository, new ObjectMapper(), outboxWriter);

    service.ingest(
        "t1",
        request,
        "zabbix:ds-1:trigger-1",
        "zabbix:ds-1:10084:order-service:demo:202607270100");

    verify(repository)
        .upsert(
            eq("t1"),
            eq(request),
            eq("zabbix:ds-1:trigger-1"),
            eq("zabbix:ds-1:10084:order-service:demo:202607270100"),
            any(),
            any());
  }

  @Test
  void ingest_shouldUseOneLifecycleIdempotencyKeyForRepeatedOpenAlerts() {
    AlertIngestRepository repository = org.mockito.Mockito.mock(AlertIngestRepository.class);
    OutboxWriter outboxWriter = org.mockito.Mockito.mock(OutboxWriter.class);
    AlertIngestRequest request =
        new AlertIngestRequest(
            "zabbix",
            "ds-1:event-1",
            "high",
            "CPU",
            null,
            "host-1",
            "service",
            "order-service",
            Map.of("env", "demo"),
            null,
            null,
            "open",
            Map.of());
    when(repository.upsert(eq("t1"), eq(request), any(), any(), any(), any()))
        .thenReturn(new AlertIngestResult("al-1", false, "open", "source-fp", "source-group"));
    AlertIngestService service =
        new AlertIngestService(
            new AlertFingerprintPolicy(), repository, new ObjectMapper(), outboxWriter);

    service.ingest("t1", request);
    service.ingest("t1", request);

    ArgumentCaptor<OutboxMessage> messages = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outboxWriter, times(2)).enqueueOrRequeueFailed(messages.capture());
    assertThat(messages.getAllValues())
        .extracting(OutboxMessage::idempotencyKey)
        .containsOnly("alert-lifecycle:al-1:open");
  }

  @Test
  void ingest_shouldUseANewLifecycleIdempotencyKeyForRecovery() {
    AlertIngestRepository repository = org.mockito.Mockito.mock(AlertIngestRepository.class);
    OutboxWriter outboxWriter = org.mockito.Mockito.mock(OutboxWriter.class);
    AlertIngestRequest request =
        new AlertIngestRequest(
            "zabbix",
            "ds-1:event-1",
            "high",
            "CPU",
            null,
            "host-1",
            "service",
            "order-service",
            Map.of("env", "demo"),
            null,
            null,
            "open",
            Map.of());
    when(repository.upsert(eq("t1"), eq(request), any(), any(), any(), any()))
        .thenReturn(
            new AlertIngestResult("al-1", true, "open", "source-fp", "source-group"),
            new AlertIngestResult("al-1", false, "resolved", "source-fp", "source-group"));
    AlertIngestService service =
        new AlertIngestService(
            new AlertFingerprintPolicy(), repository, new ObjectMapper(), outboxWriter);

    service.ingest("t1", request);
    service.ingest("t1", request);

    ArgumentCaptor<OutboxMessage> messages = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outboxWriter, times(2)).enqueueOrRequeueFailed(messages.capture());
    assertThat(messages.getAllValues())
        .extracting(OutboxMessage::idempotencyKey)
        .containsExactly("alert-lifecycle:al-1:open", "alert-lifecycle:al-1:resolved");
  }

  @Test
  void ingest_shouldRejectMissingTitle() {
    AlertIngestService service =
        new AlertIngestService(new AlertFingerprintPolicy(), null, new ObjectMapper(), null);

    assertThatThrownBy(
            () ->
                service.ingest(
                    "t1",
                    new AlertIngestRequest(
                        "zabbix", null, null, "", null, null, null, null, Map.of(), null, null,
                        null, Map.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("title");
  }

  @Test
  void ingest_shouldRejectMissingSource() {
    AlertIngestService service =
        new AlertIngestService(new AlertFingerprintPolicy(), null, new ObjectMapper(), null);

    assertThatThrownBy(
            () ->
                service.ingest(
                    "t1",
                    new AlertIngestRequest(
                        "", "10001", "high", "CPU", null, null, null, null, Map.of(), null, null,
                        null, Map.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source");
  }

  @Test
  void ingest_shouldRejectNullRequest() {
    AlertIngestService service =
        new AlertIngestService(new AlertFingerprintPolicy(), null, new ObjectMapper(), null);

    assertThatThrownBy(() -> service.ingest("t1", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required");
  }

  @Test
  void ingest_shouldRejectMissingTenant() {
    AlertIngestService service =
        new AlertIngestService(new AlertFingerprintPolicy(), null, new ObjectMapper(), null);

    assertThatThrownBy(
            () ->
                service.ingest(
                    "",
                    new AlertIngestRequest(
                        "zabbix", "10001", "high", "CPU", null, null, null, null, Map.of(), null,
                        null, null, Map.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenant");
  }
}
