package io.aegisops.evidence;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.aegisops.evidence.dto.EvidenceQueryRequest;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class JdbcEvidenceRepositoryTest {
  @Test
  void queryChangesUsesServiceNamesInsteadOfAlertTitle() {
    NamedParameterJdbcTemplate jdbc = Mockito.mock(NamedParameterJdbcTemplate.class);

    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
        .thenReturn(List.of());

    JdbcEvidenceRepository repository = new JdbcEvidenceRepository(jdbc);

    repository.queryChanges(request(), 10);

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(jdbc)
        .query(sqlCaptor.capture(), any(SqlParameterSource.class), any(RowMapper.class));

    String sql = sqlCaptor.getValue();

    assertTrue(sql.contains("service_name in (:serviceNames)"));
    assertTrue(sql.contains("asset_id = :primaryAssetId"));
  }

  @Test
  void queryLogsReturnsUnavailableWhenNoAssetAndNoService() {
    NamedParameterJdbcTemplate jdbc = Mockito.mock(NamedParameterJdbcTemplate.class);
    JdbcEvidenceRepository repository = new JdbcEvidenceRepository(jdbc);

    var result =
        repository.queryLogs(
            new EvidenceQueryRequest(
                "agent-diagnosis.v1",
                "tenant_1",
                "inc_1",
                "trace_1",
                null,
                OffsetDateTime.parse("2026-06-16T09:00:00+09:00"),
                OffsetDateTime.parse("2026-06-16T10:00:00+09:00"),
                List.of(),
                List.of(),
                List.of()),
            10);

    assertTrue(result.reason().contains("Primary asset id and service names are empty"));
  }

  private EvidenceQueryRequest request() {
    return new EvidenceQueryRequest(
        "agent-diagnosis.v1",
        "tenant_1",
        "inc_1",
        "trace_1",
        "asset_1",
        OffsetDateTime.parse("2026-06-16T09:00:00+09:00"),
        OffsetDateTime.parse("2026-06-16T10:00:00+09:00"),
        List.of("fp_cpu"),
        List.of("CPU high"),
        List.of("checkout-service"));
  }
}
