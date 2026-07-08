package io.aegisops.workrecord;

import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import io.aegisops.security.UserPrincipal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordExportService {
  public static final int MAX_EXPORT_ROWS = 5000;

  private final WorkRecordRepository repository;
  private final WorkRecordFieldRepository fieldRepository;
  private final AuditService audit;

  public WorkRecordExportService(
      WorkRecordRepository repository,
      WorkRecordFieldRepository fieldRepository,
      AuditService audit) {
    this.repository = repository;
    this.fieldRepository = fieldRepository;
    this.audit = audit;
  }

  /**
   * 导出 CSV（支持动态筛选和自定义列）。
   *
   * @param tenantId 租户 ID
   * @param templateId 模板 ID（可选）
   * @param status 状态列表（可选）
   * @param keyword 标题关键字（可选）
   * @param recordTimeFrom 记录时间起（可选）
   * @param recordTimeTo 记录时间止（可选）
   * @param filters 动态字段筛选（可选）
   * @param columns 导出的列（可选，null 表示全部列）
   * @param actor 当前用户
   * @return CSV 字节数组
   */
  public byte[] exportCsv(
      String tenantId,
      String templateId,
      List<String> status,
      String keyword,
      OffsetDateTime recordTimeFrom,
      OffsetDateTime recordTimeTo,
      List<DynamicFieldFilter> filters,
      List<String> columns,
      UserPrincipal actor) {

    boolean canReadAll = actor != null && hasAuthority(actor, "work-record:read:all");
    boolean canExport = actor != null && hasAuthority(actor, "work-record:export");

    if (!canExport) {
      throw new SecurityException("work-record:export authority is required");
    }

    // 校验动态字段筛选
    if (filters != null && !filters.isEmpty()) {
      if (templateId == null || templateId.isBlank()) {
        throw new IllegalArgumentException("templateId is required for dynamic filters");
      }
      List<WorkRecordField> fields = fieldRepository.list(tenantId, templateId);
      WorkRecordFilterValidator.validate(fields, filters);
    }

    // 查询数据
    int pageSize = MAX_EXPORT_ROWS;
    long total;
    List<WorkRecord> records;

    if (canReadAll) {
      total = repository.countWithFilters(tenantId, templateId, status, keyword,
          recordTimeFrom, recordTimeTo, filters, null, null);
      records = repository.pageForExport(
          tenantId, pageSize, templateId, status, keyword, recordTimeFrom, recordTimeTo, filters);
    } else {
      String userId = actor.id();
      total = repository.countForExportUser(tenantId, userId, templateId, status, keyword,
          recordTimeFrom, recordTimeTo, filters);
      records = repository.pageForExportUser(
          tenantId, userId, pageSize, templateId, status, keyword, recordTimeFrom, recordTimeTo, filters);
    }

    boolean truncated = total > MAX_EXPORT_ROWS;
    if (truncated) {
      records = records.subList(0, Math.min(records.size(), MAX_EXPORT_ROWS));
    }

    byte[] body = renderCsv(records, columns);

    audit.record(
        new AuditRecordCommand(
            tenantId,
            actor == null ? "system" : actor.id(),
            "work_record.record.export",
            "wr_record",
            "csv",
            "{\"count\":"
                + records.size()
                + ",\"total\":"
                + total
                + ",\"truncated\":"
                + truncated
                + ",\"maxRows\":"
                + MAX_EXPORT_ROWS
                + "}"));

    return body;
  }

  private byte[] renderCsv(List<WorkRecord> records, List<String> columns) {
    StringBuilder csv = new StringBuilder();

    // CSV header
    List<String> headers = columns != null && !columns.isEmpty()
        ? columns
        : List.of("id", "title", "status", "templateId", "ownerId", "creatorId", "recordTime", "createdAt");
    csv.append(String.join(",", headers)).append('\n');

    // CSV rows
    for (WorkRecord record : records) {
      csv.append(escape(record.id()))
          .append(',').append(escape(record.title()))
          .append(',').append(escape(record.status()))
          .append(',').append(escape(record.templateId()))
          .append(',').append(escape(record.ownerId()))
          .append(',').append(escape(record.creatorId()))
          .append(',').append(escape(record.recordTime() == null ? "" : record.recordTime().toString()))
          .append(',').append(escape(record.createdAt() == null ? "" : record.createdAt().toString()))
          .append('\n');
    }

    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  private boolean hasAuthority(UserPrincipal user, String authority) {
    return user != null
        && user.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
  }

  private String escape(String value) {
    if (value == null) {
      return "";
    }
    String escaped = value.replace("\"", "\"\"");
    if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")
        || escaped.startsWith("=") || escaped.startsWith("+") || escaped.startsWith("-")
        || escaped.startsWith("@")) {
      return "\"" + escaped + "\"";
    }
    return escaped;
  }
}
