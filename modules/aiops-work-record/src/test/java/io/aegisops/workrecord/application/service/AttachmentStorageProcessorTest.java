package io.aegisops.workrecord.application.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.workrecord.application.port.ObjectStoragePort;
import io.aegisops.workrecord.application.port.WorkRecordAttachmentRepository;
import io.aegisops.workrecord.domain.model.WorkRecordAttachment;
import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AttachmentStorageProcessorTest {
  @Test
  void quarantinesExecutableContent() {
    WorkRecordAttachmentRepository attachments = mock(WorkRecordAttachmentRepository.class);
    ObjectStoragePort storage = mock(ObjectStoragePort.class);
    byte[] content = {'M', 'Z', 0, 0};
    WorkRecordAttachment attachment = attachment(content.length);
    when(attachments.find("tenant-1", "attachment-1")).thenReturn(Optional.of(attachment));
    when(storage.stat(attachment.objectKey()))
        .thenReturn(
            new ObjectStoragePort.StoredObject(attachment.objectKey(), content.length, null));
    when(storage.get(attachment.objectKey(), content.length))
        .thenReturn(new ByteArrayInputStream(content));

    new AttachmentStorageProcessor(attachments, storage)
        .process("tenant-1", "attachment-1", "scan", attachment.objectKey());

    verify(attachments).quarantine("tenant-1", "attachment-1");
  }

  private static WorkRecordAttachment attachment(long size) {
    return new WorkRecordAttachment(
        "attachment-1",
        "record-1",
        "upload-1",
        "tenant-1/attachments/file",
        "evidence.exe",
        "application/octet-stream",
        size,
        null,
        "pending_scan",
        "user-1",
        OffsetDateTime.now());
  }
}
