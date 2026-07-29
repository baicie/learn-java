package io.aegisops.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcIncidentRepositoryTest {
  @Test
  void readyToResolveExcludesUnlinkedOpenAlertsWithTheSameAggregationKey() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
    JdbcIncidentRepository repository = new JdbcIncidentRepository(jdbc);

    repository.findActiveIncidentsReadyToResolve("tenant-a");

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
    assertThat(normalize(sql.getValue()))
        .contains("a.tenant_id = i.tenant_id")
        .contains("a.aggregation_key = i.aggregation_key")
        .contains("a.status = 'open'");
  }

  @Test
  void autoResolveUsesAnActiveStatusCompareAndSet() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    JdbcIncidentRepository repository = new JdbcIncidentRepository(jdbc);

    assertThat(
            repository.resolveIfActiveAt(
                "tenant-a", "incident-a", OffsetDateTime.parse("2026-07-27T10:20:30Z")))
        .isTrue();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).update(sql.capture(), any(Object[].class));
    assertThat(normalize(sql.getValue()))
        .contains("status = 'resolved'")
        .contains("status in ('open', 'investigating', 'mitigating')")
        .contains("a.tenant_id = i.tenant_id")
        .contains("a.aggregation_key = i.aggregation_key")
        .contains("a.status = 'open'");
  }

  @Test
  void backfillsMissingPrimaryAssetsFromLinkedAlerts() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    JdbcIncidentRepository repository = new JdbcIncidentRepository(jdbc);

    assertThat(repository.backfillPrimaryAssetIds("tenant-a")).isOne();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).update(sql.capture(), any(Object[].class));
    assertThat(normalize(sql.getValue()))
        .contains("update incident")
        .contains("primary_asset_id")
        .contains("incident_event")
        .contains("alert_event")
        .contains("tenant_id = ?");
  }

  private static String normalize(String sql) {
    return sql.replaceAll("\\s+", " ").toLowerCase();
  }
}
