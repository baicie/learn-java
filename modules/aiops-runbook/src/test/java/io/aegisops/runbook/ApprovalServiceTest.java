package io.aegisops.runbook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.runbook.dto.ApprovalDecisionCommand;
import io.aegisops.runbook.dto.ApprovalDecisionRecord;
import io.aegisops.runbook.dto.ApprovalDecisionRequest;
import io.aegisops.runbook.dto.ApprovalPolicyRecord;
import io.aegisops.runbook.dto.ApprovalProgressUpdateCommand;
import io.aegisops.runbook.dto.ApprovalTestBuilder;
import io.aegisops.runbook.dto.AutomationApprovalCreateCommand;
import io.aegisops.runbook.dto.AutomationApprovalRecord;
import io.aegisops.runbook.dto.AutomationPlanRecord;
import io.aegisops.runbook.dto.SubmitApprovalRequest;
import io.aegisops.runbook.dto.TimelineCreateCommand;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApprovalServiceTest {
  @Test
  void submitMediumPlanCreatesPendingApproval() {
    FakeApprovalRepository repository = new FakeApprovalRepository();
    repository.plan = plan("draft", "medium");
    repository.policy = policy("medium", 1, false);

    ApprovalService service = new ApprovalService(repository, new ObjectMapper());

    var response =
        service.submit(
            "tenant_1", "plan_1", new SubmitApprovalRequest("alice", "Need restart approval"));

    assertEquals("pending", response.status());
    assertEquals("pending_approval", repository.planStatus);
    assertEquals(1, response.requiredApprovals());
    assertEquals("alice", response.submittedBy());
    assertTrue(repository.timelineTypes.contains("automation_plan_submitted"));
  }

  @Test
  void submitLowPlanCanAutoApproveWithoutExecution() {
    FakeApprovalRepository repository = new FakeApprovalRepository();
    repository.plan = plan("draft", "low");
    repository.policy = policy("low", 0, false);

    ApprovalService service = new ApprovalService(repository, new ObjectMapper());

    var response =
        service.submit("tenant_1", "plan_1", new SubmitApprovalRequest("alice", "low risk"));

    assertEquals("approved", response.status());
    assertEquals("approved", repository.planStatus);
    assertEquals(0, response.requiredApprovals());
    assertTrue(repository.timelineTypes.contains("automation_plan_auto_approved"));
  }

  @Test
  void approveCriticalRequiresTwoReviewers() {
    FakeApprovalRepository repository = new FakeApprovalRepository();
    repository.plan = plan("pending_approval", "critical");
    repository.policy = policy("critical", 2, true);
    repository.approval = ApprovalTestBuilder.pending("critical", 2, 0, 0, "submitter").toRecord();

    ApprovalService service = new ApprovalService(repository, new ObjectMapper());

    var first =
        service.approve(
            "tenant_1",
            "approval_1",
            new ApprovalDecisionRequest("reviewer_1", "checked evidence"));

    assertEquals("pending", first.status());
    assertEquals(1, first.approvedCount());
    assertEquals(0, first.rejectedCount());

    var second =
        service.approve(
            "tenant_1", "approval_1", new ApprovalDecisionRequest("reviewer_2", "safe to proceed"));

    assertEquals("approved", second.status());
    assertEquals(2, second.approvedCount());
    assertTrue(repository.timelineTypes.contains("automation_plan_approved"));
  }

  @Test
  void rejectPendingApprovalRejectsPlan() {
    FakeApprovalRepository repository = new FakeApprovalRepository();
    repository.plan = plan("pending_approval", "high");
    repository.policy = policy("high", 1, true);
    repository.approval = ApprovalTestBuilder.pending("high", 1, 0, 0, "submitter").toRecord();

    ApprovalService service = new ApprovalService(repository, new ObjectMapper());

    var response =
        service.reject(
            "tenant_1", "approval_1", new ApprovalDecisionRequest("reviewer_1", "risk too high"));

    assertEquals("rejected", response.status());
    assertEquals("rejected", repository.planStatus);
    assertEquals(1, response.rejectedCount());
    assertTrue(repository.timelineTypes.contains("automation_plan_rejected"));
  }

  @Test
  void submitterCannotApproveOwnPlan() {
    FakeApprovalRepository repository = new FakeApprovalRepository();
    repository.plan = plan("pending_approval", "medium");
    repository.policy = policy("medium", 1, false);
    repository.approval = ApprovalTestBuilder.pending("medium", 1, 0, 0, "alice").toRecord();

    ApprovalService service = new ApprovalService(repository, new ObjectMapper());

    assertThrows(
        AppException.class,
        () ->
            service.approve(
                "tenant_1", "approval_1", new ApprovalDecisionRequest("alice", "self approve")));
  }

  @Test
  void reviewerCannotDecideTwice() {
    FakeApprovalRepository repository = new FakeApprovalRepository();
    repository.plan = plan("pending_approval", "critical");
    repository.policy = policy("critical", 2, true);
    repository.approval = ApprovalTestBuilder.pending("critical", 2, 0, 0, "submitter").toRecord();
    repository.existingDecisionReviewer = "reviewer_1";

    ApprovalService service = new ApprovalService(repository, new ObjectMapper());

    assertThrows(
        AppException.class,
        () ->
            service.approve(
                "tenant_1", "approval_1", new ApprovalDecisionRequest("reviewer_1", "again")));
  }

  @Test
  void submitFailsWhenPlanStatusUpdateFails() {
    FakeApprovalRepository repository = new FakeApprovalRepository();
    repository.plan = plan("draft", "medium");
    repository.policy = policy("medium", 1, false);
    repository.failPlanStatusUpdate = true;

    ApprovalService service = new ApprovalService(repository, new ObjectMapper());

    AppException ex =
        assertThrows(
            AppException.class,
            () ->
                service.submit(
                    "tenant_1", "plan_1", new SubmitApprovalRequest("alice", "Need approval")));

    assertEquals("AUTOMATION_PLAN_UPDATE_FAILED", ex.errorCode());
  }

  @Test
  void approveFailsWhenApprovalProgressUpdateFails() {
    FakeApprovalRepository repository = new FakeApprovalRepository();
    repository.plan = plan("pending_approval", "medium");
    repository.policy = policy("medium", 1, false);
    repository.approval = ApprovalTestBuilder.pending("medium", 1, 0, 0, "submitter").toRecord();
    repository.failApprovalProgressUpdate = true;

    ApprovalService service = new ApprovalService(repository, new ObjectMapper());

    AppException ex =
        assertThrows(
            AppException.class,
            () ->
                service.approve(
                    "tenant_1", "approval_1", new ApprovalDecisionRequest("reviewer_1", "ok")));

    assertEquals("APPROVAL_UPDATE_FAILED", ex.errorCode());
  }

  private AutomationPlanRecord plan(String status, String riskLevel) {
    return new AutomationPlanRecord(
        "plan_1",
        "tenant_1",
        "inc_1",
        "rb_1",
        null,
        null,
        "runbook-recommendation-v1",
        status,
        riskLevel,
        BigDecimal.valueOf(0.8),
        "title",
        "summary",
        "{}",
        "system",
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private ApprovalPolicyRecord policy(String riskLevel, int required, boolean comment) {
    return new ApprovalPolicyRecord(
        "policy_1",
        "tenant_1",
        riskLevel,
        required,
        comment,
        true,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private static class FakeApprovalRepository extends FakeRunbookRepositoryBase {
    AutomationPlanRecord plan;
    AutomationApprovalRecord approval;
    ApprovalPolicyRecord policy;
    String planStatus;
    String existingDecisionReviewer;
    boolean failPlanStatusUpdate;
    boolean failApprovalProgressUpdate;
    final List<ApprovalDecisionRecord> decisions = new ArrayList<>();
    final List<String> timelineTypes = new ArrayList<>();

    @Override
    public Optional<AutomationPlanRecord> findPlan(String tenantId, String planId) {
      return Optional.ofNullable(plan);
    }

    @Override
    public boolean updatePlanStatus(String tenantId, String planId, String status) {
      if (failPlanStatusUpdate) {
        return false;
      }
      planStatus = status;
      if (plan != null) {
        plan =
            new AutomationPlanRecord(
                plan.id(),
                plan.tenantId(),
                plan.incidentId(),
                plan.runbookId(),
                plan.aiDiagnosisId(),
                plan.rcaAnalysisId(),
                plan.source(),
                status,
                plan.riskLevel(),
                plan.confidence(),
                plan.title(),
                plan.summary(),
                plan.evidenceJson(),
                plan.createdBy(),
                plan.createdAt(),
                OffsetDateTime.now());
      }
      return true;
    }

    @Override
    public Optional<ApprovalPolicyRecord> findApprovalPolicy(String tenantId, String riskLevel) {
      return Optional.ofNullable(policy);
    }

    @Override
    public Optional<ApprovalPolicyRecord> findGlobalApprovalPolicy(String riskLevel) {
      return Optional.empty();
    }

    @Override
    public void createApproval(AutomationApprovalCreateCommand command) {
      approval =
          new AutomationApprovalRecord(
              command.id(),
              command.tenantId(),
              command.incidentId(),
              command.planId(),
              command.status(),
              command.riskLevel(),
              command.requiredApprovals(),
              command.approvedCount(),
              command.rejectedCount(),
              command.submittedBy(),
              command.submittedAt(),
              command.completedAt(),
              command.reason(),
              OffsetDateTime.now(),
              OffsetDateTime.now());
    }

    @Override
    public Optional<AutomationApprovalRecord> findApproval(String tenantId, String approvalId) {
      return Optional.ofNullable(approval);
    }

    @Override
    public Optional<AutomationApprovalRecord> findLatestApprovalByPlan(
        String tenantId, String planId) {
      return Optional.ofNullable(approval);
    }

    @Override
    public boolean updateApprovalProgress(ApprovalProgressUpdateCommand cmd) {
      if (failApprovalProgressUpdate) {
        return false;
      }
      approval =
          new AutomationApprovalRecord(
              approval.id(),
              approval.tenantId(),
              approval.incidentId(),
              approval.planId(),
              cmd.status(),
              approval.riskLevel(),
              approval.requiredApprovals(),
              cmd.approvedCount(),
              cmd.rejectedCount(),
              approval.submittedBy(),
              approval.submittedAt(),
              cmd.completedAt(),
              approval.reason(),
              approval.createdAt(),
              OffsetDateTime.now());
      return true;
    }

    @Override
    public boolean decisionExists(String tenantId, String approvalId, String reviewer) {
      return reviewer.equals(existingDecisionReviewer)
          || decisions.stream()
              .anyMatch(
                  item ->
                      item.tenantId().equals(tenantId)
                          && item.approvalId().equals(approvalId)
                          && item.reviewer().equals(reviewer));
    }

    @Override
    public void createDecision(ApprovalDecisionCommand command) {
      decisions.add(
          new ApprovalDecisionRecord(
              command.id(),
              command.tenantId(),
              command.approvalId(),
              command.planId(),
              command.reviewer(),
              command.decision(),
              command.comment(),
              command.decidedAt(),
              OffsetDateTime.now()));
    }

    @Override
    public List<ApprovalDecisionRecord> listDecisions(String approvalId) {
      return decisions;
    }

    @Override
    public void addTimeline(TimelineCreateCommand command) {
      timelineTypes.add(command.eventType());
    }
  }
}
