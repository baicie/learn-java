package io.aegisops.workrecord.application.service;

import io.aegisops.common.exception.ConflictException;
import io.aegisops.common.id.Ids;
import io.aegisops.security.PermissionCodes;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.ObjectStorageUrlSigner;
import io.aegisops.workrecord.application.port.UploadSessionRepository;
import io.aegisops.workrecord.domain.model.UploadSession;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class UploadSessionService {
  private static final long MAX_EXCEL_BYTES = 20L * 1024L * 1024L;
  private static final String XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  private static final String PURPOSE = "excel_import";
  private static final String ATTACHMENT_PURPOSE = "attachment";
  private static final java.util.Set<String> BLOCKED_ATTACHMENT_TYPES =
      java.util.Set.of(
          "application/x-msdownload",
          "application/x-sh",
          "application/x-bat",
          "application/java-archive");

  private final UploadSessionRepository repository;
  private final ObjectStorageUrlSigner signer;
  private final Clock clock;

  public UploadSessionService(
      UploadSessionRepository repository,
      ObjectStorageUrlSigner signer,
      @Qualifier("workRecordClock") Clock clock) {
    this.repository = repository;
    this.signer = signer;
    this.clock = clock;
  }

  public PreparedUpload prepareExcelImport(
      String tenantId, UserPrincipal user, PrepareUpload request) {
    requirePermission(user);
    validateTenant(tenantId, user);
    PrepareUpload safe = validate(request);
    OffsetDateTime now = OffsetDateTime.now(clock);
    OffsetDateTime expiresAt = now.plusMinutes(10);
    String id = Ids.newId();
    String objectKey = tenantId + "/imports/" + id + "/source.xlsx";
    String uploadUrl = signer.presignedPut(objectKey, safe.contentType(), Duration.ofMinutes(5));
    UploadSession session =
        repository.insert(
            new UploadSession(
                id,
                tenantId,
                user.id(),
                PURPOSE,
                objectKey,
                safe.originalFileName(),
                safe.contentType(),
                safe.sizeBytes(),
                "prepared",
                expiresAt,
                null,
                now,
                now));
    return new PreparedUpload(session.id(), uploadUrl, session.expiresAt());
  }

  public UploadSession consumeExcelImport(String tenantId, String uploadId, UserPrincipal user) {
    requirePermission(user);
    validateTenant(tenantId, user);
    if (uploadId == null || uploadId.isBlank()) {
      throw new IllegalArgumentException("uploadId is required");
    }
    return repository
        .consumePrepared(tenantId, uploadId.trim(), user.id(), PURPOSE, OffsetDateTime.now(clock))
        .orElseThrow(() -> new ConflictException("upload session is invalid, expired or consumed"));
  }

  public PreparedUpload prepareAttachment(
      String tenantId, UserPrincipal user, PrepareUpload request) {
    requireAttachmentPermission(user);
    validateTenant(tenantId, user);
    if (request == null) {
      throw new IllegalArgumentException("upload request is required");
    }
    String fileName = safeFileName(request.originalFileName());
    String contentType = request.contentType() == null ? "" : request.contentType().trim();
    if (contentType.isEmpty()
        || contentType.length() > 128
        || BLOCKED_ATTACHMENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException("attachment content type is not allowed");
    }
    if (request.sizeBytes() < 1 || request.sizeBytes() > MAX_EXCEL_BYTES) {
      throw new IllegalArgumentException("attachment must be between 1 byte and 20 MiB");
    }
    OffsetDateTime now = OffsetDateTime.now(clock);
    String id = Ids.newId();
    String objectKey = tenantId + "/attachments/" + id + "/payload";
    UploadSession session =
        repository.insert(
            new UploadSession(
                id,
                tenantId,
                user.id(),
                ATTACHMENT_PURPOSE,
                objectKey,
                fileName,
                contentType,
                request.sizeBytes(),
                "prepared",
                now.plusMinutes(10),
                null,
                now,
                now));
    return new PreparedUpload(
        id,
        signer.presignedPut(objectKey, contentType, Duration.ofMinutes(5)),
        session.expiresAt());
  }

  public UploadSession consumeAttachment(String tenantId, String uploadId, UserPrincipal user) {
    requireAttachmentPermission(user);
    validateTenant(tenantId, user);
    if (uploadId == null || uploadId.isBlank()) {
      throw new IllegalArgumentException("uploadId is required");
    }
    return repository
        .consumePrepared(
            tenantId, uploadId.trim(), user.id(), ATTACHMENT_PURPOSE, OffsetDateTime.now(clock))
        .orElseThrow(() -> new ConflictException("upload session is invalid, expired or consumed"));
  }

  private static PrepareUpload validate(PrepareUpload request) {
    if (request == null) {
      throw new IllegalArgumentException("upload request is required");
    }
    String fileName = request.originalFileName() == null ? "" : request.originalFileName().trim();
    if (fileName.isEmpty()
        || fileName.length() > 255
        || fileName.contains("/")
        || fileName.contains("\\")
        || fileName.chars().anyMatch(Character::isISOControl)
        || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
      throw new IllegalArgumentException("only a safe .xlsx file name is supported");
    }
    String contentType = request.contentType() == null ? "" : request.contentType().trim();
    if (!XLSX.equalsIgnoreCase(contentType)
        && !"application/octet-stream".equalsIgnoreCase(contentType)) {
      throw new IllegalArgumentException("unsupported Excel content type");
    }
    if (request.sizeBytes() < 1 || request.sizeBytes() > MAX_EXCEL_BYTES) {
      throw new IllegalArgumentException("Excel file must be between 1 byte and 20 MiB");
    }
    return new PrepareUpload(fileName, contentType, request.sizeBytes());
  }

  private static void validateTenant(String tenantId, UserPrincipal user) {
    if (tenantId == null || !tenantId.matches("[A-Za-z0-9_-]{1,64}")) {
      throw new IllegalArgumentException("invalid tenantId");
    }
    if (!tenantId.equals(user.tenantId())) {
      throw new AccessDeniedException("tenant mismatch");
    }
  }

  private static void requirePermission(UserPrincipal user) {
    if (user == null || !user.hasPermission(PermissionCodes.WORK_RECORD_IMPORT)) {
      throw new AccessDeniedException("not allowed to import work records");
    }
  }

  private static void requireAttachmentPermission(UserPrincipal user) {
    if (user == null || !user.hasPermission(PermissionCodes.WORK_RECORD_ATTACHMENT)) {
      throw new AccessDeniedException("not allowed to upload attachments");
    }
  }

  private static String safeFileName(String value) {
    String fileName = value == null ? "" : value.trim();
    if (fileName.isEmpty()
        || fileName.length() > 255
        || fileName.contains("/")
        || fileName.contains("\\")
        || fileName.contains("..")
        || fileName.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("attachment file name is not safe");
    }
    return fileName;
  }

  public record PrepareUpload(String originalFileName, String contentType, long sizeBytes) {}

  public record PreparedUpload(String uploadId, String uploadUrl, OffsetDateTime expiresAt) {}
}
