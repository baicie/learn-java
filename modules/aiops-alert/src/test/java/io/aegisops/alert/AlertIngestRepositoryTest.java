package io.aegisops.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AlertIngestRepositoryTest {

  @Test
  void upsertShouldPreserveCanonicalMediumAndHighSeverities() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenReturn(new AlertIngestResult("alert-1", false, "fingerprint", "group"));
    AlertIngestRepository repository = new AlertIngestRepository(jdbc);

    repository.upsert("tenant-1", request("medium", "open"), "fingerprint", "group", "{}", "{}");
    repository.upsert("tenant-1", request("high", "open"), "fingerprint", "group", "{}", "{}");

    ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc, times(2)).queryForObject(anyString(), any(RowMapper.class), arguments.capture());

    assertThat(arguments.getAllValues().stream().map(values -> values[4]))
        .containsExactly("medium", "high");
  }

  @Test
  void upsertShouldPersistRecoveredStatusAsResolved() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenReturn(new AlertIngestResult("alert-1", false, "fingerprint", "group"));
    AlertIngestRepository repository = new AlertIngestRepository(jdbc);

    repository.upsert(
        "tenant-1",
        new AlertIngestRequest(
            "zabbix",
            "event-1",
            "high",
            "CPU high",
            null,
            "asset-1",
            "host",
            "demo-host",
            Map.of(),
            OffsetDateTime.parse("2026-07-27T01:00:00Z"),
            OffsetDateTime.parse("2026-07-27T01:05:00Z"),
            "recovered",
            Map.of()),
        "fingerprint",
        "group",
        "{}",
        "{}");

    ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).queryForObject(anyString(), any(RowMapper.class), arguments.capture());

    assertThat(arguments.getValue()[13]).isEqualTo("resolved");
  }

  private AlertIngestRequest request(String severity, String status) {
    return new AlertIngestRequest(
        "zabbix",
        "event-1",
        severity,
        "CPU high",
        null,
        "asset-1",
        "host",
        "demo-host",
        Map.of(),
        OffsetDateTime.parse("2026-07-27T01:00:00Z"),
        null,
        status,
        Map.of());
  }
}
