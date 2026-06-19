package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.RollbackDecisionCreateCommand;
import io.aegisops.execution.dto.RollbackDecisionRequest;
import io.aegisops.execution.dto.RollbackPlanRecord;
import io.aegisops.execution.dto.RollbackPlanResponse;
import io.aegisops.execution.dto.RollbackPlanSubmitRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RollbackApprovalService {
  private final RollbackRepository rollbackRepository;
  private final RollbackPlanService rollbackPlanService;
  private final RollbackJson json;

  public RollbackApprovalService(
      RollbackRepository rollbackRepository,
      RollbackPlanService rollbackPlanService,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    this.rollbackRepository = rollbackRepository;
    this.rollbackPlanService = rollbackPlanService;
    this.json = new RollbackJson(objectMapper);
  }

  @Transactional
  public RollbackPlanResponse submit(
      String tenantId, String rollbackPlanId, RollbackPlanSubmitRequest request) {
    RollbackPlanRecord plan = load(tenantId, rollbackPlanId);

    if (!"draft".equals(plan.status())) {
      throw new AppException(
          "ROLLBACK_SUBMIT_STATUS_INVALID", "Only draft rollback plan can be submitted");
    }

    boolean updated =
        rollbackRepository.submitPlan(
            tenantId,
            rollbackPlanId,
            blankToDefault(request == null ? null : request.submittedBy(), "system"));

    if (!updated) {
      throw new AppException("ROLLBACK_SUBMIT_FAILED", "Rollback plan was not submitted");
    }

    return rollbackPlanService.get(tenantId, rollbackPlanId);
  }

  @Transactional
  public RollbackPlanResponse approve(
      String tenantId, String rollbackPlanId, RollbackDecisionRequest request) {
    RollbackPlanRecord plan = loadPending(tenantId, rollbackPlanId);
    String reviewer = requireReviewer(request);

    if (rollbackRepository.decisionExists(tenantId, rollbackPlanId, reviewer)) {
      throw new AppException("ROLLBACK_DECISION_EXISTS", "Reviewer has already decided");
    }

    rollbackRepository.createDecision(
        new RollbackDecisionCreateCommand(
            newId("rbd"), tenantId, rollbackPlanId, reviewer, "approve", request.comment()));

    int approvedCount = rollbackRepository.countDecisions(tenantId, rollbackPlanId, "approve");

    if (approvedCount >= plan.requiredApprovals()) {
      boolean updated =
          rollbackRepository.markApproved(
              tenantId,
              rollbackPlanId,
              approvedCount,
              json.write(
                  Map.ofEntries(
                      Map.entry("approvalId", plan.id()),
                      Map.entry("rollbackPlanId", rollbackPlanId),
                      Map.entry("planId", plan.sourcePlanId()),
                      Map.entry("sourceExecutionId", plan.sourceExecutionId()),
                      Map.entry("status", "approved"),
                      Map.entry("requiredApprovals", plan.requiredApprovals()),
                      Map.entry("approvedCount", approvedCount),
                      Map.entry("reviewer", reviewer))));

      if (!updated) {
        throw new AppException("ROLLBACK_APPROVAL_FAILED", "Rollback plan was not approved");
      }
    }

    return rollbackPlanService.get(tenantId, rollbackPlanId);
  }

  @Transactional
  public RollbackPlanResponse reject(
      String tenantId, String rollbackPlanId, RollbackDecisionRequest request) {
    RollbackPlanRecord plan = loadPending(tenantId, rollbackPlanId);
    String reviewer = requireReviewer(request);

    if (rollbackRepository.decisionExists(tenantId, rollbackPlanId, reviewer)) {
      throw new AppException("ROLLBACK_DECISION_EXISTS", "Reviewer has already decided");
    }

    rollbackRepository.createDecision(
        new RollbackDecisionCreateCommand(
            newId("rbd"), tenantId, rollbackPlanId, reviewer, "reject", request.comment()));

    int rejectedCount = rollbackRepository.countDecisions(tenantId, rollbackPlanId, "reject");

    boolean updated =
        rollbackRepository.markRejected(
            tenantId,
            rollbackPlanId,
            rejectedCount,
            json.write(
                Map.ofEntries(
                    Map.entry("approvalId", plan.id()),
                    Map.entry("rollbackPlanId", rollbackPlanId),
                    Map.entry("planId", plan.sourcePlanId()),
                    Map.entry("sourceExecutionId", plan.sourceExecutionId()),
                    Map.entry("status", "rejected"),
                    Map.entry("reviewer", reviewer),
                    Map.entry("reason", request.comment() == null ? "" : request.comment()))));

    if (!updated) {
      throw new AppException("ROLLBACK_REJECT_FAILED", "Rollback plan was not rejected");
    }

    return rollbackPlanService.get(tenantId, rollbackPlanId);
  }

  private RollbackPlanRecord load(String tenantId, String rollbackPlanId) {
    return rollbackRepository
        .findRollbackPlan(tenantId, rollbackPlanId)
        .orElseThrow(() -> new AppException("ROLLBACK_PLAN_NOT_FOUND", "Rollback plan not found"));
  }

  private RollbackPlanRecord loadPending(String tenantId, String rollbackPlanId) {
    RollbackPlanRecord plan = load(tenantId, rollbackPlanId);

    if (!"pending_approval".equals(plan.status())) {
      throw new AppException(
          "ROLLBACK_DECISION_STATUS_INVALID", "Rollback plan is not pending approval");
    }

    return plan;
  }

  private String requireReviewer(RollbackDecisionRequest request) {
    if (request == null || request.reviewer() == null || request.reviewer().isBlank()) {
      throw new AppException("ROLLBACK_REVIEWER_REQUIRED", "Rollback reviewer is required");
    }
    return request.reviewer().trim();
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
