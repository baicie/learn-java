package io.aegisops.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AlertIngestServiceTest {

  @Test
  void ingest_shouldPersistNormalizedAlert() {
    AlertIngestRepository repository = org.mockito.Mockito.mock(AlertIngestRepository.class);
    AlertIngestResult expected = new AlertIngestResult("al-1", true, "f", "g");
    when(repository.upsert(eq("t1"), any(), any(), any(), any(), any())).thenReturn(expected);

    AlertIngestService service =
        new AlertIngestService(new AlertFingerprintPolicy(), repository, new ObjectMapper());

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
  }

  @Test
  void ingest_shouldRejectMissingTitle() {
    AlertIngestService service =
        new AlertIngestService(
            new AlertFingerprintPolicy(),
            org.mockito.Mockito.mock(AlertIngestRepository.class),
            new ObjectMapper());

    assertThatThrownBy(
            () ->
                service.ingest(
                    "t1",
                    new AlertIngestRequest(
                        "zabbix",
                        null,
                        null,
                        "",
                        null,
                        null,
                        null,
                        null,
                        Map.of(),
                        null,
                        null,
                        null,
                        Map.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("title");
  }

  @Test
  void ingest_shouldRejectMissingSource() {
    AlertIngestService service =
        new AlertIngestService(
            new AlertFingerprintPolicy(),
            org.mockito.Mockito.mock(AlertIngestRepository.class),
            new ObjectMapper());

    assertThatThrownBy(
            () ->
                service.ingest(
                    "t1",
                    new AlertIngestRequest(
                        "",
                        "10001",
                        "high",
                        "CPU",
                        null,
                        null,
                        null,
                        null,
                        Map.of(),
                        null,
                        null,
                        null,
                        Map.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source");
  }

  @Test
  void ingest_shouldRejectNullRequest() {
    AlertIngestService service =
        new AlertIngestService(
            new AlertFingerprintPolicy(),
            org.mockito.Mockito.Mockito.mock(AlertIngestRepository.class),
            new ObjectMapper());

    assertThatThrownBy(() -> service.ingest("t1", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required");
  }
}
