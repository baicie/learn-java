package io.aegisops.workrecord.application.service;

import io.aegisops.common.outbox.OutboxMessage;
import io.aegisops.common.outbox.OutboxWriter;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.ObjectStorageUrlSigner;
import io.aegisops.workrecord.domain.model.UploadSession;
import io.aegisops.workrecord.domain.model.WorkRecordAttachment;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AttachmentObjectService {
  private final UploadSessionService uploads;
  private final ObjectStorageUrlSigner signer;
  private final OutboxWriter outbox;
  private final Clock clock;

  public AttachmentObjectService(
      UploadSessionService uploads,
      ObjectStorageUrlSigner signer,
      OutboxWriter outbox,
      @Qualifier("workRecordClock") Clock clock) {
    this.uploads = uploads;
    this.signer = signer;
    this.outbox = outbox;
    this.clock = clock;
  }

  public UploadSessionService.PreparedUpload prepare(
      String tenantId, UserPrincipal principal, UploadSessionService.PrepareUpload request) {
    return uploads.prepareAttachment(tenantId, principal, request);
  }

  public UploadSession consume(String tenantId, String uploadId, UserPrincipal principal) {
    return uploads.consumeAttachment(tenantId, uploadId, principal);
  }

  public WorkRecordAttachmentService.Download download(WorkRecordAttachment attachment) {
    OffsetDateTime now = OffsetDateTime.now(clock);
    return new WorkRecordAttachmentService.Download(
        signer.presignedGet(attachment.objectKey(), Duration.ofMinutes(5)),
        attachment.fileName(),
        attachment.contentType(),
        now.plusMinutes(5));
  }

  public void enqueue(String tenantId, WorkRecordAttachment attachment, String action) {
    outbox.enqueue(
        new OutboxMessage(
            tenantId,
            "worker",
            "work-record-attachment-storage",
            Map.of(
                "tenantId",
                tenantId,
                "attachmentId",
                attachment.id(),
                "action",
                action,
                "objectKey",
                attachment.objectKey()),
            "attachment:" + attachment.id() + ":" + action,
            5,
            OffsetDateTime.now(clock)));
  }
}
