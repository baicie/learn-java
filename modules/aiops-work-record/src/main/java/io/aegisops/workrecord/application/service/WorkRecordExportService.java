package io.aegisops.workrecord.application.service;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordExportService {
  private static final int MAX_ROWS = 5000;

  private final WorkRecordRepository repository;
  private final WorkRecordPermissionService permissionService;
  private final WorkRecordAuditService auditService;

  public WorkRecordExportService(
      WorkRecordRepository repository,
      WorkRecordPermissionService permissionService,
      WorkRecordAuditService auditService) {
    this.repository = repository;
    this.permissionService = permissionService;
    this.auditService = auditService;
  }

  public byte[] exportCsv(String tenantId, RecordQuery query, UserPrincipal user) {
    permissionService.requireExport(user);

    List<WorkRecord> rows = repository.listForExport(tenantId, query, MAX_ROWS);
    StringBuilder csv = new StringBuilder();
    csv.append("id,title,status,templateId,templateVersionId,ownerId,creatorId,recordTime,createdAt\n");
    for (WorkRecord row : rows) {
      csv.append(escape(row.id())).append(',');
      csv.append(escape(row.title())).append(',');
      csv.append(escape(row.status().value())).append(',');
      csv.append(escape(row.templateId())).append(',');
      csv.append(escape(row.templateVersionId())).append(',');
      csv.append(escape(row.ownerId())).append(',');
      csv.append(escape(row.creatorId())).append(',');
      csv.append(escape(String.valueOf(row.recordTime()))).append(',');
      csv.append(escape(String.valueOf(row.createdAt()))).append('\n');
    }

    auditService.record(
        tenantId,
        null,
        null,
        "work_record_export",
        "csv",
        "work_record.record.export",
        user == null ? "system" : user.id(),
        "{\"rows\":" + rows.size() + "}");

    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  private String escape(String value) {
    if (value == null) {
      return "";
    }
    String safe = value;
    if (safe.startsWith("=")
        || safe.startsWith("+")
        || safe.startsWith("-")
        || safe.startsWith("@")) {
      safe = "'" + safe;
    }
    if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
      safe = "\"" + safe.replace("\"", "\"\"") + "\"";
    }
    return safe;
  }
}
