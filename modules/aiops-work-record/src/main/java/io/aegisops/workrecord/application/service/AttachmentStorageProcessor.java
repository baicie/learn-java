package io.aegisops.workrecord.application.service;

import io.aegisops.common.exception.ResourceNotFoundException;
import io.aegisops.workrecord.application.port.ObjectStoragePort;
import io.aegisops.workrecord.application.port.WorkRecordAttachmentRepository;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "aiops.runtime", name = "app", havingValue = "worker")
public class AttachmentStorageProcessor {
  private final WorkRecordAttachmentRepository attachments;
  private final ObjectStoragePort storage;

  public AttachmentStorageProcessor(
      WorkRecordAttachmentRepository attachments, ObjectStoragePort storage) {
    this.attachments = attachments;
    this.storage = storage;
  }

  public void process(String tenantId, String attachmentId, String action, String objectKey) {
    requireObjectKey(tenantId, objectKey);
    if ("delete".equals(action)) {
      storage.delete(objectKey);
      return;
    }
    if (!"scan".equals(action)) {
      throw new IllegalArgumentException("unknown attachment storage action");
    }
    var attachment =
        attachments
            .find(tenantId, attachmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("attachment not found: " + attachmentId));
    if (!"pending_scan".equals(attachment.status())) {
      return;
    }
    if (!objectKey.equals(attachment.objectKey())
        || storage.stat(objectKey).sizeBytes() != attachment.sizeBytes()) {
      attachments.quarantine(tenantId, attachmentId);
      return;
    }
    try (InputStream raw = storage.get(objectKey, attachment.sizeBytes());
        BufferedInputStream input = new BufferedInputStream(raw)) {
      input.mark(4);
      byte[] header = input.readNBytes(4);
      input.reset();
      if (isExecutable(header)) {
        attachments.quarantine(tenantId, attachmentId);
        return;
      }
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (DigestInputStream hashing = new DigestInputStream(input, digest)) {
        hashing.transferTo(OutputStream.nullOutputStream());
      }
      if (!attachments.markReady(
          tenantId, attachmentId, HexFormat.of().formatHex(digest.digest()))) {
        throw new IllegalStateException("attachment scan state changed");
      }
    } catch (java.io.IOException | java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException("failed to scan attachment", ex);
    }
  }

  private static boolean isExecutable(byte[] header) {
    return header.length >= 2
        && ((header[0] == 'M' && header[1] == 'Z')
            || (header[0] == '#' && header[1] == '!')
            || (header.length >= 4
                && header[0] == 0x7f
                && header[1] == 'E'
                && header[2] == 'L'
                && header[3] == 'F'));
  }

  private static void requireObjectKey(String tenantId, String objectKey) {
    if (tenantId == null
        || objectKey == null
        || !objectKey.startsWith(tenantId + "/attachments/")) {
      throw new IllegalArgumentException("invalid attachment object key");
    }
  }
}
