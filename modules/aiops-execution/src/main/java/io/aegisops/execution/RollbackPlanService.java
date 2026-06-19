package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.RollbackDecisionRecord;
import io.aegisops.execution.dto.RollbackDecisionResponse;
import io.aegisops.execution.dto.RollbackPlanCreateCommand;
import io.aegisops.execution.dto.RollbackPlanCreateRequest;
import io.aegisops.execution.dto.RollbackPlanRecord;
import io.aegisops.execution.dto.RollbackPlanResponse;
import io.aegisops.execution.dto.RollbackPlanStepCreateCommand;
import io.aegisops.execution.dto.RollbackPlanStepRecord;
import io.aegisops.execution.dto.RollbackPlanStepResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RollbackPlanService {
  private final RollbackRepository rollbackRepository;
  private final ExecutionRepository executionRepository;
  private final RollbackPayloadExtractor extractor;

  public RollbackPlanService(
      RollbackRepository rollbackRepository,
      ExecutionRepository executionRepository,
      RollbackPayloadExtractor extractor) {
    this.rollbackRepository = rollbackRepository;
    this.executionRepository = executionRepository;
    this.extractor = extractor;
  }

  @Transactional
  public RollbackPlanResponse create(
      String tenantId, String sourceExecutionId, RollbackPlanCreateRequest request) {
    ExecutionRunRecord source =
        executionRepository
            .findRun(tenantId, sourceExecutionId)
            .orElseThrow(
                () -> new AppException("EXECUTION_NOT_FOUND", "Source execution not found"));

    validateSourceExecution(source);

    List<ExecutionStepRecord> sourceSteps =
        executionRepository.listExecutionSteps(tenantId, source.id());

    List<RollbackPlanStepCreateCommand> rollbackSteps = buildRollbackSteps(tenantId, sourceSteps);

    if (rollbackSteps.isEmpty()) {
      throw new AppException(
          "ROLLBACK_STEPS_EMPTY", "Source execution does not contain explicit rollback payload");
    }

    String rollbackPlanId = newId("rbp");

    rollbackRepository.createPlan(
        new RollbackPlanCreateCommand(
            rollbackPlanId,
            tenantId,
            source.incidentId(),
            source.planId(),
            source.id(),
            "draft",
            normalizeRiskLevel(request == null ? null : request.riskLevel()),
            request == null ? null : request.reason(),
            normalizeRequiredApprovals(request == null ? null : request.requiredApprovals()),
            blankToDefault(request == null ? null : request.createdBy(), "system")));

    int order = 1;
    for (RollbackPlanStepCreateCommand step : rollbackSteps) {
      rollbackRepository.createStep(
          new RollbackPlanStepCreateCommand(
              step.id(),
              tenantId,
              rollbackPlanId,
              step.sourceStepId(),
              order++,
              step.title(),
              step.description(),
              step.actionType(),
              step.targetType(),
              step.actionPayloadJson()));
    }

    return get(tenantId, rollbackPlanId);
  }

  public RollbackPlanResponse get(String tenantId, String rollbackPlanId) {
    RollbackPlanRecord plan =
        rollbackRepository
            .findRollbackPlan(tenantId, rollbackPlanId)
            .orElseThrow(
                () -> new AppException("ROLLBACK_PLAN_NOT_FOUND", "Rollback plan not found"));

    return toResponse(
        plan,
        rollbackRepository.listSteps(tenantId, rollbackPlanId),
        rollbackRepository.listDecisions(tenantId, rollbackPlanId));
  }

  public RollbackPlanResponse latestBySourceExecution(String tenantId, String sourceExecutionId) {
    RollbackPlanRecord plan =
        rollbackRepository
            .findLatestRollbackPlanBySourceExecution(tenantId, sourceExecutionId)
            .orElseThrow(
                () -> new AppException("ROLLBACK_PLAN_NOT_FOUND", "Rollback plan not found"));

    return get(tenantId, plan.id());
  }

  @Transactional
  public RollbackPlanResponse cancel(String tenantId, String rollbackPlanId) {
    RollbackPlanRecord plan =
        rollbackRepository
            .findRollbackPlan(tenantId, rollbackPlanId)
            .orElseThrow(
                () -> new AppException("ROLLBACK_PLAN_NOT_FOUND", "Rollback plan not found"));

    if (!List.of("draft", "pending_approval").contains(plan.status())) {
      throw new AppException(
          "ROLLBACK_CANCEL_STATUS_INVALID", "Only draft or pending rollback plan can be cancelled");
    }

    boolean updated =
        rollbackRepository.updatePlanStatus(tenantId, rollbackPlanId, plan.status(), "cancelled");

    if (!updated) {
      throw new AppException("ROLLBACK_CANCEL_FAILED", "Rollback plan was not cancelled");
    }

    return get(tenantId, rollbackPlanId);
  }

  private void validateSourceExecution(ExecutionRunRecord source) {
    if (!"normal".equals(source.executionKind())) {
      throw new AppException(
          "ROLLBACK_SOURCE_KIND_INVALID", "Only normal execution can be rolled back");
    }

    if (!"live".equals(source.mode())) {
      throw new AppException(
          "ROLLBACK_SOURCE_MODE_INVALID", "Only live execution can be rolled back");
    }

    if (!List.of("succeeded", "failed").contains(source.status())) {
      throw new AppException(
          "ROLLBACK_SOURCE_STATUS_INVALID",
          "Only succeeded or failed live execution can create rollback plan");
    }
  }

  private List<RollbackPlanStepCreateCommand> buildRollbackSteps(
      String tenantId, List<ExecutionStepRecord> sourceSteps) {
    return sourceSteps.stream()
        .sorted(Comparator.comparingInt(ExecutionStepRecord::sequenceNo).reversed())
        .map(step -> toRollbackStep(tenantId, step))
        .filter(Objects::nonNull)
        .toList();
  }

  private RollbackPlanStepCreateCommand toRollbackStep(
      String tenantId, ExecutionStepRecord sourceStep) {
    RollbackPayloadExtractor.RollbackPayload payload =
        extractor.extract(sourceStep.actionPayloadJson());

    if (payload == null) {
      return null;
    }

    return new RollbackPlanStepCreateCommand(
        newId("rbps"),
        tenantId,
        "",
        sourceStep.id(),
        0,
        payload.title(),
        payload.description(),
        payload.actionType(),
        payload.targetType(),
        payload.actionPayloadJson());
  }

  private RollbackPlanResponse toResponse(
      RollbackPlanRecord plan,
      List<RollbackPlanStepRecord> steps,
      List<RollbackDecisionRecord> decisions) {
    return new RollbackPlanResponse(
        plan.id(),
        plan.tenantId(),
        plan.incidentId(),
        plan.sourcePlanId(),
        plan.sourceExecutionId(),
        plan.status(),
        plan.riskLevel(),
        plan.reason(),
        plan.requiredApprovals(),
        plan.approvedCount(),
        plan.rejectedCount(),
        plan.createdBy(),
        plan.submittedBy(),
        plan.submittedAt(),
        plan.decidedAt(),
        plan.approvalSnapshotJson(),
        steps.stream().map(this::toStepResponse).toList(),
        decisions.stream().map(this::toDecisionResponse).toList(),
        plan.createdAt(),
        plan.updatedAt());
  }

  private RollbackPlanStepResponse toStepResponse(RollbackPlanStepRecord step) {
    return new RollbackPlanStepResponse(
        step.id(),
        step.sourceStepId(),
        step.stepOrder(),
        step.title(),
        step.description(),
        step.actionType(),
        step.targetType(),
        step.actionPayloadJson(),
        step.createdAt(),
        step.updatedAt());
  }

  private RollbackDecisionResponse toDecisionResponse(RollbackDecisionRecord decision) {
    return new RollbackDecisionResponse(
        decision.id(),
        decision.reviewer(),
        decision.decision(),
        decision.comment(),
        decision.createdAt());
  }

  private String normalizeRiskLevel(String value) {
    String risk = value == null || value.isBlank() ? "high" : value.trim().toLowerCase();
    if (!List.of("low", "medium", "high", "critical").contains(risk)) {
      throw new AppException("ROLLBACK_RISK_LEVEL_INVALID", "Invalid rollback risk level");
    }
    return risk;
  }

  private int normalizeRequiredApprovals(Integer value) {
    if (value == null) {
      return 1;
    }
    return Math.max(1, Math.min(value, 5));
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
