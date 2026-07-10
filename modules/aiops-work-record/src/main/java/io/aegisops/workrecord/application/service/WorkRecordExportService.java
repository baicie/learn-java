package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.id.Ids;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.RecordListColumn;
import io.aegisops.workrecord.application.command.RecordListMeta;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.command.WorkRecordExportResult;
import io.aegisops.workrecord.application.port.WorkRecordDictionaryPort;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.application.port.WorkRecordUserPort;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordExportService {
  private static final DateTimeFormatter FILE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  private static final DateTimeFormatter CELL_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");

  private final WorkRecordRepository recordRepository;
  private final WorkRecordQueryService queryService;
  private final WorkRecordListMetaService metaService;
  private final WorkRecordPermissionService permissionService;
  private final WorkRecordExportPolicy exportPolicy;
  private final WorkRecordDictionaryPort dictionaryPort;
  private final WorkRecordUserPort userPort;
  private final WorkRecordAuditService auditService;
  private final WorkRecordCsvWriter csvWriter;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public WorkRecordExportService(
      WorkRecordRepository recordRepository,
      WorkRecordQueryService queryService,
      WorkRecordListMetaService metaService,
      WorkRecordPermissionService permissionService,
      WorkRecordExportPolicy exportPolicy,
      WorkRecordDictionaryPort dictionaryPort,
      WorkRecordUserPort userPort,
      WorkRecordAuditService auditService,
      WorkRecordCsvWriter csvWriter,
      ObjectMapper objectMapper,
      @Qualifier("workRecordClock") Clock clock) {
    this.recordRepository = recordRepository;
    this.queryService = queryService;
    this.metaService = metaService;
    this.permissionService = permissionService;
    this.exportPolicy = exportPolicy;
    this.dictionaryPort = dictionaryPort;
    this.userPort = userPort;
    this.auditService = auditService;
    this.csvWriter = csvWriter;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public WorkRecordExportResult export(
      String tenantId,
      RecordQuery rawQuery,
      List<String> requestedColumnKeys,
      UserPrincipal user) {
    permissionService.requireExport(user);

    RecordQuery query =
        queryService.prepareEffectiveQuery(
            tenantId, rawQuery, user);

    RecordListMeta meta =
        metaService.meta(tenantId, query.templateId());

    List<RecordListColumn> columns =
        resolveColumns(
            meta.columns(), requestedColumnKeys);

    int maxRows = exportPolicy.maxRows();

    List<WorkRecord> records =
        recordRepository.listForExport(
            tenantId,
            query,
            maxRows + 1);

    if (records.size() > maxRows) {
      recordRejectedAudit(
          tenantId,
          query,
          columns,
          user,
          maxRows);

      throw new IllegalArgumentException(
          "导出结果超过 "
              + maxRows
              + " 行，请缩小筛选范围后重试");
    }

    List<ParsedRecord> parsedRecords =
        parseRecords(records);

    LookupContext context =
        buildLookupContext(
            tenantId,
            meta,
            columns,
            parsedRecords);

    List<String> headers =
        columns.stream()
            .map(RecordListColumn::title)
            .toList();

    List<List<String>> rows = new ArrayList<>();

    for (ParsedRecord record : parsedRecords) {
      List<String> row = new ArrayList<>();

      for (RecordListColumn column : columns) {
        row.add(formatCell(record, column, context));
      }

      rows.add(List.copyOf(row));
    }

    byte[] content = csvWriter.write(headers, rows);

    String fileName =
        "work-records-"
            + OffsetDateTime.now(clock)
                .format(FILE_TIME_FORMATTER)
            + ".csv";

    String exportId = Ids.newId();

    recordSuccessAudit(
        tenantId,
        exportId,
        query,
        columns,
        records.size(),
        user);

    return new WorkRecordExportResult(
        fileName,
        content,
        records.size());
  }

  private List<RecordListColumn> resolveColumns(
      List<RecordListColumn> candidates,
      List<String> requestedKeys) {
    Map<String, RecordListColumn> candidateMap =
        new LinkedHashMap<>();

    for (RecordListColumn column : candidates) {
      candidateMap.put(column.key(), column);
    }

    LinkedHashSet<String> selectedKeys =
        new LinkedHashSet<>();

    if (requestedKeys == null || requestedKeys.isEmpty()) {
      candidates.stream()
          .filter(RecordListColumn::visibleByDefault)
          .filter(RecordListColumn::exportable)
          .map(RecordListColumn::key)
          .forEach(selectedKeys::add);
    } else {
      for (String key : requestedKeys) {
        if (key == null || key.isBlank()) {
          continue;
        }
        selectedKeys.add(key);
      }
    }

    if (selectedKeys.isEmpty()) {
      throw new IllegalArgumentException(
          "至少选择一个导出列");
    }

    List<RecordListColumn> result = new ArrayList<>();

    for (String key : selectedKeys) {
      RecordListColumn column = candidateMap.get(key);

      if (column == null) {
        throw new IllegalArgumentException(
            "unknown export column: " + key);
      }

      if (!column.exportable()) {
        throw new IllegalArgumentException(
            "column is not exportable: " + key);
      }

      result.add(column);
    }

    return List.copyOf(result);
  }

  private List<ParsedRecord> parseRecords(
      List<WorkRecord> records) {
    List<ParsedRecord> result = new ArrayList<>();

    for (WorkRecord record : records) {
      JsonNode customData;
      try {
        customData =
            objectMapper.readTree(
                record.customDataJson() == null
                    ? "{}"
                    : record.customDataJson());

        if (customData == null || !customData.isObject()) {
          customData = objectMapper.createObjectNode();
        }
      } catch (Exception ex) {
        customData = objectMapper.createObjectNode();
      }

      result.add(new ParsedRecord(record, customData));
    }

    return List.copyOf(result);
  }

  private LookupContext buildLookupContext(
      String tenantId,
      RecordListMeta meta,
      List<RecordListColumn> columns,
      List<ParsedRecord> records) {
    Map<String, String> templateNames =
        new HashMap<>();

    meta.templates()
        .forEach(
            template ->
                templateNames.put(
                    template.id(), template.name()));

    Set<String> userIds = new HashSet<>();

    for (ParsedRecord parsed : records) {
      addNonBlank(userIds, parsed.record().ownerId());
      addNonBlank(userIds, parsed.record().creatorId());

      for (RecordListColumn column : columns) {
        if (!"custom".equals(column.source())
            || !"user".equals(column.fieldType())
            || column.fieldCode() == null) {
          continue;
        }

        JsonNode value =
            parsed.customData().get(column.fieldCode());

        if (value != null && value.isTextual()) {
          addNonBlank(userIds, value.asText());
        }
      }
    }

    Map<String, String> userNames =
        userPort.displayNames(tenantId, userIds);

    Map<String, Map<String, String>> dictionaryLabels =
        new HashMap<>();

    Map<String, Map<String, String>> staticOptionLabels =
        new HashMap<>();

    for (RecordListColumn column : columns) {
      if (column.dictCode() != null
          && !column.dictCode().isBlank()) {
        dictionaryLabels.computeIfAbsent(
            column.dictCode(),
            dictCode ->
                dictionaryPort.itemLabels(
                    tenantId, dictCode));
      }

      if ("static".equals(column.optionSource())) {
        staticOptionLabels.put(
            column.key(),
            parseStaticOptionLabels(
                column.optionsJson()));
      }
    }

    return new LookupContext(
        Map.copyOf(templateNames),
        Map.copyOf(userNames),
        Map.copyOf(dictionaryLabels),
        Map.copyOf(staticOptionLabels));
  }

  private String formatCell(
      ParsedRecord parsed,
      RecordListColumn column,
      LookupContext context) {
    if ("builtin".equals(column.source())) {
      return formatBuiltin(
          parsed.record(), column.key(), context);
    }

    if (column.fieldCode() == null) {
      return "";
    }

    JsonNode value =
        parsed.customData().get(column.fieldCode());

    return formatDynamic(value, column, context);
  }

  private String formatBuiltin(
      WorkRecord record,
      String key,
      LookupContext context) {
    return switch (key) {
      case "title" -> blank(record.title());
      case "status" -> statusLabel(record.status().value());
      case "templateId" ->
          context.templateNames()
              .getOrDefault(
                  record.templateId(),
                  record.templateId());
      case "ownerId" ->
          displayUser(record.ownerId(), context.userNames());
      case "creatorId" ->
          displayUser(record.creatorId(), context.userNames());
      case "recordTime" ->
          formatTime(record.recordTime());
      case "createdAt" ->
          formatTime(record.createdAt());
      case "updatedAt" ->
          formatTime(record.updatedAt());
      case "templateVersionId" ->
          blank(record.templateVersionId());
      default -> "";
    };
  }

  private String formatDynamic(
      JsonNode value,
      RecordListColumn column,
      LookupContext context) {
    if (value == null || value.isNull()) {
      return "";
    }

    if ("user".equals(column.fieldType())) {
      return displayUser(
          value.asText(), context.userNames());
    }

    if ("datetime".equals(column.fieldType())) {
      return formatDynamicTime(value.asText());
    }

    if ("boolean".equals(column.fieldType())) {
      return value.asBoolean() ? "是" : "否";
    }

    if (value.isArray()) {
      List<String> values = new ArrayList<>();

      for (JsonNode item : value) {
        values.add(
            formatOptionValue(
                item.asText(), column, context));
      }

      return String.join("; ", values);
    }

    if ("select".equals(column.fieldType())) {
      return formatOptionValue(
          value.asText(), column, context);
    }

    if (value.isTextual()
        || value.isNumber()
        || value.isBoolean()) {
      return value.asText();
    }

    return value.toString();
  }

  private String formatOptionValue(
      String raw,
      RecordListColumn column,
      LookupContext context) {
    if (column.dictCode() != null
        && !column.dictCode().isBlank()) {
      return context.dictionaryLabels()
          .getOrDefault(column.dictCode(), Map.of())
          .getOrDefault(raw, raw);
    }

    return context.staticOptionLabels()
        .getOrDefault(column.key(), Map.of())
        .getOrDefault(raw, raw);
  }

  private Map<String, String> parseStaticOptionLabels(
      String optionsJson) {
    if (optionsJson == null || optionsJson.isBlank()) {
      return Map.of();
    }

    try {
      JsonNode root = objectMapper.readTree(optionsJson);

      if (!root.isArray()) {
        return Map.of();
      }

      Map<String, String> result = new LinkedHashMap<>();

      for (JsonNode item : root) {
        if (item.isTextual()
            || item.isNumber()
            || item.isBoolean()) {
          result.put(item.asText(), item.asText());
          continue;
        }

        if (!item.isObject()) {
          continue;
        }

        JsonNode value = item.get("value");
        if (value == null || value.isNull()) {
          value = item.get("itemValue");
        }

        JsonNode label = item.get("label");
        if (label == null || label.isNull()) {
          label = item.get("itemLabel");
        }

        if (value != null && !value.isNull()) {
          result.put(
              value.asText(),
              label == null || label.isNull()
                  ? value.asText()
                  : label.asText());
        }
      }

      return Map.copyOf(result);
    } catch (Exception ex) {
      return Map.of();
    }
  }

  private String displayUser(
      String userId,
      Map<String, String> userNames) {
    if (userId == null || userId.isBlank()) {
      return "";
    }

    return userNames.getOrDefault(userId, userId);
  }

  private String formatDynamicTime(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }

    try {
      return formatTime(OffsetDateTime.parse(value));
    } catch (DateTimeParseException ex) {
      return value;
    }
  }

  private String formatTime(OffsetDateTime value) {
    if (value == null) {
      return "";
    }

    return value
        .atZoneSameInstant(clock.getZone())
        .format(CELL_TIME_FORMATTER);
  }

  private String statusLabel(String status) {
    return switch (status) {
      case "draft" -> "草稿";
      case "processing" -> "处理中";
      case "done" -> "已完成";
      case "archived" -> "已归档";
      default -> status;
    };
  }

  private void recordSuccessAudit(
      String tenantId,
      String exportId,
      RecordQuery query,
      List<RecordListColumn> columns,
      int rowCount,
      UserPrincipal user) {
    auditService.record(
        tenantId,
        null,
        query.templateId(),
        "work_record_export",
        exportId,
        "work_record.record.export",
        actorId(user),
        auditDetail(
            query,
            columns,
            rowCount,
            exportPolicy.maxRows(),
            "success"));
  }

  private void recordRejectedAudit(
      String tenantId,
      RecordQuery query,
      List<RecordListColumn> columns,
      UserPrincipal user,
      int maxRows) {
    auditService.record(
        tenantId,
        null,
        query.templateId(),
        "work_record_export",
        Ids.newId(),
        "work_record.record.export_rejected",
        actorId(user),
        auditDetail(
            query,
            columns,
            maxRows + 1,
            maxRows,
            "limit_exceeded"));
  }

  private String auditDetail(
      RecordQuery query,
      List<RecordListColumn> columns,
      int rowCount,
      int maxRows,
      String result) {
    Map<String, Object> detail = new LinkedHashMap<>();

    detail.put("result", result);
    detail.put("rowCount", rowCount);
    detail.put("maxRows", maxRows);
    detail.put(
        "columns",
        columns.stream()
            .map(RecordListColumn::key)
            .toList());
    detail.put("templateId", query.templateId());
    detail.put(
        "templateVersionId",
        query.templateVersionId());
    detail.put("statuses", query.statuses());
    detail.put("quickView", query.quickView());
    detail.put(
        "dynamicFilterCount",
        query.dynamicFilters() == null
            ? 0
            : query.dynamicFilters().size());

    try {
      return objectMapper.writeValueAsString(detail);
    } catch (Exception ex) {
      return "{}";
    }
  }

  private void addNonBlank(
      Collection<String> target,
      String value) {
    if (value != null && !value.isBlank()) {
      target.add(value);
    }
  }

  private String actorId(UserPrincipal user) {
    return user == null
        || user.id() == null
        || user.id().isBlank()
        ? "system"
        : user.id();
  }

  private String blank(String value) {
    return value == null ? "" : value;
  }

  private record ParsedRecord(
      WorkRecord record,
      JsonNode customData) {}

  private record LookupContext(
      Map<String, String> templateNames,
      Map<String, String> userNames,
      Map<String, Map<String, String>> dictionaryLabels,
      Map<String, Map<String, String>> staticOptionLabels) {}
}
