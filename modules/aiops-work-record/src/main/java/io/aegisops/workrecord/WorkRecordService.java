package io.aegisops.workrecord;

import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import io.aegisops.common.api.PageResult;
import io.aegisops.common.json.JsonPayloads;
import io.aegisops.security.UserPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordService {
  private static final Set<String> STATUSES = Set.of("draft", "processing", "done", "archived");
  private static final String PERMISSION_READ_SELF = "work-record:read:self";
  private static final String PERMISSION_READ_ALL = "work-record:read:all";

  private final WorkRecordRepository repository;
  private final WorkRecordFieldRepository fieldRepository;
  private final AuditService audit;

  public WorkRecordService(
      WorkRecordRepository repository,
      WorkRecordFieldRepository fieldRepository,
      AuditService audit) {
    this.repository = repository;
    this.fieldRepository = fieldRepository;
    this.audit = audit;
  }

  /**
   * 列表查询遵循 read self/all 的权限边界：若当前用户不具备 {@code work-record:read:all}， 强制只返回以其 userId 作为 ownerId 或
   * creatorId 的记录。
   */
  public PageResult<WorkRecord> list(
      String tenantId, UserPrincipal user, String status, Integer page, Integer size) {
    if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
      throw new IllegalArgumentException("unsupported status: " + status);
    }
    int p = PageResult.normalizePage(page);
    int s = PageResult.normalizeSize(size);
    String effectiveStatus = blankToNull(status);
    if (canReadAll(user)) {
      long total = repository.count(tenantId, null, effectiveStatus);
      List<WorkRecord> items = repository.page(tenantId, null, effectiveStatus, p, s);
      return new PageResult<>(total, p, s, items);
    }
    String selfId = user == null ? null : user.id();
    long total = repository.countForUser(tenantId, selfId, effectiveStatus);
    List<WorkRecord> items = repository.pageForUser(tenantId, selfId, effectiveStatus, p, s);
    return new PageResult<>(total, p, s, items);
  }

  public WorkRecord get(String tenantId, String id, UserPrincipal user) {
    requireText(id, "id");
    WorkRecord record =
        repository
            .find(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("work record not found"));
    if (!canReadAll(user) && !isOwnerOrCreator(user, record)) {
      throw new SecurityException("not allowed to read this work record");
    }
    return record;
  }

  public WorkRecord create(String tenantId, CreateWorkRecordRequest request, String creatorId) {
    if (request == null) {
      throw new IllegalArgumentException("work record request is required");
    }
    requireText(request.templateId(), "templateId");
    requireText(request.title(), "title");
    if (request.status() != null
        && !request.status().isBlank()
        && !STATUSES.contains(request.status())) {
      throw new IllegalArgumentException("unsupported status: " + request.status());
    }
    String builtin = JsonPayloads.normalizeObject(request.builtinDataJson(), "builtinDataJson");
    String custom = JsonPayloads.normalizeObject(request.customDataJson(), "customDataJson");
    List<WorkRecordField> templateFields = fieldRepository.list(tenantId, request.templateId());
    if (templateFields.isEmpty()) {
      throw new IllegalArgumentException("template not found: " + request.templateId());
    }
    WorkRecordFieldValidator.validateAgainstTemplate(templateFields, custom);
    OffsetDateTime recordTime =
        request.recordTime() == null ? OffsetDateTime.now() : request.recordTime();
    CreateWorkRecordRequest normalized =
        new CreateWorkRecordRequest(
            request.templateId(),
            request.title(),
            request.status(),
            request.ownerId(),
            recordTime,
            builtin,
            custom);
    WorkRecord created = repository.create(tenantId, normalized, defaultActor(creatorId));
    audit.record(
        new AuditRecordCommand(
            tenantId,
            defaultActor(creatorId),
            "work_record.record.create",
            "wr_record",
            created.id(),
            auditDetail(
                "templateId",
                created.templateId(),
                "title",
                created.title(),
                "status",
                created.status())));
    return created;
  }

  public WorkRecord update(
      String tenantId, String id, UpdateWorkRecordRequest request, UserPrincipal actor) {
    requireText(id, "id");
    if (request == null) {
      throw new IllegalArgumentException("update request is required");
    }
    WorkRecord existing =
        repository
            .find(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("work record not found"));
    if (!canReadAll(actor) && !isOwnerOrCreator(actor, existing)) {
      throw new SecurityException("not allowed to update this work record");
    }
    if (request.status() != null
        && !request.status().isBlank()
        && !STATUSES.contains(request.status())) {
      throw new IllegalArgumentException("unsupported status: " + request.status());
    }
    String builtin =
        request.builtinDataJson() == null
            ? null
            : JsonPayloads.normalizeObject(request.builtinDataJson(), "builtinDataJson");
    String custom =
        request.customDataJson() == null
            ? null
            : JsonPayloads.normalizeObject(request.customDataJson(), "customDataJson");
    if (custom != null) {
      List<WorkRecordField> templateFields = fieldRepository.list(tenantId, existing.templateId());
      WorkRecordFieldValidator.validateAgainstTemplate(templateFields, custom);
    }
    UpdateWorkRecordRequest normalized =
        new UpdateWorkRecordRequest(
            request.title(),
            request.status(),
            request.ownerId(),
            request.recordTime(),
            builtin,
            custom);
    WorkRecord updated =
        repository
            .update(tenantId, id, normalized)
            .orElseThrow(() -> new IllegalArgumentException("work record not found"));
    audit.record(
        new AuditRecordCommand(
            tenantId,
            defaultActor(actor == null ? null : actor.id()),
            "work_record.record.update",
            "wr_record",
            updated.id(),
            auditDetail("title", updated.title(), "status", updated.status())));
    return updated;
  }

  public void delete(String tenantId, String id, UserPrincipal actor) {
    requireText(id, "id");
    WorkRecord existing =
        repository
            .find(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("work record not found"));
    if (!canReadAll(actor) && !isOwnerOrCreator(actor, existing)) {
      throw new SecurityException("not allowed to delete this work record");
    }
    repository.softDelete(tenantId, id);
    audit.record(
        new AuditRecordCommand(
            tenantId,
            defaultActor(actor == null ? null : actor.id()),
            "work_record.record.delete",
            "wr_record",
            id,
            auditDetail("title", existing.title())));
  }

  private boolean canReadAll(UserPrincipal user) {
    if (user == null) {
      return false;
    }
    return user.getAuthorities().stream()
        .anyMatch(a -> PERMISSION_READ_ALL.equals(a.getAuthority()));
  }

  private boolean isOwnerOrCreator(UserPrincipal user, WorkRecord record) {
    if (user == null) {
      return false;
    }
    String userId = user.id();
    return userId != null && (userId.equals(record.ownerId()) || userId.equals(record.creatorId()));
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private String defaultActor(String userId) {
    return userId == null || userId.isBlank() ? "system" : userId;
  }

  private String auditDetail(String... keyValues) {
    StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i + 1 < keyValues.length; i += 2) {
      if (i > 0) sb.append(',');
      sb.append('"').append(escape(keyValues[i])).append('"');
      sb.append(':');
      sb.append('"').append(escape(keyValues[i + 1] == null ? "" : keyValues[i + 1])).append('"');
    }
    sb.append('}');
    return sb.toString();
  }

  private String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
