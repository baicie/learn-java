package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.ObjectStorageUrlSigner;
import io.aegisops.workrecord.application.port.UploadSessionRepository;
import io.aegisops.workrecord.domain.model.UploadSession;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class UploadSessionServiceTest {
  private final UploadSessionRepository repository = mock(UploadSessionRepository.class);
  private final ObjectStorageUrlSigner signer = mock(ObjectStorageUrlSigner.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-14T10:00:00Z"), ZoneOffset.UTC);
  private final UploadSessionService service = new UploadSessionService(repository, signer, clock);

  @Test
  void preparesTenantAndUserBoundDirectUpload() {
    when(signer.presignedPut(any(), any(), any())).thenReturn("https://minio/upload");
    when(repository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var result =
        service.prepareExcelImport(
            "tenant-1",
            principal("user-1", Set.of("work-record:import")),
            new UploadSessionService.PrepareUpload("records.xlsx", XLSX, 1024));

    assertThat(result.uploadUrl()).isEqualTo("https://minio/upload");
    assertThat(result.expiresAt())
        .isEqualTo(Instant.parse("2026-07-14T10:10:00Z").atOffset(ZoneOffset.UTC));
    verify(repository).insert(any(UploadSession.class));
  }

  @Test
  void rejectsUnauthorizedOrInvalidFilesBeforeSigning() {
    assertThatThrownBy(
            () ->
                service.prepareExcelImport(
                    "tenant-1",
                    principal("user-1", Set.of()),
                    new UploadSessionService.PrepareUpload("records.xlsx", XLSX, 1024)))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () ->
                service.prepareExcelImport(
                    "tenant-1",
                    principal("user-1", Set.of("work-record:import")),
                    new UploadSessionService.PrepareUpload("../records.csv", "text/csv", 1024)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void consumeIsAtomicAndBoundToTenantRequesterAndPurpose() {
    UploadSession session = session();
    when(repository.consumePrepared(
            "tenant-1",
            "upload-1",
            "user-1",
            "excel_import",
            clock.instant().atOffset(ZoneOffset.UTC)))
        .thenReturn(Optional.of(session));

    assertThat(
            service.consumeExcelImport(
                "tenant-1", "upload-1", principal("user-1", Set.of("work-record:import"))))
        .isEqualTo(session);
  }

  private static UserPrincipal principal(String userId, Set<String> permissions) {
    return new UserPrincipal(
        new UserPrincipal.Identity(userId, "tenant-1", "alice", "Alice"),
        Set.of(),
        permissions,
        Map.of());
  }

  private static UploadSession session() {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-14T10:00Z");
    return new UploadSession(
        "upload-1",
        "tenant-1",
        "user-1",
        "excel_import",
        "tenant-1/imports/upload-1/source.xlsx",
        "records.xlsx",
        XLSX,
        1024,
        "consumed",
        now.plusMinutes(10),
        now,
        now,
        now);
  }

  private static final String XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
}
