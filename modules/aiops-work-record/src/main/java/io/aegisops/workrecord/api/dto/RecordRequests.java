package io.aegisops.workrecord.api.dto;

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
      String ownerId) {}
}