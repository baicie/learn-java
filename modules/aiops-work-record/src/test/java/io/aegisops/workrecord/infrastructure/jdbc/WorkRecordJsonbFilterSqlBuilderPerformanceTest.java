package io.aegisops.workrecord.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.command.DynamicFilterOperator;
import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import io.aegisops.workrecord.domain.model.FieldType;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("performance")
class WorkRecordJsonbFilterSqlBuilderPerformanceTest {
  private final WorkRecordJsonbFilterSqlBuilder builder =
      new WorkRecordJsonbFilterSqlBuilder(new ObjectMapper());

  @Test
  void shouldBuildParameterizedSqlFastEnoughForRepeatedQueries() {
    List<RecordDynamicFilter> filters =
        List.of(
            RecordDynamicFilter.normalized(
                "content", DynamicFilterOperator.CONTAINS, FieldType.TEXT, "error", List.of()),
            RecordDynamicFilter.normalized(
                "cost", DynamicFilterOperator.GTE, FieldType.NUMBER, "100", List.of()),
            RecordDynamicFilter.normalized(
                "tags",
                DynamicFilterOperator.CONTAINS_ALL,
                FieldType.MULTI_SELECT,
                null,
                List.of("a", "b")),
            RecordDynamicFilter.normalized(
                "startedAt",
                DynamicFilterOperator.BETWEEN,
                FieldType.DATETIME,
                null,
                List.of("2026-01-01T00:00Z", "2026-02-01T00:00Z")));

    Instant start = Instant.now();

    for (int i = 0; i < 5_000; i++) {
      StringBuilder where = new StringBuilder(" where tenant_id = :tenantId ");
      Map<String, Object> params = new HashMap<>();
      params.put("tenantId", "t1");
      builder.appendFilters(where, params, filters);

      assertThat(where).isNotEmpty();
      assertThat(params)
          .containsKeys(
              "dfKey0",
              "dfValue0",
              "dfKey1",
              "dfValue1",
              "dfKey2",
              "dfJson2",
              "dfKey3",
              "dfFrom3",
              "dfTo3");
    }

    Duration elapsed = Duration.between(start, Instant.now());
    assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
  }
}
