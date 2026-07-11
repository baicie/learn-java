package io.aegisops.runbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.runbook.dto.ApprovalDecisionCommand;
import io.aegisops.runbook.dto.ApprovalDecisionRecord;
import io.aegisops.runbook.dto.ApprovalDecisionRequest;
import io.aegisops.runbook.dto.ApprovalDecisionResponse;
import io.aegisops.runbook.dto.ApprovalPolicyRecord;
import io.aegisops.runbook.dto.ApprovalProgressUpdateCommand;
import io.aegisops.runbook.dto.ApprovalResponse;
import io.aegisops.runbook.dto.AutomationApprovalCreateCommand;
import io.aegisops.runbook.dto.AutomationApprovalRecord;
import io.aegisops.runbook.dto.AutomationPlanRecord;
import io.aegisops.runbook.dto.SubmitApprovalRequest;
import io.aegisops.runbook.dto.TimelineCreateCommand;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalService {
  private static final String SYSTEM_SOURCE = "system";

  private final RunbookRepository repository;
  private final RunbookJson json;
  private final ApprovalPolicyResolver policyResolver;

  public ApprovalService(RunbookRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.json = new RunbookJson(objectMapper);
    this.policyResolver = new ApprovalPolicyResolver(repository);
  }

  @Transactional
  public ApprovalResponse submit(String tenantId, String planId, SubmitApprovalRequest request) {
    AutomationPlanRecord plan = loadPlan(tenantId, planId);

    if (!"draft".equals(plan.status())) {
      throw new AppException(
          "AUTOMATION_PLAN_STATUS_INVALID",
          "Only draft automation plan can be submitted for approval");
    }

    var existing = repository.findLatestApprovalByPlan(tenantId, planId);
    if (existing.isPresent() && "pending".equals(existing.get().status())) {
      return toApprovalResponse(existing.get(), repository.listDecisions(existing.get().id()));
    }

    ApprovalPolicyRecord policy = policyResolver.resolve(tenantId, plan.riskLevel());

    String approvalId = newId("approval");
    OffsetDateTime now = OffsetDateTime.now();
    String submittedBy = blankToDefault(request == null ? null : request.submittedBy(), "system");
    String reason = request == null ? null : request.reason();

    boolean autoApproved = policy.requiredApprovals() == 0;

    AutomationApprovalCreateCommand command =
        new AutomationApprovalCreateCommand(
            approvalId,
            tenantId,
            plan.incidentId(),
            plan.id(),
            autoApproved ? "approved" : "pending",
            policy.riskLevel(),
            policy.requiredApprovals(),
            0,
            0,
            submittedBy,
            now,
            autoApproved ? now : null,
            reason);

    repository.createApproval(command);

    ensureUpdated(
        repository.updatePlanStatus(
            tenantId, planId, autoApproved ? "approved" : "pending_approval"),
        "AUTOMATION_PLAN_UPDATE_FAILED",
        "Automation plan status was not updated");

    repository.addTimeline(
        timeline(
            plan.incidentId(),
            autoApproved ? "automation_plan_auto_approved" : "automation_plan_submitted",
            autoApproved ? "Automation plan auto approved" : "Automation plan submitted",
            autoApproved
                ? "Low-risk automation plan was auto approved by approval policy."
                : "Automation plan submitted for approval.",
            Map.of(
                "approvalId", approvalId,
                "planId", planId,
                "riskLevel", policy.riskLevel(),
                "requiredApprovals", policy.requiredApprovals(),
                "submittedBy", submittedBy)));

    return getApproval(tenantId, approvalId);
  }

  public ApprovalResponse latestByPlan(String tenantId, String planId) {
    AutomationPlanRecord plan = loadPlan(tenantId, planId);

    AutomationApprovalRecord approval =
        repository
            .findLatestApprovalByPlan(tenantId, plan.id())
            .orElseThrow(() -> new AppException("APPROVAL_NOT_FOUND", "Approval not found"));

    return toApprovalResponse(approval, repository.listDecisions(approval.id()));
  }

  public ApprovalResponse getApproval(String tenantId, String approvalId) {
    AutomationApprovalRecord approval =
        repository
            .findApproval(tenantId, approvalId)
            .orElseThrow(() -> new AppException("APPROVAL_NOT_FOUND", "Approval not found"));

    return toApprovalResponse(approval, repository.listDecisions(approval.id()));
  }

  @Transactional
  public ApprovalResponse approve(
      String tenantId, String approvalId, ApprovalDecisionRequest request) {
    AutomationApprovalRecord approval = loadPendingApproval(tenantId, approvalId);
    String reviewer = requiredReviewer(request);
    String comment = request == null ? null : request.comment();

    ApprovalPolicyRecord policy = policyResolver.resolve(tenantId, approval.riskLevel());
    if (policy.requireComment() && (comment == null || comment.isBlank())) {
      throw new AppException(
          "APPROVAL_COMMENT_REQUIRED", "Approval comment is required for this risk level");
    }

    ensureReviewerCanDecide(tenantId, approval, reviewer);

    int approvedCount = approval.approvedCount() + 1;
    int rejectedCount = approval.rejectedCount();
    boolean completed = approvedCount >= approval.requiredApprovals();

    repository.createDecision(
        new ApprovalDecisionCommand(
            newId("decision"),
            tenantId,
            approval.id(),
            approval.planId(),
            reviewer,
            "approve",
            comment,
            OffsetDateTime.now()));

    ensureUpdated(
        repository.updateApprovalProgress(
            new ApprovalProgressUpdateCommand(
                tenantId,
                approval.id(),
                completed ? "approved" : "pending",
                approvedCount,
                rejectedCount,
                completed ? OffsetDateTime.now() : null)),
        "APPROVAL_UPDATE_FAILED",
        "Approval progress was not updated");

    if (completed) {
      ensureUpdated(
          repository.updatePlanStatus(tenantId, approval.planId(), "approved"),
          "AUTOMATION_PLAN_UPDATE_FAILED",
          "Automation plan status was not updated");

      repository.addTimeline(
          timeline(
              approval.incidentId(),
              "automation_plan_approved",
              "Automation plan approved",
              "Automation plan received required approvals.",
              Map.of(
                  "approvalId", approval.id(),
                  "planId", approval.planId(),
                  "approvedCount", approvedCount,
                  "requiredApprovals", approval.requiredApprovals())));
    }

    return getApproval(tenantId, approval.id());
  }

  @Transactional
  public ApprovalResponse reject(
      String tenantId, String approvalId, ApprovalDecisionRequest request) {
    AutomationApprovalRecord approval = loadPendingApproval(tenantId, approvalId);
    String reviewer = requiredReviewer(request);
    String comment = request == null ? null : request.comment();

    if (comment == null || comment.isBlank()) {
      throw new AppException("APPROVAL_REJECT_COMMENT_REQUIRED", "Reject comment is required");
    }

    ensureReviewerCanDecide(tenantId, approval, reviewer);

    int approvedCount = approval.approvedCount();
    int rejectedCount = approval.rejectedCount() + 1;

    repository.createDecision(
        new ApprovalDecisionCommand(
            newId("decision"),
            tenantId,
            approval.id(),
            approval.planId(),
            reviewer,
            "reject",
            comment,
            OffsetDateTime.now()));

    ensureUpdated(
        repository.updateApprovalProgress(
            new ApprovalProgressUpdateCommand(
                tenantId,
                approval.id(),
                "rejected",
                approvedCount,
                rejectedCount,
                OffsetDateTime.now())),
        "APPROVAL_UPDATE_FAILED",
        "Approval progress was not updated");

    ensureUpdated(
        repository.updatePlanStatus(tenantId, approval.planId(), "rejected"),
        "AUTOMATION_PLAN_UPDATE_FAILED",
        "Automation plan status was not updated");

    repository.addTimeline(
        timeline(
            approval.incidentId(),
            "automation_plan_rejected",
            "Automation plan rejected",
            "Automation plan was rejected by reviewer.",
            Map.of(
                "approvalId", approval.id(),
                "planId", approval.planId(),
                "reviewer", reviewer)));

    return getApproval(tenantId, approval.id());
  }

  @Transactional
  public ApprovalResponse cancel(
      String tenantId, String approvalId, ApprovalDecisionRequest request) {
    AutomationApprovalRecord approval = loadPendingApproval(tenantId, approvalId);
    String reviewer = requiredReviewer(request);
    String comment = request == null ? null : request.comment();

    ensureUpdated(
        repository.updateApprovalProgress(
            new ApprovalProgressUpdateCommand(
                tenantId,
                approval.id(),
                "cancelled",
                approval.approvedCount(),
                approval.rejectedCount(),
                OffsetDateTime.now())),
        "APPROVAL_UPDATE_FAILED",
        "Approval progress was not updated");

    ensureUpdated(
        repository.updatePlanStatus(tenantId, approval.planId(), "draft"),
        "AUTOMATION_PLAN_UPDATE_FAILED",
        "Automation plan status was not updated");

    repository.addTimeline(
        timeline(
            approval.incidentId(),
            "automation_approval_cancelled",
            "Automation approval cancelled",
            "Automation approval was cancelled before completion.",
            Map.of(
                "approvalId",
                approval.id(),
                "planId",
                approval.planId(),
                "cancelledBy",
                reviewer,
                "comment",
                comment == null ? "" : comment)));

    return getApproval(tenantId, approval.id());
  }

  private AutomationPlanRecord loadPlan(String tenantId, String planId) {
    return repository
        .findPlan(tenantId, planId)
        .orElseThrow(
            () -> new AppException("AUTOMATION_PLAN_NOT_FOUND", "Automation plan not found"));
  }

  private AutomationApprovalRecord loadPendingApproval(String tenantId, String approvalId) {
    AutomationApprovalRecord approval =
        repository
            .findApproval(tenantId, approvalId)
            .orElseThrow(() -> new AppException("APPROVAL_NOT_FOUND", "Approval not found"));

    if (!"pending".equals(approval.status())) {
      throw new AppException("APPROVAL_STATUS_INVALID", "Only pending approval can be decided");
    }

    return approval;
  }

  private void ensureReviewerCanDecide(
      String tenantId, AutomationApprovalRecord approval, String reviewer) {
    if (approval.submittedBy().equals(reviewer)) {
      throw new AppException(
          "APPROVAL_REVIEWER_INVALID", "Submitter cannot approve or reject own automation plan");
    }

    if (repository.decisionExists(tenantId, approval.id(), reviewer)) {
      throw new AppException(
          "APPROVAL_DECISION_DUPLICATED", "Reviewer already made a decision for this approval");
    }
  }

  private String requiredReviewer(ApprovalDecisionRequest request) {
    if (request == null || request.reviewer() == null || request.reviewer().isBlank()) {
      throw new AppException("APPROVAL_REVIEWER_REQUIRED", "Reviewer is required");
    }

    return request.reviewer().trim();
  }

  private ApprovalResponse toApprovalResponse(
      AutomationApprovalRecord approval, List<ApprovalDecisionRecord> decisions) {
    return new ApprovalResponse(
        approval.id(),
        approval.tenantId(),
        approval.incidentId(),
        approval.planId(),
        approval.status(),
        approval.riskLevel(),
        approval.requiredApprovals(),
        approval.approvedCount(),
        approval.rejectedCount(),
        approval.submittedBy(),
        approval.submittedAt(),
        approval.completedAt(),
        approval.reason(),
        decisions.stream().map(record -> toDecisionResponse(record)).toList(),
        approval.createdAt(),
        approval.updatedAt());
  }

  private ApprovalDecisionResponse toDecisionResponse(ApprovalDecisionRecord record) {
    return new ApprovalDecisionResponse(
        record.id(),
        record.approvalId(),
        record.planId(),
        record.reviewer(),
        record.decision(),
        record.comment(),
        record.decidedAt(),
        record.createdAt());
  }

  private TimelineCreateCommand timeline(
      String incidentId,
      String eventType,
      String title,
      String description,
      Map<String, Object> payload) {
    return new TimelineCreateCommand(
        newId("tl"),
        incidentId,
        OffsetDateTime.now(),
        eventType,
        title,
        description,
        SYSTEM_SOURCE,
        json.write(payload));
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private void ensureUpdated(boolean updated, String code, String message) {
    if (!updated) {
      throw new AppException(code, message);
    }
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
