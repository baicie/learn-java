package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.common.exception.ResourceNotFoundException;
import io.aegisops.common.outbox.OutboxWriter;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.ObjectStorageUrlSigner;
import io.aegisops.workrecord.application.port.WorkRecordAttachmentRepository;
import io.aegisops.workrecord.domain.model.WorkRecord;
import io.aegisops.workrecord.domain.model.WorkRecordAttachment;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class WorkRecordAttachmentServiceTest {
  @Test
  void deleteAuditsSuccessfulMutation() {
    WorkRecordQueryService records = mock(WorkRecordQueryService.class);
    WorkRecordPermissionService permissions = mock(WorkRecordPermissionService.class);
    WorkRecordAttachmentRepository attachments = mock(WorkRecordAttachmentRepository.class);
    WorkRecordAuditService audit = mock(WorkRecordAuditService.class);
    WorkRecord record = mock(WorkRecord.class);
    UserPrincipal principal = principalWithAttachmentPermission();
    when(record.templateId()).thenReturn("template-1");
    when(records.get("tenant-1", "record-1", principal)).thenReturn(record);
    when(attachments.find("tenant-1", "attachment-1"))
        .thenReturn(java.util.Optional.of(attachment()));
    when(attachments.markDeleted("tenant-1", "attachment-1")).thenReturn(true);
    var service =
        new WorkRecordAttachmentService(
            records, permissions, attachments, objects(mock(ObjectStorageUrlSigner.class)), audit);

    service.delete("tenant-1", "record-1", "attachment-1", principal);

    verify(audit)
        .record(
            "tenant-1",
            "record-1",
            "template-1",
            "work_record_attachment",
            "attachment-1",
            "work_record.attachment.delete",
            "user-1",
            "{\"recordId\":\"record-1\"}");
  }

  @Test
  void downloadRejectsAttachmentFromAnotherRecord() {
    WorkRecordAttachmentRepository attachments = mock(WorkRecordAttachmentRepository.class);
    ObjectStorageUrlSigner signer = mock(ObjectStorageUrlSigner.class);
    WorkRecordAttachment attachment = attachment();
    when(attachments.find("tenant-1", "attachment-1"))
        .thenReturn(java.util.Optional.of(attachment));
    var service = service(mock(WorkRecordQueryService.class), attachments, signer);

    assertThatThrownBy(
            () ->
                service.download(
                    "tenant-1", "record-2", "attachment-1", principalWithAttachmentPermission()))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(signer, never())
        .presignedGet(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(java.time.Duration.class));
  }

  @Test
  void deleteRequiresAttachmentPermissionInApplicationService() {
    WorkRecordQueryService records = mock(WorkRecordQueryService.class);
    WorkRecordPermissionService permissions = mock(WorkRecordPermissionService.class);
    WorkRecordAttachmentRepository attachments = mock(WorkRecordAttachmentRepository.class);
    WorkRecord record = mock(WorkRecord.class);
    WorkRecordAttachment attachment = attachment();
    when(attachments.find("tenant-1", "attachment-1"))
        .thenReturn(java.util.Optional.of(attachment));
    when(records.get("tenant-1", "record-1", principalWithoutAttachmentPermission()))
        .thenReturn(record);
    var service = service(records, permissions, attachments, mock(ObjectStorageUrlSigner.class));

    assertThatThrownBy(
            () ->
                service.delete(
                    "tenant-1", "record-1", "attachment-1", principalWithoutAttachmentPermission()))
        .isInstanceOf(AccessDeniedException.class);
    verify(attachments, never()).markDeleted("tenant-1", "attachment-1");
  }

  private static WorkRecordAttachmentService service(
      WorkRecordQueryService records,
      WorkRecordAttachmentRepository attachments,
      ObjectStorageUrlSigner signer) {
    return service(records, mock(WorkRecordPermissionService.class), attachments, signer);
  }

  private static WorkRecordAttachmentService service(
      WorkRecordQueryService records,
      WorkRecordPermissionService permissions,
      WorkRecordAttachmentRepository attachments,
      ObjectStorageUrlSigner signer) {
    return new WorkRecordAttachmentService(
        records, permissions, attachments, objects(signer), mock(WorkRecordAuditService.class));
  }

  private static AttachmentObjectService objects(ObjectStorageUrlSigner signer) {
    return new AttachmentObjectService(
        mock(UploadSessionService.class),
        signer,
        mock(OutboxWriter.class),
        Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC));
  }

  private static WorkRecordAttachment attachment() {
    return new WorkRecordAttachment(
        "attachment-1",
        "record-1",
        "upload-1",
        "tenant-1/attachments/file",
        "evidence.txt",
        "text/plain",
        4,
        null,
        "ready",
        "user-1",
        OffsetDateTime.now());
  }

  private static UserPrincipal principalWithAttachmentPermission() {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
        Set.of(),
        Set.of("work-record:attachment", "work-record:read:self"),
        Map.of());
  }

  private static UserPrincipal principalWithoutAttachmentPermission() {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
        Set.of(),
        Set.of("work-record:edit:self", "work-record:read:self"),
        Map.of());
  }
}
