package io.aegisops.workrecord.application.service;

import io.aegisops.common.exception.ResourceNotFoundException;
import io.aegisops.security.PermissionCodes;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.WorkRecordAttachmentRepository;
import io.aegisops.workrecord.domain.model.WorkRecordAttachment;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkRecordAttachmentService {
  private final WorkRecordQueryService records;
  private final WorkRecordPermissionService permissions;
  private final WorkRecordAttachmentRepository attachments;
  private final AttachmentObjectService objects;
  private final WorkRecordAuditService audit;

  public WorkRecordAttachmentService(
      WorkRecordQueryService records,
      WorkRecordPermissionService permissions,
      WorkRecordAttachmentRepository attachments,
      AttachmentObjectService objects,
      WorkRecordAuditService audit) {
    this.records = records;
    this.permissions = permissions;
    this.attachments = attachments;
    this.objects = objects;
    this.audit = audit;
  }

  public List<WorkRecordAttachment> list(
      String tenantId, String recordId, UserPrincipal principal) {
    records.get(tenantId, recordId, principal);
    return attachments.list(tenantId, recordId);
  }

  public UploadSessionService.PreparedUpload prepare(
      String tenantId,
      String recordId,
      UploadSessionService.PrepareUpload request,
      UserPrincipal principal) {
    requirePermission(tenantId, principal);
    var record = records.get(tenantId, recordId, principal);
    permissions.requireEdit(principal, record);
    return objects.prepare(tenantId, principal, request);
  }

  @Transactional
  public WorkRecordAttachment complete(
      String tenantId, String recordId, String uploadId, UserPrincipal principal) {
    requirePermission(tenantId, principal);
    var record = records.get(tenantId, recordId, principal);
    permissions.requireEdit(principal, record);
    var upload = objects.consume(tenantId, uploadId, principal);
    WorkRecordAttachment attachment =
        attachments.create(tenantId, recordId, upload, principal.id());
    objects.enqueue(tenantId, attachment, "scan");
    audit.record(
        tenantId,
        recordId,
        record.templateId(),
        "work_record_attachment",
        attachment.id(),
        "work_record.attachment.create",
        principal.id(),
        "{\"status\":\"pending_scan\"}");
    return attachment;
  }

  public Download download(
      String tenantId, String recordId, String attachmentId, UserPrincipal principal) {
    WorkRecordAttachment attachment = requireFound(tenantId, attachmentId);
    requireRecord(recordId, attachment);
    records.get(tenantId, recordId, principal);
    if (!"ready".equals(attachment.status())) {
      throw new IllegalStateException("attachment is not ready");
    }
    return objects.download(attachment);
  }

  @Transactional
  public void delete(
      String tenantId, String recordId, String attachmentId, UserPrincipal principal) {
    requirePermission(tenantId, principal);
    WorkRecordAttachment attachment = requireFound(tenantId, attachmentId);
    requireRecord(recordId, attachment);
    var record = records.get(tenantId, recordId, principal);
    permissions.requireEdit(principal, record);
    if (!principal.id().equals(attachment.uploadedBy())
        && !principal.hasPermission(PermissionCodes.WORK_RECORD_ATTACHMENT_MODERATE)) {
      throw new AccessDeniedException("not allowed to delete attachment");
    }
    if (attachments.markDeleted(tenantId, attachmentId)) {
      objects.enqueue(tenantId, attachment, "delete");
      audit.record(
          tenantId,
          recordId,
          record.templateId(),
          "work_record_attachment",
          attachmentId,
          "work_record.attachment.delete",
          principal.id(),
          "{\"recordId\":\"" + recordId + "\"}");
    }
  }

  private WorkRecordAttachment requireFound(String tenantId, String id) {
    return attachments
        .find(tenantId, id)
        .orElseThrow(() -> new ResourceNotFoundException("attachment not found: " + id));
  }

  private static void requireRecord(String recordId, WorkRecordAttachment attachment) {
    if (!recordId.equals(attachment.recordId())) {
      throw new ResourceNotFoundException("attachment not found: " + attachment.id());
    }
  }

  private static void requirePermission(String tenantId, UserPrincipal principal) {
    if (principal == null
        || !tenantId.equals(principal.tenantId())
        || !principal.hasPermission(PermissionCodes.WORK_RECORD_ATTACHMENT)) {
      throw new AccessDeniedException("not allowed to manage attachments");
    }
  }

  public record Download(
      String url, String fileName, String contentType, OffsetDateTime expiresAt) {}
}
