package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.ExecutionStepCreateCommand;
import io.aegisops.execution.dto.RollbackExecutionCreateRequest;
import io.aegisops.execution.dto.RollbackPlanRecord;
import io.aegisops.execution.dto.RollbackPlanStepRecord;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RollbackExecutionService {
  private final RollbackRepository rollbackRepository;
  private final ExecutionRepository executionRepository;
  private final ExecutionRequestService executionRequestService;
  private final ExecutionProperties properties;
  private final RollbackJson json;

  public RollbackExecutionService(
      RollbackRepository rollbackRepository,
      ExecutionRepository executionRepository,
      ExecutionRequestService executionRequestService,
      ExecutionProperties properties,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    this.rollbackRepository = rollbackRepository;
    this.executionRepository = executionRepository;
    this.executionRequestService = executionRequestService;
    this.properties = properties;
    this.json = new RollbackJson(objectMapper);
  }

  @Transactional
  public ExecutionRunResponse createExecution(
      String tenantId, String rollbackPlanId, RollbackExecutionCreateRequest request) {
    RollbackPlanRecord plan =
        rollbackRepository
            .findRollbackPlan(tenantId, rollbackPlanId)
            .orElseThrow(
                () -> new AppException("ROLLBACK_PLAN_NOT_FOUND", "Rollback plan not found"));

    if (!"approved".equals(plan.status())) {
      throw new AppException(
          "ROLLBACK_EXECUTION_STATUS_INVALID", "Only approved rollback plan can be executed");
    }

    List<RollbackPlanStepRecord> steps = rollbackRepository.listSteps(tenantId, rollbackPlanId);
    if (steps.isEmpty()) {
      throw new AppException("ROLLBACK_STEPS_EMPTY", "Rollback plan has no steps");
    }

    String executionId = newId("exec");

    executionRepository.createRun(
        new ExecutionRunCreateCommand(
            executionId,
            tenantId,
            plan.incidentId(),
            plan.sourcePlanId(),
            "queued",
            "live",
            blankToDefault(request == null ? null : request.requestedBy(), "system"),
            1,
            normalizeMaxAttempts(request == null ? null : request.maxAttempts()),
            null,
            properties.normalizedRunTimeoutSeconds(),
            plan.id(),
            normalizeRollbackApprovalSnapshot(plan),
            plan.riskLevel(),
            "rollback",
            plan.id(),
            plan.sourceExecutionId()));

    executionRepository.createSteps(
        steps.stream()
            .map(
                step ->
                    new ExecutionStepCreateCommand(
                        newId("execstep"),
                        tenantId,
                        executionId,
                        step.id(),
                        step.stepOrder(),
                        step.title(),
                        step.actionType(),
                        step.targetType(),
                        "queued",
                        step.actionPayloadJson(),
                        null,
                        1,
                        properties.normalizedStepTimeoutSeconds()))
            .toList());

    boolean marked = rollbackRepository.markExecuting(tenantId, rollbackPlanId);
    if (!marked) {
      throw new AppException(
          "ROLLBACK_MARK_EXECUTING_FAILED", "Rollback plan was not marked executing");
    }

    return executionRequestService.getExecution(tenantId, executionId);
  }

  private int normalizeMaxAttempts(Integer value) {
    if (value == null) {
      return 1;
    }
    return Math.max(1, Math.min(value, 3));
  }

  private String normalizeRollbackApprovalSnapshot(RollbackPlanRecord plan) {
    return json.write(
        Map.ofEntries(
            Map.entry("approvalId", plan.id()),
            Map.entry("rollbackPlanId", plan.id()),
            Map.entry("planId", plan.sourcePlanId()),
            Map.entry("sourceExecutionId", plan.sourceExecutionId()),
            Map.entry("status", "approved"),
            Map.entry("requiredApprovals", plan.requiredApprovals()),
            Map.entry("approvedCount", plan.approvedCount())));
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
