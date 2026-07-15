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
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiInputBuilder {
  private static final int MAX_MONTH_RECORDS = 5000;
  private final WorkRecordQueryService records;
  private final ObjectMapper objectMapper;

  public AiInputBuilder(WorkRecordQueryService records, ObjectMapper objectMapper) {
    this.records = records;
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
        null,
        null,
        "zh-CN",
        "work-record-summary-v1",
        List.of(item(record)),
        Map.of(),
        traceId);
  }

  public WorkRecordGenerationRequest monthlyReport(
      String tenantId, LocalDate month, UserPrincipal principal, String traceId) {
    LocalDate start = month.withDayOfMonth(1);
    LocalDate end = start.plusMonths(1);
    List<WorkRecordGenerationRequest.RecordItem> items = new ArrayList<>();
    for (int page = 1; items.size() < MAX_MONTH_RECORDS; page++) {
      RecordQuery query =
          new RecordQuery(
              page,
              100,
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
      result.items().forEach(value -> items.add(item(value)));
      if (result.items().isEmpty() || items.size() >= result.total()) {
        break;
      }
    }
    return new WorkRecordGenerationRequest(
        "work-record-generation.v1",
        "monthly_report",
        tenantId,
        start.toString().substring(0, 7),
        start,
        end.minusDays(1),
        "zh-CN",
        "work-record-monthly-v1",
        items,
        Map.of(),
        traceId);
  }

  private WorkRecordGenerationRequest.RecordItem item(WorkRecord record) {
    return new WorkRecordGenerationRequest.RecordItem(
        record.id(),
        record.title(),
        record.status().value(),
        record.recordTime(),
        record.ownerId(),
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
}
