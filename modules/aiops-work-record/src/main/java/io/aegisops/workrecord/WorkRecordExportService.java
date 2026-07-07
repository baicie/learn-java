package io.aegisops.workrecord;

import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import io.aegisops.security.UserPrincipal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordExportService {
  private static final String HEADER = "id,title,status,ownerId,creatorId,recordTime";
  public static final int MAX_EXPORT_ROWS = 5000;

  private final WorkRecordRepository repository;
  private final AuditService audit;

  public WorkRecordExportService(WorkRecordRepository repository, AuditService audit) {
    this.repository = repository;
    this.audit = audit;
  }

  public byte[] exportCsv(String tenantId, String ownerId, String status, UserPrincipal actor) {
    boolean canReadAll = actor != null && hasAuthority(actor, "work-record:read:all");
    String effectiveOwner = ownerId;
    if (!canReadAll) {
      // 非全量权限用户：忽略传入 ownerId，仅导出以自己作为 owner 或 creator 的记录。
      effectiveOwner = actor == null ? null : actor.id();
    }
    List<WorkRecord> records;
    String scoped;
    if (canReadAll) {
      records =
          repository.export(
              tenantId, blankToNull(effectiveOwner), blankToNull(status), MAX_EXPORT_ROWS);
      scoped = "all";
    } else if (effectiveOwner == null) {
      records = List.of();
      scoped = "self";
    } else {
      records =
          repository.exportForUser(tenantId, effectiveOwner, blankToNull(status), MAX_EXPORT_ROWS);
      scoped = "self";
    }
    boolean truncated = records.size() >= MAX_EXPORT_ROWS;
    byte[] body = renderCsv(records);
    audit.record(
        new AuditRecordCommand(
            tenantId,
            actor == null ? "system" : actor.id(),
            "work_record.record.export",
            "wr_record",
            "csv",
            "{\"count\":"
                + records.size()
                + ",\"scopedBySelf\":"
                + (!canReadAll)
                + ",\"truncated\":"
                + truncated
                + ",\"maxRows\":"
                + MAX_EXPORT_ROWS
                + ",\"scope\":\""
                + scoped
                + "\"}"));
    return body;
  }

  private byte[] renderCsv(List<WorkRecord> records) {
    StringBuilder csv = new StringBuilder();
    csv.append(HEADER).append('\n');
    for (WorkRecord record : records) {
      csv.append(escape(record.id()))
          .append(',')
          .append(escape(record.title()))
          .append(',')
          .append(escape(record.status()))
          .append(',')
          .append(escape(record.ownerId()))
          .append(',')
          .append(escape(record.creatorId()))
          .append(',')
          .append(escape(record.recordTime() == null ? "" : record.recordTime().toString()))
          .append('\n');
    }
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  private boolean hasAuthority(UserPrincipal user, String authority) {
    return user.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
  }

  private String escape(String value) {
    if (value == null) {
      return "";
    }
    String escaped = value.replace("\"", "\"\"");
    if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
      return "\"" + escaped + "\"";
    }
    return escaped;
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
