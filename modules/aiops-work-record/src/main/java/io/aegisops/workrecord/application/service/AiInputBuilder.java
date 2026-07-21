package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.workrecord.WorkRecordGenerationRequest;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiInputBuilder {
  private static final int PAGE_SIZE = 100;
  private static final int MAX_MONTH_SAMPLES = 50;
  private static final int MAX_MONTH_INPUT_BYTES = 65_536;
  private final WorkRecordQueryService records;
  private final WorkRecordUserLookupService users;
  private final ObjectMapper objectMapper;

  public AiInputBuilder(
      WorkRecordQueryService records,
      WorkRecordUserLookupService users,
      ObjectMapper objectMapper) {
    this.records = records;
    this.users = users;
    this.objectMapper = objectMapper;
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
    LocalDate end = start.plusMonths(1);
    List<WorkRecord> samples = new ArrayList<>();
    Map<String, Long> statusCounts = new LinkedHashMap<>();
    long recordCount = 0;
    for (int page = 1; ; page++) {
      RecordQuery query =
          new RecordQuery(
              page,
              PAGE_SIZE,
              null,
              null,
              List.of(),
              null,
              start.atStartOfDay().atOffset(ZoneOffset.UTC),
              end.atStartOfDay().atOffset(ZoneOffset.UTC),
              null,
              null,
              false,
              null,
              List.of(),
              "recordTime",
              "asc",
              "all",
              null);
      var result = records.page(tenantId, query, principal);
      for (WorkRecord record : result.items()) {
        recordCount++;
        statusCounts.merge(record.status().value(), 1L, Long::sum);
        if (samples.size() < MAX_MONTH_SAMPLES) {
          samples.add(record);
        }
      }
      if (result.items().isEmpty() || recordCount >= result.total()) {
        break;
      }
    }
    Map<String, Object> statistics = new LinkedHashMap<>();
    statistics.put("recordCount", recordCount);
    statistics.put("statusCounts", Map.copyOf(statusCounts));
    statistics.put("truncated", recordCount > samples.size());
    Map<String, String> ownerNames = ownerNames(tenantId, samples);
    List<WorkRecordGenerationRequest.RecordItem> items = new ArrayList<>();
    var context = new MonthlyRequestContext(tenantId, start, end, principal.id(), traceId);
    for (WorkRecord sample : samples) {
      items.add(item(sample, ownerNames));
      statistics.put("sampledRecordCount", items.size());
      if (!fitsMonthlyLimit(context, items, statistics)) {
        items.removeLast();
        break;
      }
    }
    statistics.put("sampledRecordCount", items.size());
    statistics.put("truncated", recordCount > items.size());
    return request(context, items, statistics);
  }

  private WorkRecordGenerationRequest request(
      MonthlyRequestContext context,
      List<WorkRecordGenerationRequest.RecordItem> items,
      Map<String, Object> statistics) {
    return new WorkRecordGenerationRequest(
        "work-record-generation.v1",
        "monthly_report",
        context.tenantId(),
        context.start().toString().substring(0, 7),
        context.actorId(),
        context.start(),
        context.end().minusDays(1),
        "zh-CN",
        "work-record-monthly-v1",
        items,
        statistics,
        context.traceId());
  }

  private boolean fitsMonthlyLimit(
      MonthlyRequestContext context,
      List<WorkRecordGenerationRequest.RecordItem> items,
      Map<String, Object> statistics) {
    try {
      return objectMapper.writeValueAsBytes(
                  request(context, items, statistics))
              .length
          <= MAX_MONTH_INPUT_BYTES;
    } catch (Exception ex) {
      throw new IllegalStateException("failed to size AI input", ex);
    }
  }

  private Map<String, String> ownerNames(String tenantId, List<WorkRecord> source) {
    LinkedHashSet<String> ownerIds = new LinkedHashSet<>();
    source.stream()
        .map(WorkRecord::ownerId)
        .filter(value -> value != null && !value.isBlank())
        .forEach(ownerIds::add);
    return users.displayNames(tenantId, ownerIds);
  }

  private WorkRecordGenerationRequest.RecordItem item(
      WorkRecord record, Map<String, String> ownerNames) {
    return new WorkRecordGenerationRequest.RecordItem(
        record.id(),
        record.title(),
        record.status().value(),
        record.recordTime(),
        ownerNames.getOrDefault(record.ownerId(), "anonymous"),
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

  private record MonthlyRequestContext(
      String tenantId, LocalDate start, LocalDate end, String actorId, String traceId) {}
}
