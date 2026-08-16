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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
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
  private final OutboxWriter outbox;
  private final ObjectMapper objectMapper;
  private final WorkRecordAuditService audit;
  private final AiGenerationAccessGuard guard;

  public AiGenerationService(
      AiGenerationRepository generations,
      AiInputBuilder inputs,
      AiGenerationAccessGuard guard,
      OutboxWriter outbox,
      ObjectMapper objectMapper,
      WorkRecordAuditService audit) {
    this.generations = generations;
    this.inputs = inputs;
    this.outbox = outbox;
    this.objectMapper = objectMapper;
    this.audit = audit;
    this.guard = guard;
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
    guard.requireTenantWideRead(principal);
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

  @Transactional
  public AiGeneration requestWeeklyReport(
      String tenantId, LocalDate week, UserPrincipal principal) {
    requireGenerate(tenantId, principal);
    guard.requireTenantWideRead(principal);
    if (week == null) {
      throw new IllegalArgumentException("week is required");
    }
    LocalDate start = week.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    return createOrReuse(
        new GenerationRequest(
            tenantId,
            "weekly_report",
            "tenant_week",
            start.toString(),
            start,
            start.plusDays(6),
            inputs.weeklyReport(tenantId, start, principal, Ids.newId()),
            principal.id()));
  }

  public List<AiGeneration> list(
      String tenantId, String resourceType, String resourceId, UserPrincipal principal) {
    if ("record".equals(resourceType)) {
      requireGenerate(tenantId, principal);
      guard.visibleRecord(tenantId, resourceId, principal);
    } else if ("tenant_week".equals(resourceType) || "tenant_month".equals(resourceType)) {
      requireGenerateOrReview(tenantId, principal);
      guard.requireTenantWideRead(principal);
    } else {
      throw new IllegalArgumentException("unsupported AI generation resource type");
    }
    List<AiGeneration> result = generations.listByResource(tenantId, resourceType, resourceId);
    result.forEach(generation -> guard.requireInputReadable(generation, principal));
    return result;
  }

  @Transactional
  public AiGeneration review(
      String tenantId, String id, boolean accepted, UserPrincipal principal) {
    if (principal == null
        || !tenantId.equals(principal.tenantId())
        || !principal.hasPermission(PermissionCodes.WORK_RECORD_AI_REVIEW)) {
      throw new AccessDeniedException("not allowed to review AI result");
    }
    AiGeneration generation = generations.find(tenantId, id).orElseThrow();
    guard.requireResourceRead(generation, principal);
    guard.requireInputReadable(generation, principal);
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
                promptVersion(request.type()),
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

  private static String promptVersion(String type) {
    return switch (type) {
      case "record_summary" -> "work-record-summary-v1";
      case "weekly_report" -> "work-record-weekly-v1";
      case "monthly_report" -> "work-record-monthly-v1";
      default -> throw new IllegalArgumentException("unsupported AI generation type");
    };
  }

  private static void requireGenerate(String tenantId, UserPrincipal principal) {
    if (principal == null
        || !tenantId.equals(principal.tenantId())
        || !principal.hasPermission(PermissionCodes.WORK_RECORD_AI_GENERATE)) {
      throw new AccessDeniedException("not allowed to generate AI work-record content");
    }
  }

  private static void requireGenerateOrReview(String tenantId, UserPrincipal principal) {
    if (principal == null
        || !tenantId.equals(principal.tenantId())
        || !(principal.hasPermission(PermissionCodes.WORK_RECORD_AI_GENERATE)
            || principal.hasPermission(PermissionCodes.WORK_RECORD_AI_REVIEW))) {
      throw new AccessDeniedException("not allowed to read AI work-record content");
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
