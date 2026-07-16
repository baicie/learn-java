package io.aegisops.asset.infrastructure.persistence;

import static io.aegisops.persistence.jooq.public_.tables.AssetImportJob.ASSET_IMPORT_JOB;
import static io.aegisops.persistence.jooq.public_.tables.AssetImportRow.ASSET_IMPORT_ROW;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.asset.api.dto.AssetImportPreviewResponse;
import io.aegisops.asset.api.dto.AssetImportRowPageResponse;
import io.aegisops.asset.api.dto.AssetImportRowResponse;
import io.aegisops.common.id.Ids;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

@Repository
public class AssetImportRepository {
  private final DSLContext dsl;
  private final ObjectMapper objectMapper;

  public AssetImportRepository(DSLContext dsl, ObjectMapper objectMapper) {
    this.dsl = dsl;
    this.objectMapper = objectMapper;
  }

  public Optional<AssetImportPreviewResponse> findByChecksum(
      String tenantId, String sourceInstanceId, String checksum) {
    return dsl.selectFrom(ASSET_IMPORT_JOB)
        .where(ASSET_IMPORT_JOB.TENANT_ID.eq(tenantId))
        .and(ASSET_IMPORT_JOB.SOURCE_INSTANCE_ID.eq(sourceInstanceId))
        .and(ASSET_IMPORT_JOB.CONTENT_SHA256.eq(checksum))
        .fetchOptional(this::job);
  }

  public AssetImportPreviewResponse createPreview(AssetImportPreviewDraft draft) {
    String tenantId = draft.tenantId();
    List<AssetImportRowDraft> rows = draft.rows();
    OffsetDateTime now = draft.createdAt();
    String jobId = "aimp_" + Ids.newId();
    int valid = (int) rows.stream().filter(row -> row.validationStatus().equals("valid")).count();
    int invalid =
        (int) rows.stream().filter(row -> row.validationStatus().equals("invalid")).count();
    int conflicts =
        (int) rows.stream().filter(row -> row.validationStatus().equals("conflict")).count();
    dsl.insertInto(ASSET_IMPORT_JOB)
        .set(ASSET_IMPORT_JOB.ID, jobId)
        .set(ASSET_IMPORT_JOB.TENANT_ID, tenantId)
        .set(ASSET_IMPORT_JOB.SOURCE_INSTANCE_ID, draft.sourceInstanceId())
        .set(ASSET_IMPORT_JOB.FILE_NAME, draft.fileName())
        .set(ASSET_IMPORT_JOB.CONTENT_SHA256, draft.checksum())
        .set(ASSET_IMPORT_JOB.STATUS, "previewed")
        .set(ASSET_IMPORT_JOB.TOTAL_ROWS, rows.size())
        .set(ASSET_IMPORT_JOB.VALID_ROWS, valid)
        .set(ASSET_IMPORT_JOB.INVALID_ROWS, invalid)
        .set(ASSET_IMPORT_JOB.CONFLICT_ROWS, conflicts)
        .set(ASSET_IMPORT_JOB.CREATED_BY, draft.actorId())
        .set(ASSET_IMPORT_JOB.CREATED_AT, now)
        .set(ASSET_IMPORT_JOB.UPDATED_AT, now)
        .execute();
    for (AssetImportRowDraft row : rows) {
      dsl.insertInto(ASSET_IMPORT_ROW)
          .set(ASSET_IMPORT_ROW.ID, "airow_" + Ids.newId())
          .set(ASSET_IMPORT_ROW.TENANT_ID, tenantId)
          .set(ASSET_IMPORT_ROW.JOB_ID, jobId)
          .set(ASSET_IMPORT_ROW.ROW_NUMBER, row.rowNumber())
          .set(ASSET_IMPORT_ROW.EXTERNAL_ID, blankToNull(row.externalId()))
          .set(ASSET_IMPORT_ROW.NORMALIZED_PAYLOAD, json(row.payload()))
          .set(ASSET_IMPORT_ROW.VALIDATION_STATUS, row.validationStatus())
          .set(ASSET_IMPORT_ROW.RESOLUTION_ACTION, row.resolutionAction())
          .set(ASSET_IMPORT_ROW.ERROR_CODES, json(row.errorCodes()))
          .set(ASSET_IMPORT_ROW.CREATED_AT, now)
          .execute();
    }
    return get(tenantId, jobId).orElseThrow();
  }

  public Optional<AssetImportPreviewResponse> get(String tenantId, String jobId) {
    return dsl.selectFrom(ASSET_IMPORT_JOB)
        .where(ASSET_IMPORT_JOB.TENANT_ID.eq(tenantId))
        .and(ASSET_IMPORT_JOB.ID.eq(jobId))
        .fetchOptional(this::job);
  }

  public AssetImportRowPageResponse rows(
      String tenantId, String jobId, int page, int pageSize, String status) {
    var condition = ASSET_IMPORT_ROW.TENANT_ID.eq(tenantId).and(ASSET_IMPORT_ROW.JOB_ID.eq(jobId));
    if (status != null && !status.isBlank()) {
      condition = condition.and(ASSET_IMPORT_ROW.VALIDATION_STATUS.eq(status));
    }
    long total = dsl.fetchCount(dsl.selectFrom(ASSET_IMPORT_ROW).where(condition));
    List<AssetImportRowResponse> items =
        dsl.selectFrom(ASSET_IMPORT_ROW)
            .where(condition)
            .orderBy(ASSET_IMPORT_ROW.ROW_NUMBER.asc())
            .offset((page - 1) * pageSize)
            .limit(pageSize)
            .fetch(this::row);
    return new AssetImportRowPageResponse(total, page, pageSize, items);
  }

  public List<AssetImportRowResponse> validRows(String tenantId, String jobId) {
    return dsl.selectFrom(ASSET_IMPORT_ROW)
        .where(ASSET_IMPORT_ROW.TENANT_ID.eq(tenantId))
        .and(ASSET_IMPORT_ROW.JOB_ID.eq(jobId))
        .and(ASSET_IMPORT_ROW.VALIDATION_STATUS.eq("valid"))
        .orderBy(ASSET_IMPORT_ROW.ROW_NUMBER.asc())
        .fetch(this::row);
  }

  public boolean start(String tenantId, String jobId, String actorId, OffsetDateTime now) {
    return dsl.update(ASSET_IMPORT_JOB)
            .set(ASSET_IMPORT_JOB.STATUS, "running")
            .set(ASSET_IMPORT_JOB.CONFIRMED_BY, actorId)
            .set(ASSET_IMPORT_JOB.CONFIRMED_AT, now)
            .set(ASSET_IMPORT_JOB.UPDATED_AT, now)
            .where(ASSET_IMPORT_JOB.TENANT_ID.eq(tenantId))
            .and(ASSET_IMPORT_JOB.ID.eq(jobId))
            .and(ASSET_IMPORT_JOB.STATUS.eq("previewed"))
            .execute()
        == 1;
  }

  public void resolveRow(
      String tenantId, String jobId, int rowNumber, String assetId, String action) {
    dsl.update(ASSET_IMPORT_ROW)
        .set(ASSET_IMPORT_ROW.RESOLVED_ASSET_ID, assetId)
        .set(ASSET_IMPORT_ROW.RESOLUTION_ACTION, action)
        .where(ASSET_IMPORT_ROW.TENANT_ID.eq(tenantId))
        .and(ASSET_IMPORT_ROW.JOB_ID.eq(jobId))
        .and(ASSET_IMPORT_ROW.ROW_NUMBER.eq(rowNumber))
        .execute();
  }

  public void finish(String tenantId, String jobId, int created, int updated, OffsetDateTime now) {
    dsl.update(ASSET_IMPORT_JOB)
        .set(ASSET_IMPORT_JOB.STATUS, "success")
        .set(ASSET_IMPORT_JOB.CREATED_ROWS, created)
        .set(ASSET_IMPORT_JOB.UPDATED_ROWS, updated)
        .set(ASSET_IMPORT_JOB.UPDATED_AT, now)
        .where(ASSET_IMPORT_JOB.TENANT_ID.eq(tenantId))
        .and(ASSET_IMPORT_JOB.ID.eq(jobId))
        .execute();
  }

  public boolean cancel(String tenantId, String jobId, OffsetDateTime now) {
    return dsl.update(ASSET_IMPORT_JOB)
            .set(ASSET_IMPORT_JOB.STATUS, "cancelled")
            .set(ASSET_IMPORT_JOB.UPDATED_AT, now)
            .where(ASSET_IMPORT_JOB.TENANT_ID.eq(tenantId))
            .and(ASSET_IMPORT_JOB.ID.eq(jobId))
            .and(ASSET_IMPORT_JOB.STATUS.eq("previewed"))
            .execute()
        == 1;
  }

  private AssetImportPreviewResponse job(
      io.aegisops.persistence.jooq.public_.tables.records.AssetImportJobRecord row) {
    return new AssetImportPreviewResponse(
        row.getId(),
        row.getFileName(),
        row.getSourceInstanceId(),
        row.getStatus(),
        row.getTotalRows(),
        row.getValidRows(),
        row.getInvalidRows(),
        row.getConflictRows(),
        row.getCreatedRows(),
        row.getUpdatedRows(),
        row.getCreatedAt());
  }

  private AssetImportRowResponse row(
      io.aegisops.persistence.jooq.public_.tables.records.AssetImportRowRecord row) {
    return new AssetImportRowResponse(
        row.getRowNumber(),
        row.getExternalId(),
        row.getValidationStatus(),
        row.getResolutionAction(),
        row.getResolvedAssetId(),
        object(row.getNormalizedPayload()),
        strings(row.getErrorCodes()));
  }

  private JSONB json(Object value) {
    try {
      return JSONB.valueOf(objectMapper.writeValueAsString(value));
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("import data is not serializable", exception);
    }
  }

  private Map<String, Object> object(JSONB value) {
    try {
      return objectMapper.readValue(value.data(), new TypeReference<>() {});
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("import payload is invalid", exception);
    }
  }

  private List<String> strings(JSONB value) {
    try {
      return objectMapper.readValue(value.data(), new TypeReference<>() {});
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("import errors are invalid", exception);
    }
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
