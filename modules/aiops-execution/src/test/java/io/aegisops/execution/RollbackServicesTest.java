package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.RollbackDecisionRequest;
import io.aegisops.execution.dto.RollbackExecutionCreateRequest;
import io.aegisops.execution.dto.RollbackPlanCreateRequest;
import io.aegisops.execution.dto.RollbackPlanResponse;
import io.aegisops.execution.dto.RollbackPlanSubmitRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 集中覆盖 RollbackPayloadExtractor / RollbackPlanService / RollbackApprovalService /
 * RollbackExecutionService。
 */
class RollbackServicesTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void payloadExtractorReadsRollbackBlock() {
    RollbackPayloadExtractor extractor = new RollbackPayloadExtractor(mapper);
    String payload =
        "{\"command\":\"a\",\"rollback\":{\"actionType\":\"shell\",\"targetType\":\"host\",\"actionPayload\":{\"command\":\"undo\"}}}";

    RollbackPayloadExtractor.RollbackPayload payloadValue = extractor.extract(payload);

    assertNotNull(payloadValue);
    assertEquals("shell", payloadValue.actionType());
    assertEquals("host", payloadValue.targetType());
    assertTrue(payloadValue.actionPayloadJson().contains("undo"));
  }

  @Test
  void payloadExtractorReturnsNullWhenNoRollbackBlock() {
    RollbackPayloadExtractor extractor = new RollbackPayloadExtractor(mapper);
    String payload = "{\"command\":\"a\"}";

    assertNull(extractor.extract(payload));
  }

  @Test
  void payloadExtractorRejectsRollbackWithoutActionType() {
    RollbackPayloadExtractor extractor = new RollbackPayloadExtractor(mapper);
    String payload = "{\"rollback\":{\"actionPayload\":{}}}";

    assertThrows(AppException.class, () -> extractor.extract(payload));
  }

  @Test
  void planServiceRejectsRollbackOfDryRunExecution() {
    RollbackTestSupport.FakeExecutionRepository repo =
        new RollbackTestSupport.FakeExecutionRepository();
    RollbackTestSupport.FakeRollbackRepository rollback =
        new RollbackTestSupport.FakeRollbackRepository();
    repo.runRecord = RollbackTestSupport.executionRecord("dry_run", "succeeded", "normal");

    RollbackPlanService service =
        new RollbackPlanService(rollback, repo, new RollbackPayloadExtractor(mapper));

    AppException ex = assertRejects(service);
    assertEquals("ROLLBACK_SOURCE_MODE_INVALID", ex.errorCode());
  }

  @Test
  void planServiceRejectsRollbackOfRunningExecution() {
    RollbackTestSupport.FakeExecutionRepository repo =
        new RollbackTestSupport.FakeExecutionRepository();
    RollbackTestSupport.FakeRollbackRepository rollback =
        new RollbackTestSupport.FakeRollbackRepository();
    repo.runRecord = RollbackTestSupport.executionRecord("live", "running", "normal");

    RollbackPlanService service =
        new RollbackPlanService(rollback, repo, new RollbackPayloadExtractor(mapper));

    AppException ex = assertRejects(service);
    assertEquals("ROLLBACK_SOURCE_STATUS_INVALID", ex.errorCode());
  }

  @Test
  void planServiceCreatesPlanAndSteps() {
    RollbackTestSupport.FakeExecutionRepository repo =
        new RollbackTestSupport.FakeExecutionRepository();
    RollbackTestSupport.FakeRollbackRepository rollback =
        new RollbackTestSupport.FakeRollbackRepository();
    repo.runRecord = RollbackTestSupport.executionRecord("live", "succeeded", "normal");
    repo.steps.add(
        RollbackTestSupport.stepRecord(
            "step_2",
            2,
            "shell",
            "{\"command\":\"deploy\",\"rollback\":{\"actionType\":\"shell\",\"targetType\":\"host\",\"actionPayload\":{\"command\":\"undo\"}}}"));
    repo.steps.add(
        RollbackTestSupport.stepRecord("step_1", 1, "manual", "{\"command\":\"check\"}"));

    RollbackPlanService service =
        new RollbackPlanService(rollback, repo, new RollbackPayloadExtractor(mapper));

    RollbackPlanResponse response =
        service.create(
            "tenant_1", "exec_1", new RollbackPlanCreateRequest("revert", "high", 2, "alice"));

    assertEquals("draft", response.status());
    assertEquals("high", response.riskLevel());
    assertEquals(2, response.requiredApprovals());
    assertEquals(1, response.steps().size());
    assertEquals("step_2", response.steps().get(0).sourceStepId());
    assertEquals(1, rollback.createdPlan.size());
    assertEquals(1, rollback.createdSteps.size());
  }

  @Test
  void planServiceRejectsInvalidRiskLevel() {
    RollbackTestSupport.FakeExecutionRepository repo =
        new RollbackTestSupport.FakeExecutionRepository();
    RollbackTestSupport.FakeRollbackRepository rollback =
        new RollbackTestSupport.FakeRollbackRepository();
    repo.runRecord = RollbackTestSupport.executionRecord("live", "failed", "normal");

    RollbackPlanService service =
        new RollbackPlanService(rollback, repo, new RollbackPayloadExtractor(mapper));

    assertThrows(
        AppException.class,
        () ->
            service.create(
                "tenant_1",
                "exec_1",
                new RollbackPlanCreateRequest("reason", "extreme", 1, "alice")));
  }

  @Test
  void planServiceRejectsSourceWithoutRollbackPayload() {
    RollbackTestSupport.FakeExecutionRepository repo =
        new RollbackTestSupport.FakeExecutionRepository();
    RollbackTestSupport.FakeRollbackRepository rollback =
        new RollbackTestSupport.FakeRollbackRepository();
    repo.runRecord = RollbackTestSupport.executionRecord("live", "succeeded", "normal");
    repo.steps.add(RollbackTestSupport.stepRecord("step_1", 1, "manual", "{}"));

    RollbackPlanService service =
        new RollbackPlanService(rollback, repo, new RollbackPayloadExtractor(mapper));

    assertThrows(
        AppException.class,
        () ->
            service.create(
                "tenant_1", "exec_1", new RollbackPlanCreateRequest("reason", "high", 1, "alice")));
  }

  @Test
  void planServiceCancelRejectsWhenStatusInvalid() {
    RollbackTestSupport.FakeExecutionRepository repo =
        new RollbackTestSupport.FakeExecutionRepository();
    RollbackTestSupport.FakeRollbackRepository rollback =
        new RollbackTestSupport.FakeRollbackRepository();
    rollback.planById =
        Optional.of(
            RollbackTestSupport.rollbackPlanRecord("rbp_1", "tenant_1", "inc_1", "approved"));

    RollbackPlanService service =
        new RollbackPlanService(rollback, repo, new RollbackPayloadExtractor(mapper));

    assertThrows(AppException.class, () -> service.cancel("tenant_1", "rbp_1"));
  }

  @Test
  void planServiceCancelUpdatesDraft() {
    RollbackTestSupport.FakeExecutionRepository repo =
        new RollbackTestSupport.FakeExecutionRepository();
    RollbackTestSupport.FakeRollbackRepository rollback =
        new RollbackTestSupport.FakeRollbackRepository();
    rollback.planById =
        Optional.of(RollbackTestSupport.rollbackPlanRecord("rbp_1", "tenant_1", "inc_1", "draft"));

    RollbackPlanService service =
        new RollbackPlanService(rollback, repo, new RollbackPayloadExtractor(mapper));

    RollbackPlanResponse response = service.cancel("tenant_1", "rbp_1");

    assertEquals("cancelled", response.status());
    assertEquals("cancelled", rollback.lastStatus);
  }

  @Test
  void approvalServiceRejectsReviewerTwice() {
    RollbackTestSupport.FakeExecutionRepository repo =
        new RollbackTestSupport.FakeExecutionRepository();
    RollbackTestSupport.FakeRollbackRepository rollback =
        new RollbackTestSupport.FakeRollbackRepository();
    rollback.planById =
        Optional.of(
            RollbackTestSupport.rollbackPlanRecord(
                "rbp_1", "tenant_1", "inc_1", "pending_approval"));
    rollback.decisionExists = true;

    RollbackPlanService planService =
        new RollbackPlanService(rollback, repo, new RollbackPayloadExtractor(mapper));
    RollbackApprovalService service = new RollbackApprovalService(rollback, planService, mapper);

    assertThrows(
        AppException.class,
        () -> service.approve("tenant_1", "rbp_1", new RollbackDecisionRequest("bob", "ok")));
  }

  @Test
  void approvalServiceApprovesWhenEnoughDecisions() {
    RollbackTestSupport.FakeExecutionRepository repo =
        new RollbackTestSupport.FakeExecutionRepository();
    RollbackTestSupport.FakeRollbackRepository rollback =
        new RollbackTestSupport.FakeRollbackRepository();
    rollback.planById =
        Optional.of(
            RollbackTestSupport.rollbackPlanRecord(
                new RollbackTestSupport.RollbackKey(
                    "rbp_1", "tenant_1", "inc_1", "pending_approval", 0, 1)));
    rollback.decisionExists = false;

    RollbackPlanService planService =
        new RollbackPlanService(rollback, repo, new RollbackPayloadExtractor(mapper));
    RollbackApprovalService service = new RollbackApprovalService(rollback, planService, mapper);

    service.approve("tenant_1", "rbp_1", new RollbackDecisionRequest("bob", "ok"));

    assertEquals("approved", rollback.lastStatus);
    assertTrue(rollback.lastSnapshot.contains("approved"));
  }

  @Test
  void approvalServiceRejectsDecisionWhenNotPending() {
    RollbackTestSupport.FakeExecutionRepository repo =
        new RollbackTestSupport.FakeExecutionRepository();
    RollbackTestSupport.FakeRollbackRepository rollback =
        new RollbackTestSupport.FakeRollbackRepository();
    rollback.planById =
        Optional.of(RollbackTestSupport.rollbackPlanRecord("rbp_1", "tenant_1", "inc_1", "draft"));

    RollbackPlanService planService =
        new RollbackPlanService(rollback, repo, new RollbackPayloadExtractor(mapper));
    RollbackApprovalService service = new RollbackApprovalService(rollback, planService, mapper);

    assertThrows(
        AppException.class,
        () -> service.approve("tenant_1", "rbp_1", new RollbackDecisionRequest("bob", "ok")));
  }

  @Test
  void approvalServiceRejectsBlankReviewer() {
    RollbackTestSupport.FakeExecutionRepository repo =
        new RollbackTestSupport.FakeExecutionRepository();
    RollbackTestSupport.FakeRollbackRepository rollback =
        new RollbackTestSupport.FakeRollbackRepository();
    rollback.planById =
        Optional.of(
            RollbackTestSupport.rollbackPlanRecord(
                "rbp_1", "tenant_1", "inc_1", "pending_approval"));

    RollbackPlanService planService =
        new RollbackPlanService(rollback, repo, new RollbackPayloadExtractor(mapper));
    RollbackApprovalService service = new RollbackApprovalService(rollback, planService, mapper);

    assertThrows(
        AppException.class,
        () -> service.approve("tenant_1", "rbp_1", new RollbackDecisionRequest(" ", "ok")));
  }

  @Test
  void approvalServiceSubmitOnlyDraft() {
    RollbackTestSupport.FakeExecutionRepository repo =
        new RollbackTestSupport.FakeExecutionRepository();
    RollbackTestSupport.FakeRollbackRepository rollback =
        new RollbackTestSupport.FakeRollbackRepository();
    rollback.planById =
        Optional.of(
            RollbackTestSupport.rollbackPlanRecord("rbp_1", "tenant_1", "inc_1", "approved"));

    RollbackPlanService planService =
        new RollbackPlanService(rollback, repo, new RollbackPayloadExtractor(mapper));
    RollbackApprovalService service = new RollbackApprovalService(rollback, planService, mapper);

    assertThrows(
        AppException.class,
        () -> service.submit("tenant_1", "rbp_1", new RollbackPlanSubmitRequest("bob")));
  }

  @Test
  void executionServiceRejectsWhenPlanNotApproved() {
    RollbackTestSupport.FakeExecutionRepository repo =
        new RollbackTestSupport.FakeExecutionRepository();
    RollbackTestSupport.FakeRollbackRepository rollback =
        new RollbackTestSupport.FakeRollbackRepository();
    rollback.planById =
        Optional.of(
            RollbackTestSupport.rollbackPlanRecord(
                "rbp_1", "tenant_1", "inc_1", "pending_approval"));

    RollbackExecutionService service =
        new RollbackExecutionService(
            rollback,
            repo,
            new RollbackTestSupport.FakeExecutionRequestService(repo),
            new ExecutionProperties());

    assertThrows(AppException.class, () -> service.createExecution("tenant_1", "rbp_1", null));
  }

  @Test
  void executionServiceCreatesExecutionFromApprovedPlan() {
    RollbackTestSupport.FakeExecutionRepository repo =
        new RollbackTestSupport.FakeExecutionRepository();
    RollbackTestSupport.FakeRollbackRepository rollback =
        new RollbackTestSupport.FakeRollbackRepository();
    rollback.planById =
        Optional.of(
            RollbackTestSupport.rollbackPlanRecord("rbp_1", "tenant_1", "inc_1", "approved"));
    rollback.steps.add(
        RollbackTestSupport.rollbackStep("rbps_1", 1, "shell", "host", "{\"command\":\"undo\"}"));

    RollbackExecutionService service =
        new RollbackExecutionService(
            rollback,
            repo,
            new RollbackTestSupport.FakeExecutionRequestService(repo),
            new ExecutionProperties());

    ExecutionRunResponse response =
        service.createExecution(
            "tenant_1", "rbp_1", new RollbackExecutionCreateRequest("alice", 1));

    assertNotNull(response);
    assertEquals("rollback", response.executionKind());
    assertEquals("rbp_1", response.rollbackPlanId());
    assertEquals("exec_1", response.rollbackOfExecutionId());
    assertEquals(1, repo.createdRuns.size());
    assertEquals(1, repo.createdSteps.size());
    assertEquals("executing", rollback.lastStatus);
  }

  private AppException assertRejects(RollbackPlanService service) {
    return assertThrows(
        AppException.class,
        () ->
            service.create(
                "tenant_1", "exec_1", new RollbackPlanCreateRequest("reason", "high", 1, "alice")));
  }
}
