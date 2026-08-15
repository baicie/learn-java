package io.aegisops.workrecord.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.workrecord.application.port.AiPeriodReportRepository;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcAiPeriodReportRepositoryTest {
  @Test
  void snapshotUsesBoundedSamplesWithoutOffsetPaging() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForList(any(String.class), any(Map.class)))
        .thenReturn(
            java.util.List.of(
                Map.of("statistic_type", "total", "statistic_key", "", "item_count", 120_000L),
                Map.of("statistic_type", "status", "statistic_key", "done", "item_count", 100_000L),
                Map.of(
                    "statistic_type", "owner", "statistic_key", "owner-1", "item_count", 80_000L)));
    when(jdbc.query(any(String.class), any(Map.class), any(RowMapper.class)))
        .thenReturn(Collections.emptyList());
    var repository = new JdbcAiPeriodReportRepository(jdbc);

    var result =
        repository.snapshot(
            "tenant-1",
            OffsetDateTime.parse("2026-07-01T00:00:00+08:00"),
            OffsetDateTime.parse("2026-08-01T00:00:00+08:00"),
            50);

    assertThat(result.recordCount()).isEqualTo(120_000L);
    assertThat(result.statusCounts())
        .containsExactly(new AiPeriodReportRepository.Count("done", 100_000L));
    assertThat(result.ownerCounts())
        .containsExactly(new AiPeriodReportRepository.Count("owner-1", 80_000L));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
    verify(jdbc).queryForList(any(String.class), params.capture());
    assertThat(params.getValue()).containsEntry("sampleLimit", 50).doesNotContainKey("offset");
  }
}
