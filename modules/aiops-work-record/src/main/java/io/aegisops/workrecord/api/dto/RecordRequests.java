package io.aegisops.workrecord.api.dto;

import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import java.time.OffsetDateTime;
import java.util.List;

public final class RecordRequests {
  private RecordRequests() {}

  public record CreateRecordRequest(
      String templateId,
      String templateVersionId,
      String title,
      String status,
      String ownerId,
      String recordTime,
      String builtinDataJson,
      String customDataJson) {}

  public record UpdateRecordRequest(
      String title,
      String status,
      String ownerId,
      String recordTime,
      String builtinDataJson,
      String customDataJson) {}

  public record RecordQueryRequest(
      Integer page,
      Integer pageSize,
      String templateId,
      String templateVersionId,
      List<String> statuses,
      String keyword,
      OffsetDateTime recordTimeFrom,
      OffsetDateTime recordTimeTo,
      String creatorId,
      String ownerId,
      String quickView,
      String dynamicFilters,
      String sortBy,
      String sortDir,
      Integer workdayCount) {}

  public record ExportRecordRequest(
      String templateId,
      String templateVersionId,
      List<String> statuses,
      String keyword,
      OffsetDateTime recordTimeFrom,
      OffsetDateTime recordTimeTo,
      String creatorId,
      String ownerId,
      String quickView,
      List<RecordDynamicFilter> dynamicFilters,
      String sortBy,
      String sortDir,
      Integer workdayCount,
      List<String> columns) {}
}
