package io.aegisops.asset.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.asset.api.dto.AssetImportPreviewResponse;
import io.aegisops.asset.api.dto.AssetImportRowPageResponse;
import io.aegisops.asset.api.dto.AssetImportRowResponse;
import io.aegisops.asset.api.dto.AssetUpsertCommand;
import io.aegisops.asset.domain.model.AssetIdentityInput;
import io.aegisops.asset.domain.model.NormalizedAssetIdentity.Strength;
import io.aegisops.asset.domain.rule.AssetCsvRowValidator;
import io.aegisops.asset.domain.rule.AssetIdentityNormalizer;
import io.aegisops.asset.infrastructure.adapter.AssetCsvParser;
import io.aegisops.asset.infrastructure.adapter.AssetCsvRow;
import io.aegisops.asset.infrastructure.persistence.AssetImportRepository;
import io.aegisops.asset.infrastructure.persistence.AssetImportRowDraft;
import io.aegisops.asset.infrastructure.persistence.AssetRepository;
import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import io.aegisops.common.exception.ConflictException;
import io.aegisops.common.exception.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetImportService {
  private final AssetCsvParser parser;
  private final AssetCsvRowValidator validator;
  private final AssetIdentityNormalizer identityNormalizer;
  private final AssetImportRepository importRepository;
  private final AssetRepository assetRepository;
  private final AssetApplicationService assetService;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public AssetImportService(
      AssetCsvParser parser,
      AssetCsvRowValidator validator,
      AssetIdentityNormalizer identityNormalizer,
      AssetImportRepository importRepository,
      AssetRepository assetRepository,
      AssetApplicationService assetService,
      AuditService auditService,
      ObjectMapper objectMapper) {
    this.parser = parser;
    this.validator = validator;
    this.identityNormalizer = identityNormalizer;
    this.importRepository = importRepository;
    this.assetRepository = assetRepository;
    this.assetService = assetService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public AssetImportPreviewResponse preview(
      String tenantId,
      String sourceInstanceId,
      String fileName,
      byte[] content,
      String actorId) {
    requireSourceInstance(sourceInstanceId);
    String checksum = sha256(content);
    var existing = importRepository.findByChecksum(tenantId, sourceInstanceId, checksum);
    if (existing.isPresent()) {
      return existing.orElseThrow();
    }

    List<AssetCsvRow> rows = parser.parse(content);
    Map<String, Integer> externalIds = new LinkedHashMap<>();
    for (AssetCsvRow row : rows) {
      if (!row.externalId().isBlank()) {
        externalIds.merge(row.externalId(), 1, Integer::sum);
      }
    }
    List<AssetImportRowDraft> drafts =
        rows.stream()
            .map(row -> previewRow(tenantId, sourceInstanceId, row, externalIds))
            .toList();
    return importRepository.createPreview(
        tenantId,
        sourceInstanceId,
        safeFileName(fileName),
        checksum,
        actorId,
        drafts,
        OffsetDateTime.now(ZoneOffset.UTC));
  }

  public AssetImportPreviewResponse get(String tenantId, String jobId) {
    return importRepository
        .get(tenantId, jobId)
        .orElseThrow(() -> new ResourceNotFoundException("导入任务不存在: " + jobId));
  }

  public AssetImportRowPageResponse rows(
      String tenantId, String jobId, int page, int pageSize, String status) {
    get(tenantId, jobId);
    return importRepository.rows(tenantId, jobId, page, Math.min(pageSize, 100), status);
  }

  @Transactional
  public AssetImportPreviewResponse confirm(String tenantId, String jobId, String actorId) {
    AssetImportPreviewResponse job = get(tenantId, jobId);
    if (job.status().equals("success")) {
      return job;
    }
    if (job.conflictRows() > 0) {
      throw new ConflictException("导入任务存在冲突行，请处理为 create/link/skip 后再确认");
    }
    if (!importRepository.start(
        tenantId, jobId, actorId, OffsetDateTime.now(ZoneOffset.UTC))) {
      throw new ConflictException("导入任务当前状态不可确认: " + job.status());
    }

    int created = 0;
    int updated = 0;
    for (AssetImportRowResponse row : importRepository.validRows(tenantId, jobId)) {
      AssetUpsertCommand command = command(tenantId, job.sourceInstanceId(), row.payload());
      var result = assetService.upsert(command);
      importRepository.resolveRow(
          tenantId, jobId, row.rowNumber(), result.assetId(), result.action());
      if (result.action().equals("created")) {
        created++;
      } else {
        updated++;
      }
    }
    importRepository.finish(
        tenantId, jobId, created, updated, OffsetDateTime.now(ZoneOffset.UTC));
    audit(tenantId, actorId, "asset.import.confirm", jobId, Map.of("checksumJob", jobId));
    return get(tenantId, jobId);
  }

  @Transactional
  public void cancel(String tenantId, String jobId, String actorId) {
    get(tenantId, jobId);
    if (!importRepository.cancel(tenantId, jobId, OffsetDateTime.now(ZoneOffset.UTC))) {
      throw new ConflictException("只有待确认的导入任务可以取消");
    }
    audit(tenantId, actorId, "asset.import.cancel", jobId, Map.of());
  }

  public byte[] template() {
    return (String.join(",", AssetCsvParser.TEMPLATE_HEADERS)
            + "\n"
            + "host-001,host,db-prod-01,生产数据库一号,production,shanghai-idc,payment,tier-1,10.0.0.8,machine-001,,,\"role=mysql;engine=postgresql\"\n")
        .getBytes(StandardCharsets.UTF_8);
  }

  private AssetImportRowDraft previewRow(
      String tenantId,
      String sourceInstanceId,
      AssetCsvRow row,
      Map<String, Integer> externalIds) {
    List<String> errors = new ArrayList<>(validator.validate(row));
    if (externalIds.getOrDefault(row.externalId(), 0) > 1) {
      errors.add("EXTERNAL_ID_DUPLICATE");
    }
    String status = errors.isEmpty() ? "valid" : "invalid";
    String action = null;
    if (errors.isEmpty()) {
      var identities =
          identityNormalizer.normalize(identityInputs(row), blankToNull(row.ip()));
      Set<String> strongMatches =
          assetRepository.findAssetIdsByStrongIdentities(
              tenantId,
              identities.stream().filter(identity -> identity.strength() == Strength.STRONG).toList());
      var sourceMatch =
          assetRepository.findAssetIdBySourceLink(
              tenantId, "csv", sourceInstanceId, row.externalId());
      if (strongMatches.size() > 1
          || (sourceMatch.isPresent()
              && !strongMatches.isEmpty()
              && !strongMatches.contains(sourceMatch.orElseThrow()))) {
        status = "conflict";
        errors.add("STRONG_IDENTITY_CONFLICT");
      } else if (sourceMatch.isPresent()) {
        action = "update";
      } else if (!strongMatches.isEmpty()) {
        action = "link";
      } else {
        action = "create";
      }
    }
    return new AssetImportRowDraft(
        row.rowNumber(), row.externalId(), payload(row), status, action, List.copyOf(errors));
  }

  private AssetUpsertCommand command(
      String tenantId, String sourceInstanceId, Map<String, Object> payload) {
    return new AssetUpsertCommand(
        tenantId,
        string(payload, "assetType"),
        string(payload, "name"),
        string(payload, "displayName"),
        null,
        string(payload, "environment"),
        string(payload, "site"),
        string(payload, "ownerTeam"),
        string(payload, "criticality"),
        string(payload, "ip"),
        map(payload.get("tags")),
        "csv",
        sourceInstanceId,
        null,
        string(payload, "externalId"),
        "csv",
        payload,
        identityInputs(payload));
  }

  private Map<String, Object> payload(AssetCsvRow row) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("externalId", row.externalId());
    payload.put("assetType", row.assetType().toLowerCase(Locale.ROOT));
    payload.put("name", row.name());
    payload.put("displayName", row.displayName());
    payload.put("environment", row.environment());
    payload.put("site", row.site());
    payload.put("ownerTeam", row.ownerTeam());
    payload.put("criticality", row.criticality());
    payload.put("ip", row.ip());
    payload.put("machineId", row.machineId());
    payload.put("cloudInstanceId", row.cloudInstanceId());
    payload.put("k8sUid", row.k8sUid());
    payload.put("tags", row.tags());
    return Map.copyOf(payload);
  }

  private List<AssetIdentityInput> identityInputs(AssetCsvRow row) {
    List<AssetIdentityInput> result = new ArrayList<>();
    addIdentity(result, "machine_id", row.machineId());
    addIdentity(result, "cloud_instance_id", row.cloudInstanceId());
    addIdentity(result, "k8s_uid", row.k8sUid());
    return List.copyOf(result);
  }

  private List<AssetIdentityInput> identityInputs(Map<String, Object> payload) {
    List<AssetIdentityInput> result = new ArrayList<>();
    addIdentity(result, "machine_id", string(payload, "machineId"));
    addIdentity(result, "cloud_instance_id", string(payload, "cloudInstanceId"));
    addIdentity(result, "k8s_uid", string(payload, "k8sUid"));
    return List.copyOf(result);
  }

  private void addIdentity(List<AssetIdentityInput> identities, String type, String value) {
    if (value != null && !value.isBlank()) {
      identities.add(new AssetIdentityInput(type, "global", value, true));
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
  }

  private String string(Map<String, Object> payload, String key) {
    Object value = payload.get(key);
    return value == null ? null : value.toString();
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  private String safeFileName(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return "assets.csv";
    }
    String normalized = fileName.replace('\\', '/');
    String result = normalized.substring(normalized.lastIndexOf('/') + 1);
    return result.length() > 256 ? result.substring(result.length() - 256) : result;
  }

  private void requireSourceInstance(String value) {
    if (value == null || value.isBlank() || value.length() > 128) {
      throw new IllegalArgumentException("sourceInstanceId 必填且不能超过 128 字符");
    }
  }

  private void audit(
      String tenantId, String actorId, String action, String jobId, Object detail) {
    try {
      auditService.record(
          new AuditRecordCommand(
              tenantId,
              actorId,
              action,
              "asset_import_job",
              jobId,
              objectMapper.writeValueAsString(detail)));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("导入审计信息无法序列化", exception);
    }
  }
}
