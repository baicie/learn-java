package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.api.PageResult;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AiInputBuilderTest {
  @Test
  void monthlyReportUsesAllRecordsForStatisticsButBoundsPromptSamples() throws Exception {
    WorkRecordQueryService records = mock(WorkRecordQueryService.class);
    WorkRecordUserLookupService users = mock(WorkRecordUserLookupService.class);
    List<WorkRecord> first =
        IntStream.range(0, 100).mapToObj(i -> record(i, RecordStatus.DONE)).toList();
    List<WorkRecord> second =
        IntStream.range(100, 120).mapToObj(i -> record(i, RecordStatus.PROCESSING)).toList();
    when(records.page(any(), any(), any()))
        .thenReturn(new PageResult<>(120, 1, 100, first))
        .thenReturn(new PageResult<>(120, 2, 100, second));
    when(users.displayNames(any(), any())).thenReturn(Map.of("owner-1", "Alice"));
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    var builder = new AiInputBuilder(records, users, mapper);

    var request =
        builder.monthlyReport(
            "tenant-1", java.time.LocalDate.of(2026, 7, 1), principal(), "trace-1");

    assertThat(request.statistics())
        .containsEntry("recordCount", 120L)
        .containsEntry("sampledRecordCount", request.records().size())
        .containsEntry("truncated", true);
    assertThat(request.statistics().get("statusCounts"))
        .isEqualTo(Map.of("done", 100L, "processing", 20L));
    assertThat(request.records()).hasSizeLessThanOrEqualTo(50);
    assertThat(request.records()).allMatch(item -> "Alice".equals(item.ownerName()));
    assertThat(request.records())
        .allMatch(item -> item.fields().equals(Map.of("visible", "value")));
    assertThat(request.actorId()).isEqualTo("user-1");
    assertThat(mapper.writeValueAsBytes(request).length).isLessThanOrEqualTo(65_536);
  }

  @Test
  void recordSummaryIncludesRequestingActorId() {
    WorkRecordQueryService records = mock(WorkRecordQueryService.class);
    WorkRecordUserLookupService users = mock(WorkRecordUserLookupService.class);
    when(records.get(any(), any(), any())).thenReturn(record(1, RecordStatus.DONE));
    when(users.displayNames(any(), any())).thenReturn(Map.of("owner-1", "Alice"));
    var builder = new AiInputBuilder(records, users, new ObjectMapper().findAndRegisterModules());

    var request = builder.recordSummary("tenant-1", "record-1", principal(), "trace-1");

    assertThat(request.actorId()).isEqualTo("user-1");
  }

  @Test
  void monthlyReportUsesAnonymousOwnerForUnassignedRecords() {
    WorkRecordQueryService records = mock(WorkRecordQueryService.class);
    WorkRecordUserLookupService users = mock(WorkRecordUserLookupService.class);
    WorkRecord unassigned = record(1, RecordStatus.DONE, null);
    when(records.page(any(), any(), any()))
        .thenReturn(new PageResult<>(1, 1, 100, List.of(unassigned)));
    when(users.displayNames(any(), any())).thenReturn(Map.of());
    var builder = new AiInputBuilder(records, users, new ObjectMapper().findAndRegisterModules());

    var request =
        builder.monthlyReport(
            "tenant-1", java.time.LocalDate.of(2026, 7, 1), principal(), "trace-1");

    assertThat(request.records())
        .singleElement()
        .extracting(item -> item.ownerName())
        .isEqualTo("anonymous");
  }

  @Test
  void monthlyReportDropsOversizedSamplesButKeepsAuthoritativeCounts() throws Exception {
    WorkRecordQueryService records = mock(WorkRecordQueryService.class);
    WorkRecordUserLookupService users = mock(WorkRecordUserLookupService.class);
    WorkRecord oversized =
        new WorkRecord(
            "record-large",
            "tenant-1",
            "template-1",
            "version-1",
            "Large record",
            RecordStatus.DONE,
            "owner-1",
            "creator-1",
            OffsetDateTime.parse("2026-07-01T00:00:00Z"),
            "{}",
            "{\"visible\":\"" + "x".repeat(70_000) + "\"}",
            1,
            OffsetDateTime.parse("2026-07-01T00:00:00Z"),
            OffsetDateTime.parse("2026-07-01T00:00:00Z"),
            null);
    when(records.page(any(), any(), any()))
        .thenReturn(new PageResult<>(1, 1, 100, List.of(oversized)));
    when(users.displayNames(any(), any())).thenReturn(Map.of("owner-1", "Alice"));
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    var builder = new AiInputBuilder(records, users, mapper);

    var request =
        builder.monthlyReport(
            "tenant-1", java.time.LocalDate.of(2026, 7, 1), principal(), "trace-1");

    assertThat(request.statistics())
        .containsEntry("recordCount", 1L)
        .containsEntry("sampledRecordCount", 0)
        .containsEntry("truncated", true);
    assertThat(request.records()).isEmpty();
    assertThat(mapper.writeValueAsBytes(request).length).isLessThanOrEqualTo(65_536);
  }

  private static WorkRecord record(int index, RecordStatus status) {
    return record(index, status, "owner-1");
  }

  private static WorkRecord record(int index, RecordStatus status, String ownerId) {
    return new WorkRecord(
        "record-" + index,
        "tenant-1",
        "template-1",
        "version-1",
        "Title " + index,
        status,
        ownerId,
        "creator-1",
        OffsetDateTime.parse("2026-07-01T00:00:00Z"),
        "{}",
        "{\"visible\":\"value\"}",
        1,
        OffsetDateTime.parse("2026-07-01T00:00:00Z"),
        OffsetDateTime.parse("2026-07-01T00:00:00Z"),
        null);
  }

  private static UserPrincipal principal() {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
        Set.of(),
        Set.of(),
        Map.of());
  }
}
