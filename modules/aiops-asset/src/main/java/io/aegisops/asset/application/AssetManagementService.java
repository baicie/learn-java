package io.aegisops.asset.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.asset.api.dto.AssetResponse;
import io.aegisops.asset.api.dto.AssetUpsertCommand;
import io.aegisops.asset.api.dto.CreateAssetRelationRequest;
import io.aegisops.asset.api.dto.CreateAssetRequest;
import io.aegisops.asset.api.dto.UpdateAssetRequest;
import io.aegisops.asset.infrastructure.persistence.AssetRepository;
import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import io.aegisops.common.exception.ConflictException;
import io.aegisops.common.exception.ResourceNotFoundException;
import io.aegisops.common.id.Ids;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetManagementService {
  private final AssetApplicationService applicationService;
  private final AssetQueryService queryService;
  private final AssetRepository repository;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public AssetManagementService(
      AssetApplicationService applicationService,
      AssetQueryService queryService,
      AssetRepository repository,
      AuditService auditService,
      ObjectMapper objectMapper) {
    this.applicationService = applicationService;
    this.queryService = queryService;
    this.repository = repository;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public AssetResponse create(String tenantId, CreateAssetRequest request, String actorId) {
    String externalId = "manual_" + Ids.newId();
    var result =
        applicationService.upsert(
            new AssetUpsertCommand(
                tenantId,
                request.assetType(),
                request.name(),
                request.displayName(),
                request.description(),
                request.environment(),
                request.site(),
                request.ownerTeam(),
                request.criticality(),
                request.ip(),
                request.tags(),
                "manual",
                "manual",
                null,
                externalId,
                "manual",
                Map.of(),
                request.identities()));
    AssetResponse created = queryService.get(tenantId, result.assetId());
    audit(tenantId, actorId, "asset.create", created.id(), null, created, Map.of());
    return created;
  }

  @Transactional
  public AssetResponse update(
      String tenantId, String assetId, UpdateAssetRequest request, String actorId) {
    AssetResponse before = queryService.get(tenantId, assetId);
    boolean updated =
        repository.updateCanonical(tenantId, assetId, request, OffsetDateTime.now(ZoneOffset.UTC));
    if (!updated) {
      throw new ConflictException("资源已被其他操作更新，请刷新后重试");
    }
    AssetResponse after = queryService.get(tenantId, assetId);
    audit(tenantId, actorId, "asset.update", assetId, before, after, Map.of());
    return after;
  }

  @Transactional
  public void archive(String tenantId, String assetId, long version, String actorId) {
    AssetResponse before = queryService.get(tenantId, assetId);
    if (!repository.archive(tenantId, assetId, version, OffsetDateTime.now(ZoneOffset.UTC))) {
      throw new ConflictException("资源已被其他操作更新，请刷新后重试");
    }
    audit(tenantId, actorId, "asset.archive", assetId, before, null, Map.of());
  }

  @Transactional
  public String createRelation(
      String tenantId, String assetId, CreateAssetRelationRequest request, String actorId) {
    queryService.get(tenantId, assetId);
    queryService.get(tenantId, request.targetAssetId());
    if (assetId.equals(request.targetAssetId())) {
      throw new ConflictException("资源不能关联自身");
    }
    String relationId =
        repository.createRelation(tenantId, assetId, request, OffsetDateTime.now(ZoneOffset.UTC));
    audit(
        tenantId,
        actorId,
        "asset.relation.create",
        assetId,
        null,
        null,
        Map.of("relationId", relationId, "targetAssetId", request.targetAssetId()));
    return relationId;
  }

  @Transactional
  public void deleteRelation(String tenantId, String assetId, String relationId, String actorId) {
    queryService.get(tenantId, assetId);
    if (!repository.deleteRelation(tenantId, assetId, relationId)) {
      throw new ResourceNotFoundException("资源关系不存在: " + relationId);
    }
    audit(
        tenantId,
        actorId,
        "asset.relation.delete",
        assetId,
        null,
        null,
        Map.of("relationId", relationId));
  }

  private void audit(
      String tenantId,
      String actorId,
      String action,
      String assetId,
      Object before,
      Object after,
      Object detail) {
    auditService.record(
        new AuditRecordCommand(
            tenantId, actorId, action, "asset", assetId, json(before), json(after), json(detail)));
  }

  private String json(Object value) {
    try {
      return value == null ? "{}" : objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("资源审计快照无法序列化", exception);
    }
  }
}
