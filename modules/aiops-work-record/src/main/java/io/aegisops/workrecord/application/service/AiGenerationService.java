package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aegisops.common.id.Ids;
import io.aegisops.common.outbox.OutboxMessage;
import io.aegisops.common.outbox.OutboxWriter;
import io.aegisops.security.PermissionCodes;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.AiGenerationRepository;
import io.aegisops.workrecord.domain.model.AiGeneration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiGenerationService {
  private final AiGenerationRepository generations;
  private final AiInputBuilder inputs;
  private final WorkRecordQueryService records;
  private final OutboxWriter outbox;
  private final ObjectMapper objectMapper;
  private final WorkRecordAuditService audit;

  public AiGenerationService(
      AiGenerationRepository generations,
      AiInputBuilder inputs,
      WorkRecordQueryService records,
      OutboxWriter outbox,
      ObjectMapper objectMapper,
      WorkRecordAuditService audit) {
    this.generations = generations;
    this.inputs = inputs;
    this.records = records;
    this.outbox = outbox;
    this.objectMapper = objectMapper;
    this.audit = audit;
  }

  @Transactional
  public AiGeneration requestRecordSummary(
      String tenantId, String recordId, UserPrincipal principal) {
    requireGenerate(tenantId, principal);
    String traceId = Ids.newId();
    return createOrReuse(
        new GenerationRequest(
            tenantId,
            "record_summary",
            "record",
            recordId,
            null,
            null,
            inputs.recordSummary(tenantId, recordId, principal, traceId),
            principal.id()));
  }

  @Transactional
  public AiGeneration requestMonthlyReport(
      String tenantId, LocalDate month, UserPrincipal principal) {
    requireGenerate(tenantId, principal);
    requireTenantWideRead(principal);
    if (month == null) {
      throw new IllegalArgumentException("month is required");
    }
    LocalDate start = month.withDayOfMonth(1);
    return createOrReuse(
        new GenerationRequest(
            tenantId,
            "monthly_report",
            "tenant_month",
            start.toString().substring(0, 7),
            start,
            start.plusMonths(1).minusDays(1),
            inputs.monthlyReport(tenantId, start, principal, Ids.newId()),
            principal.id()));
  }

  public List<AiGeneration> list(
      String tenantId, String resourceType, String resourceId, UserPrincipal principal) {
    requireGenerate(tenantId, principal);
    if ("record".equals(resourceType)) {
      records.get(tenantId, resourceId, principal);
    } else if ("tenant_month".equals(resourceType)) {
      requireTenantWideRead(principal);
    } else {
      throw new IllegalArgumentException("unsupported AI generation resource type");
    }
    return generations.listByResource(tenantId, resourceType, resourceId);
  }

  @Transactional
  public AiGeneration review(
      String tenantId, String id, boolean accepted, UserPrincipal principal) {
    if (principal == null
        || !tenantId.equals(principal.tenantId())
        || !principal.hasPermission(PermissionCodes.WORK_RECORD_AI_REVIEW)) {
      throw new AccessDeniedException("not allowed to review AI result");
    }
    if (!generations.review(tenantId, id, accepted ? "accepted" : "rejected", principal.id())) {
      throw new IllegalStateException("AI result cannot be reviewed");
    }
    AiGeneration reviewed = generations.find(tenantId, id).orElseThrow();
    audit(reviewed, WorkRecordAuditActions.AI_GENERATION_REVIEWED, principal.id());
    return reviewed;
  }

  private AiGeneration createOrReuse(GenerationRequest request) {
    String inputJson = write(request.input());
    String hash = sha256(write(hashInput(request.input())));
    var reusable =
        generations.findReusable(
            request.tenantId(), request.type(), request.resourceType(), request.resourceId(), hash);
    if (reusable.isPresent()) {
      AiGeneration reused = reusable.get();
      audit(reused, WorkRecordAuditActions.AI_GENERATION_REUSED, request.requestedBy());
      return reused;
    }
    String id = Ids.newId();
    AiGeneration created =
        generations.create(
            new AiGenerationRepository.CreateGeneration(
                id,
                request.tenantId(),
                request.type(),
                request.resourceType(),
                request.resourceId(),
                request.periodStart(),
                request.periodEnd(),
                request.type().equals("record_summary")
                    ? "work-record-summary-v1"
                    : "work-record-monthly-v1",
                hash,
                inputJson,
                request.requestedBy()));
    outbox.enqueue(
        new OutboxMessage(
            request.tenantId(),
            "worker",
            "work-record-ai-generate",
            Map.of(
                "tenantId",
                request.tenantId(),
                "generationId",
                id,
                "requestedBy",
                request.requestedBy()),
            "ai-generation:" + id,
            5,
            OffsetDateTime.now()));
    audit(created, WorkRecordAuditActions.AI_GENERATION_REQUESTED, request.requestedBy());
    return created;
  }

  private void audit(AiGeneration generation, String action, String actorId) {
    audit.record(
        generation.tenantId(),
        null,
        null,
        "work_record_ai_generation",
        generation.id(),
        action,
        actorId,
        write(Map.of("status", generation.status())));
  }

  private Object hashInput(Object input) {
    var node = objectMapper.valueToTree(input);
    if (node instanceof ObjectNode object) {
      object.remove(List.of("traceId", "actorId"));
    }
    return node;
  }

  private String write(Object input) {
    try {
      return objectMapper.writeValueAsString(input);
    } catch (Exception ex) {
      throw new IllegalStateException("failed to serialize AI input", ex);
    }
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  private static void requireGenerate(String tenantId, UserPrincipal principal) {
    if (principal == null
        || !tenantId.equals(principal.tenantId())
        || !principal.hasPermission(PermissionCodes.WORK_RECORD_AI_GENERATE)) {
      throw new AccessDeniedException("not allowed to generate AI work-record content");
    }
  }

  private static void requireTenantWideRead(UserPrincipal principal) {
    if (!principal.hasPermission(PermissionCodes.WORK_RECORD_READ_ALL)) {
      throw new AccessDeniedException("tenant-wide AI generation requires read-all permission");
    }
  }

  private record GenerationRequest(
      String tenantId,
      String type,
      String resourceType,
      String resourceId,
      LocalDate periodStart,
      LocalDate periodEnd,
      Object input,
      String requestedBy) {}
}
