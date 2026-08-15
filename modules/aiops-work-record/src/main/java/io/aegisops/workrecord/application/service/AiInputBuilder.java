package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.workrecord.WorkRecordGenerationRequest;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.AiPeriodReportRepository;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AiInputBuilder {
  private static final int MAX_PERIOD_SAMPLES = 50;
  private static final int MAX_PERIOD_INPUT_BYTES = 65_536;
  private final WorkRecordQueryService records;
  private final AiPeriodReportRepository periodReports;
  private final WorkRecordUserLookupService users;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public AiInputBuilder(
      WorkRecordQueryService records,
      AiPeriodReportRepository periodReports,
      WorkRecordUserLookupService users,
      ObjectMapper objectMapper,
      @Qualifier("workRecordClock") Clock clock) {
    this.records = records;
    this.periodReports = periodReports;
    this.users = users;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public WorkRecordGenerationRequest recordSummary(
      String tenantId, String recordId, UserPrincipal principal, String traceId) {
    WorkRecord record = records.get(tenantId, recordId, principal);
    return new WorkRecordGenerationRequest(
        "work-record-generation.v1",
        "record_summary",
        tenantId,
        recordId,
        principal.id(),
        null,
        null,
        "zh-CN",
        "work-record-summary-v1",
        List.of(item(record, ownerNames(tenantId, List.of(record)))),
        Map.of(),
        traceId);
  }

  public WorkRecordGenerationRequest monthlyReport(
      String tenantId, LocalDate month, UserPrincipal principal, String traceId) {
    LocalDate start = month.withDayOfMonth(1);
    return periodReport(
        tenantId,
        start,
        start.plusMonths(1),
        "monthly_report",
        "work-record-monthly-v1",
        start.toString().substring(0, 7),
        principal,
        traceId);
  }

  public WorkRecordGenerationRequest weeklyReport(
      String tenantId, LocalDate weekStart, UserPrincipal principal, String traceId) {
    return periodReport(
        tenantId,
        weekStart,
        weekStart.plusWeeks(1),
        "weekly_report",
        "work-record-weekly-v1",
        weekStart.toString(),
        principal,
        traceId);
  }

  private WorkRecordGenerationRequest periodReport(
      String tenantId,
      LocalDate start,
      LocalDate end,
      String generationType,
      String promptVersion,
      String resourceId,
      UserPrincipal principal,
      String traceId) {
    var snapshot =
        periodReports.snapshot(
            tenantId,
            start.atStartOfDay(clock.getZone()).toOffsetDateTime(),
            end.atStartOfDay(clock.getZone()).toOffsetDateTime(),
            MAX_PERIOD_SAMPLES);
    List<WorkRecord> samples = records.visible(tenantId, snapshot.samples(), principal);
    long recordCount = snapshot.recordCount();
    Map<String, Object> statistics = new LinkedHashMap<>();
    statistics.put("recordCount", recordCount);
    statistics.put(
        "statusCounts",
        snapshot.statusCounts().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    AiPeriodReportRepository.Count::key,
                    AiPeriodReportRepository.Count::count,
                    Long::sum,
                    LinkedHashMap::new)));
    statistics.put("truncated", recordCount > samples.size());
    List<Entry<String, Long>> topOwners =
        snapshot.ownerCounts().stream()
            .map(value -> Map.entry(value.key(), value.count()))
            .toList();
    LinkedHashSet<String> relevantOwnerIds = new LinkedHashSet<>();
    samples.stream().map(WorkRecord::ownerId).forEach(relevantOwnerIds::add);
    topOwners.stream().map(Entry::getKey).forEach(relevantOwnerIds::add);
    Map<String, String> ownerNames = ownerNames(tenantId, relevantOwnerIds);
    statistics.put("ownerCounts", ownerCounts(topOwners, ownerNames));
    List<WorkRecordGenerationRequest.RecordItem> items = new ArrayList<>();
    var context =
        new PeriodRequestContext(
            tenantId,
            start,
            end,
            generationType,
            promptVersion,
            resourceId,
            principal.id(),
            traceId);
    for (WorkRecord sample : samples) {
      items.add(item(sample, ownerNames));
      statistics.put("sampledRecordCount", items.size());
      if (!fitsPeriodLimit(context, items, statistics)) {
        items.removeLast();
        break;
      }
    }
    statistics.put("sampledRecordCount", items.size());
    statistics.put("truncated", recordCount > items.size());
    return request(context, items, statistics);
  }

  private WorkRecordGenerationRequest request(
      PeriodRequestContext context,
      List<WorkRecordGenerationRequest.RecordItem> items,
      Map<String, Object> statistics) {
    return new WorkRecordGenerationRequest(
        "work-record-generation.v1",
        context.generationType(),
        context.tenantId(),
        context.resourceId(),
        context.actorId(),
        context.start(),
        context.end().minusDays(1),
        "zh-CN",
        context.promptVersion(),
        items,
        statistics,
        context.traceId());
  }

  private boolean fitsPeriodLimit(
      PeriodRequestContext context,
      List<WorkRecordGenerationRequest.RecordItem> items,
      Map<String, Object> statistics) {
    try {
      return objectMapper.writeValueAsBytes(request(context, items, statistics)).length
          <= MAX_PERIOD_INPUT_BYTES;
    } catch (Exception ex) {
      throw new IllegalStateException("failed to size AI input", ex);
    }
  }

  private List<Map<String, Object>> ownerCounts(
      List<Entry<String, Long>> owners, Map<String, String> ownerNames) {
    List<Map<String, Object>> result = new ArrayList<>();
    owners.forEach(
        owner -> {
          String ownerId = owner.getKey();
          String displayName =
              ownerId.isEmpty() ? "anonymous" : ownerNames.getOrDefault(ownerId, "anonymous");
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("ownerId", ownerId);
          item.put("displayName", displayName);
          item.put("count", owner.getValue());
          result.add(Map.copyOf(item));
        });
    return List.copyOf(result);
  }

  private Map<String, String> ownerNames(String tenantId, Iterable<String> source) {
    LinkedHashSet<String> ownerIds = new LinkedHashSet<>();
    source.forEach(
        value -> {
          if (value != null && !value.isBlank()) {
            ownerIds.add(value);
          }
        });
    return users.displayNames(tenantId, ownerIds);
  }

  private Map<String, String> ownerNames(String tenantId, List<WorkRecord> source) {
    return ownerNames(tenantId, source.stream().map(WorkRecord::ownerId).toList());
  }

  private WorkRecordGenerationRequest.RecordItem item(
      WorkRecord record, Map<String, String> ownerNames) {
    String ownerName =
        record.ownerId() == null
            ? "anonymous"
            : ownerNames.getOrDefault(record.ownerId(), "anonymous");
    return new WorkRecordGenerationRequest.RecordItem(
        record.id(),
        record.title(),
        record.status().value(),
        record.recordTime(),
        ownerName,
        readObject(record.customDataJson()),
        List.of());
  }

  private Map<String, Object> readObject(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception ex) {
      throw new IllegalStateException("invalid work-record custom data", ex);
    }
  }

  private record PeriodRequestContext(
      String tenantId,
      LocalDate start,
      LocalDate end,
      String generationType,
      String promptVersion,
      String resourceId,
      String actorId,
      String traceId) {}
}
