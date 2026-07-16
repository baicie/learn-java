package io.aegisops.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.asset.api.dto.AssetImportPreviewResponse;
import io.aegisops.asset.domain.rule.AssetCsvRowValidator;
import io.aegisops.asset.domain.rule.AssetIdentityNormalizer;
import io.aegisops.asset.infrastructure.adapter.AssetCsvParser;
import io.aegisops.asset.infrastructure.adapter.AssetCsvRow;
import io.aegisops.asset.infrastructure.persistence.AssetImportPreviewDraft;
import io.aegisops.asset.infrastructure.persistence.AssetImportRepository;
import io.aegisops.asset.infrastructure.persistence.AssetRepository;
import io.aegisops.audit.AuditService;
import io.aegisops.common.exception.ConflictException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetImportServiceTest {
  private AssetCsvParser parser;
  private AssetImportRepository importRepository;
  private AssetRepository assetRepository;
  private AssetApplicationService assetService;
  private AssetImportService service;

  @BeforeEach
  void setUp() {
    parser = mock(AssetCsvParser.class);
    importRepository = mock(AssetImportRepository.class);
    assetRepository = mock(AssetRepository.class);
    assetService = mock(AssetApplicationService.class);
    service =
        new AssetImportService(
            new AssetImportPreviewer(
                parser, new AssetCsvRowValidator(), new AssetIdentityNormalizer(), assetRepository),
            importRepository,
            assetService,
            mock(AuditService.class),
            new ObjectMapper());
  }

  @Test
  void previewPersistsRowsButMustNotWriteCanonicalAssets() {
    byte[] content = "csv-content".getBytes(StandardCharsets.UTF_8);
    when(importRepository.findByChecksum(eq("tenant-1"), eq("sheet-a"), any()))
        .thenReturn(Optional.empty());
    when(parser.parse(content)).thenReturn(List.of(validRow()));
    when(assetRepository.findAssetIdsByStrongIdentities(eq("tenant-1"), any()))
        .thenReturn(Set.of());
    when(assetRepository.findAssetIdBySourceLink("tenant-1", "csv", "sheet-a", "host-1"))
        .thenReturn(Optional.empty());
    when(importRepository.createPreview(any(AssetImportPreviewDraft.class)))
        .thenReturn(job("previewed", 0));

    var result = service.preview("tenant-1", "sheet-a", "assets.csv", content, "user-1");

    assertThat(result.status()).isEqualTo("previewed");
    verifyNoInteractions(assetService);
  }

  @Test
  void sameChecksumReturnsExistingJobWithoutParsingAgain() {
    byte[] content = "same-content".getBytes(StandardCharsets.UTF_8);
    when(importRepository.findByChecksum(eq("tenant-1"), eq("sheet-a"), any()))
        .thenReturn(Optional.of(job("previewed", 0)));

    var result = service.preview("tenant-1", "sheet-a", "assets.csv", content, "user-1");

    assertThat(result.jobId()).isEqualTo("job-1");
    verifyNoInteractions(parser, assetService);
    verify(importRepository, never()).createPreview(any());
  }

  @Test
  void confirmRejectsUnresolvedStrongIdentityConflicts() {
    when(importRepository.get("tenant-1", "job-1")).thenReturn(Optional.of(job("previewed", 1)));

    assertThatThrownBy(() -> service.confirm("tenant-1", "job-1", "user-1"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("冲突行");

    verifyNoInteractions(assetService);
  }

  private AssetCsvRow validRow() {
    return new AssetCsvRow(
        2,
        "host-1",
        "host",
        "db-1",
        "数据库",
        "production",
        "shanghai",
        "payment",
        "tier-1",
        "10.0.0.8",
        "machine-1",
        "",
        "",
        Map.of("role", "db"));
  }

  private AssetImportPreviewResponse job(String status, int conflicts) {
    return new AssetImportPreviewResponse(
        "job-1",
        "assets.csv",
        "sheet-a",
        status,
        1,
        conflicts == 0 ? 1 : 0,
        0,
        conflicts,
        0,
        0,
        OffsetDateTime.parse("2026-07-16T10:00:00Z"));
  }
}
