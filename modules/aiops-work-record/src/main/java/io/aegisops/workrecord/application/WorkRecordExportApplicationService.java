package io.aegisops.workrecord.application;

import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.domain.model.DynamicFieldFilter;
import io.aegisops.workrecord.domain.model.WorkRecord;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.rule.WorkRecordFilterValidator;
import io.aegisops.workrecord.infrastructure.config.WorkRecordProperties;
import io.aegisops.workrecord.infrastructure.persistence.WorkRecordFieldRepository;
import io.aegisops.workrecord.infrastructure.persistence.WorkRecordRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordExportApplicationService {
  /** 兜底默认导出行数；真实值由 {@link WorkRecordProperties.Export#getMaxRows()} 决定。 */
  public static final int DEFAULT_MAX_EXPORT_ROWS = 5000;

  private final WorkRecordRepository repository;
  private final WorkRecordFieldRepository fieldRepository;
  private final AuditService audit;
  private final WorkRecordProperties properties;

  public WorkRecordExportApplicationService(
      WorkRecordRepository repository,
      WorkRecordFieldRepository fieldRepository,
      AuditService audit,
      WorkRecordProperties properties) {
    this.repository = repository;
    this.fieldRepository = fieldRepository;
    this.audit = audit;
    this.properties = properties;
  }

  /** 当前生效的最大导出行数（来自配置）。 */
  public int getMaxExportRows() {
    int configured = properties.getExport().getMaxRows();
    return configured > 0 ? configured : DEFAULT_MAX_EXPORT_ROWS;
  }

  /**
   * 导出 CSV（支持动态筛选和自定义列）。
   *
   * <p>列过滤规则：
   *
   * <ul>
   *   <li>内建列（id / title / status / templateId / ownerId / creatorId / recordTime /
   *       createdAt）始终允许导出
   *   <li>动态列必须存在于模板字段中且 {@code exportable=true}，否则抛出 SecurityException
   * </ul>
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

    // 校验 columns 中所有动态列必须 exportable=true
    List<String> resolvedColumns = resolveColumns(tenantId, templateId, columns);

    // 查询数据
    int maxRows = getMaxExportRows();
    long total;
    List<WorkRecord> records;

    if (canReadAll) {
      total =
          repository.countWithFilters(
              tenantId,
              templateId,
              status,
              keyword,
              recordTimeFrom,
              recordTimeTo,
              filters,
              null,
              null);
      records =
          repository.pageForExport(
              tenantId,
              maxRows,
              templateId,
              status,
              keyword,
              recordTimeFrom,
              recordTimeTo,
              filters);
    } else {
      String userId = actor.id();
      total =
          repository.countForExportUser(
              tenantId, userId, templateId, status, keyword, recordTimeFrom, recordTimeTo, filters);
      records =
          repository.pageForExportUser(
              tenantId,
              userId,
              maxRows,
              templateId,
              status,
              keyword,
              recordTimeFrom,
              recordTimeTo,
              filters);
    }

    boolean truncated = total > maxRows;
    if (truncated) {
      records = records.subList(0, Math.min(records.size(), maxRows));
    }

    byte[] body = renderCsv(records, resolvedColumns);

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
                + maxRows
                + "}"));

    return body;
  }

  /** 内建列始终允许导出，动态列必须 exportable=true。 */
  private static final List<String> BUILTIN_COLUMNS =
      List.of(
          "id", "title", "status", "templateId", "ownerId", "creatorId", "recordTime", "createdAt");

  /**
   * 解析导出列：
   *
   * <ul>
   *   <li>columns 为 null → 返回默认内建列
   *   <li>columns 非空 → 内建列直接放行，动态列按模板 exportable 校验
   * </ul>
   */
  private List<String> resolveColumns(String tenantId, String templateId, List<String> columns) {
    if (columns == null || columns.isEmpty()) {
      return BUILTIN_COLUMNS;
    }
    // 没有任何动态列 → 直接返回
    if (templateId == null || templateId.isBlank()) {
      return columns;
    }
    List<WorkRecordField> fields = fieldRepository.list(tenantId, templateId);
    java.util.Map<String, WorkRecordField> fieldByCode = new java.util.HashMap<>();
    for (WorkRecordField f : fields) {
      fieldByCode.put(f.fieldCode(), f);
    }
    for (String col : columns) {
      if (BUILTIN_COLUMNS.contains(col)) {
        continue;
      }
      WorkRecordField f = fieldByCode.get(col);
      if (f == null) {
        throw new IllegalArgumentException("column not in template: " + col);
      }
      if (!f.exportable()) {
        throw new SecurityException("column is not exportable: " + col);
      }
    }
    return columns;
  }

  private byte[] renderCsv(List<WorkRecord> records, List<String> columns) {
    StringBuilder csv = new StringBuilder();

    // CSV header
    List<String> headers =
        columns != null && !columns.isEmpty()
            ? columns
            : List.of(
                "id",
                "title",
                "status",
                "templateId",
                "ownerId",
                "creatorId",
                "recordTime",
                "createdAt");
    csv.append(String.join(",", headers)).append('\n');

    // CSV rows
    for (WorkRecord record : records) {
      csv.append(escape(record.id()))
          .append(',')
          .append(escape(record.title()))
          .append(',')
          .append(escape(record.status()))
          .append(',')
          .append(escape(record.templateId()))
          .append(',')
          .append(escape(record.ownerId()))
          .append(',')
          .append(escape(record.creatorId()))
          .append(',')
          .append(escape(record.recordTime() == null ? "" : record.recordTime().toString()))
          .append(',')
          .append(escape(record.createdAt() == null ? "" : record.createdAt().toString()))
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
    if (escaped.contains(",")
        || escaped.contains("\n")
        || escaped.contains("\"")
        || escaped.startsWith("=")
        || escaped.startsWith("+")
        || escaped.startsWith("-")
        || escaped.startsWith("@")) {
      return "\"" + escaped + "\"";
    }
    return escaped;
  }
}
