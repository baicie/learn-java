package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.AiPeriodReportRepository;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AiInputBuilderTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneId.of("Asia/Shanghai"));

  @Test
  void monthlyReportUsesAllRecordsForStatisticsButBoundsPromptSamples() throws Exception {
    WorkRecordQueryService records = mock(WorkRecordQueryService.class);
    AiPeriodReportRepository periodReports = mock(AiPeriodReportRepository.class);
    WorkRecordUserLookupService users = mock(WorkRecordUserLookupService.class);
    List<WorkRecord> first =
        IntStream.range(0, 100).mapToObj(i -> record(i, RecordStatus.DONE)).toList();
    List<WorkRecord> second =
        IntStream.range(100, 120).mapToObj(i -> record(i, RecordStatus.PROCESSING)).toList();
    List<WorkRecord> all = java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    when(periodReports.snapshot(any(), any(), any(), any(Integer.class))).thenReturn(snapshot(all));
    when(records.visible(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    when(users.displayNames(any(), any())).thenReturn(Map.of("owner-1", "Alice", "owner-2", "Bob"));
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    var builder = new AiInputBuilder(records, periodReports, users, mapper, CLOCK);

    var request =
        builder.monthlyReport(
            "tenant-1", java.time.LocalDate.of(2026, 7, 1), principal(), "trace-1");

    assertThat(request.statistics())
        .containsEntry("recordCount", 120L)
        .containsEntry("sampledRecordCount", request.records().size())
        .containsEntry("truncated", true);
    assertThat(request.statistics().get("statusCounts"))
        .isEqualTo(Map.of("done", 100L, "processing", 20L));
    assertThat(request.statistics().get("ownerCounts"))
        .isEqualTo(
            List.of(
                Map.of("ownerId", "owner-1", "displayName", "Alice", "count", 100L),
                Map.of("ownerId", "owner-2", "displayName", "Bob", "count", 20L)));
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
    AiPeriodReportRepository periodReports = mock(AiPeriodReportRepository.class);
    WorkRecordUserLookupService users = mock(WorkRecordUserLookupService.class);
    when(records.get(any(), any(), any())).thenReturn(record(1, RecordStatus.DONE));
    when(users.displayNames(any(), any())).thenReturn(Map.of("owner-1", "Alice"));
    var builder =
        new AiInputBuilder(
            records, periodReports, users, new ObjectMapper().findAndRegisterModules(), CLOCK);

    var request = builder.recordSummary("tenant-1", "record-1", principal(), "trace-1");

    assertThat(request.actorId()).isEqualTo("user-1");
  }

  @Test
  void monthlyReportUsesAnonymousOwnerForUnassignedRecords() {
    WorkRecordQueryService records = mock(WorkRecordQueryService.class);
    AiPeriodReportRepository periodReports = mock(AiPeriodReportRepository.class);
    WorkRecordUserLookupService users = mock(WorkRecordUserLookupService.class);
    WorkRecord unassigned = record(1, RecordStatus.DONE, null);
    when(periodReports.snapshot(any(), any(), any(), any(Integer.class)))
        .thenReturn(snapshot(List.of(unassigned)));
    when(records.visible(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    when(users.displayNames(any(), any())).thenReturn(Map.of());
    var builder =
        new AiInputBuilder(
            records, periodReports, users, new ObjectMapper().findAndRegisterModules(), CLOCK);

    var request =
        builder.monthlyReport(
            "tenant-1", java.time.LocalDate.of(2026, 7, 1), principal(), "trace-1");

    assertThat(request.records())
        .singleElement()
        .extracting(item -> item.ownerName())
        .isEqualTo("anonymous");
  }

  @Test
  void monthlyReportKeepsOwnersWithTheSameDisplayNameSeparate() {
    WorkRecordQueryService records = mock(WorkRecordQueryService.class);
    AiPeriodReportRepository periodReports = mock(AiPeriodReportRepository.class);
    WorkRecordUserLookupService users = mock(WorkRecordUserLookupService.class);
    WorkRecord first = record(1, RecordStatus.DONE, "owner-1");
    WorkRecord second = record(2, RecordStatus.DONE, "owner-2");
    when(periodReports.snapshot(any(), any(), any(), any(Integer.class)))
        .thenReturn(snapshot(List.of(first, second)));
    when(records.visible(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    when(users.displayNames(any(), any())).thenReturn(Map.of("owner-1", "Alex", "owner-2", "Alex"));
    var builder =
        new AiInputBuilder(
            records, periodReports, users, new ObjectMapper().findAndRegisterModules(), CLOCK);

    var request =
        builder.monthlyReport(
            "tenant-1", java.time.LocalDate.of(2026, 7, 1), principal(), "trace-1");

    assertThat(request.statistics().get("ownerCounts"))
        .isEqualTo(
            List.of(
                Map.of("ownerId", "owner-1", "displayName", "Alex", "count", 1L),
                Map.of("ownerId", "owner-2", "displayName", "Alex", "count", 1L)));
  }

  @Test
  void monthlyReportUsesOnlyFieldPolicyFilteredSamples() {
    WorkRecordQueryService records = mock(WorkRecordQueryService.class);
    AiPeriodReportRepository periodReports = mock(AiPeriodReportRepository.class);
    WorkRecordUserLookupService users = mock(WorkRecordUserLookupService.class);
    WorkRecord raw = record(1, RecordStatus.DONE);
    WorkRecord filtered =
        new WorkRecord(
            raw.id(),
            raw.tenantId(),
            raw.templateId(),
            raw.templateVersionId(),
            raw.title(),
            raw.status(),
            raw.ownerId(),
            raw.creatorId(),
            raw.recordTime(),
            raw.builtinDataJson(),
            "{}",
            raw.rowVersion(),
            raw.createdAt(),
            raw.updatedAt(),
            raw.deletedAt());
    UserPrincipal principal = principal();
    when(periodReports.snapshot(any(), any(), any(), any(Integer.class)))
        .thenReturn(snapshot(List.of(raw)));
    when(records.visible("tenant-1", List.of(raw), principal)).thenReturn(List.of(filtered));
    when(users.displayNames(any(), any())).thenReturn(Map.of("owner-1", "Alice"));
    var builder =
        new AiInputBuilder(
            records, periodReports, users, new ObjectMapper().findAndRegisterModules(), CLOCK);

    var request =
        builder.monthlyReport("tenant-1", java.time.LocalDate.of(2026, 7, 1), principal, "trace-1");

    assertThat(request.records())
        .singleElement()
        .satisfies(item -> assertThat(item.fields()).isEmpty());
  }

  @Test
  void weeklyReportUsesSevenDayInclusivePeriodAndWeeklyContract() {
    WorkRecordQueryService records = mock(WorkRecordQueryService.class);
    AiPeriodReportRepository periodReports = mock(AiPeriodReportRepository.class);
    WorkRecordUserLookupService users = mock(WorkRecordUserLookupService.class);
    when(periodReports.snapshot(any(), any(), any(), any(Integer.class)))
        .thenReturn(snapshot(List.of(record(1, RecordStatus.DONE))));
    when(records.visible(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    when(users.displayNames(any(), any())).thenReturn(Map.of("owner-1", "Alice"));
    var builder =
        new AiInputBuilder(
            records, periodReports, users, new ObjectMapper().findAndRegisterModules(), CLOCK);

    var request =
        builder.weeklyReport(
            "tenant-1", java.time.LocalDate.of(2026, 7, 13), principal(), "trace-weekly");

    assertThat(request.generationType()).isEqualTo("weekly_report");
    assertThat(request.resourceId()).isEqualTo("2026-07-13");
    assertThat(request.periodStart()).isEqualTo(java.time.LocalDate.of(2026, 7, 13));
    assertThat(request.periodEnd()).isEqualTo(java.time.LocalDate.of(2026, 7, 19));
    assertThat(request.promptVersion()).isEqualTo("work-record-weekly-v1");
    verify(periodReports)
        .snapshot(
            "tenant-1",
            OffsetDateTime.parse("2026-07-13T00:00:00+08:00"),
            OffsetDateTime.parse("2026-07-20T00:00:00+08:00"),
            50);
  }

  @Test
  void monthlyReportDropsOversizedSamplesButKeepsAuthoritativeCounts() throws Exception {
    WorkRecordQueryService records = mock(WorkRecordQueryService.class);
    AiPeriodReportRepository periodReports = mock(AiPeriodReportRepository.class);
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
    when(periodReports.snapshot(any(), any(), any(), any(Integer.class)))
        .thenReturn(snapshot(List.of(oversized)));
    when(records.visible(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    when(users.displayNames(any(), any())).thenReturn(Map.of("owner-1", "Alice"));
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    var builder = new AiInputBuilder(records, periodReports, users, mapper, CLOCK);

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
    return record(index, status, index < 100 ? "owner-1" : "owner-2");
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

  private static AiPeriodReportRepository.PeriodSnapshot snapshot(List<WorkRecord> records) {
    Map<String, Long> statuses = new java.util.LinkedHashMap<>();
    Map<String, Long> owners = new java.util.LinkedHashMap<>();
    records.forEach(
        record -> {
          statuses.merge(record.status().value(), 1L, Long::sum);
          owners.merge(record.ownerId() == null ? "" : record.ownerId(), 1L, Long::sum);
        });
    return new AiPeriodReportRepository.PeriodSnapshot(
        records.size(),
        statuses.entrySet().stream()
            .map(entry -> new AiPeriodReportRepository.Count(entry.getKey(), entry.getValue()))
            .toList(),
        owners.entrySet().stream()
            .sorted(
                Map.Entry.<String, Long>comparingByValue()
                    .reversed()
                    .thenComparing(Map.Entry.comparingByKey()))
            .limit(10)
            .map(entry -> new AiPeriodReportRepository.Count(entry.getKey(), entry.getValue()))
            .toList(),
        records.stream().limit(50).toList());
  }

  private static UserPrincipal principal() {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
        Set.of(),
        Set.of(),
        Map.of());
  }
}
