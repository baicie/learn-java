package io.aegisops.runbook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.runbook.dto.AlertForPlanRecord;
import io.aegisops.runbook.dto.AutomationPlanCreateCommand;
import io.aegisops.runbook.dto.AutomationPlanRecord;
import io.aegisops.runbook.dto.AutomationPlanStepCreateCommand;
import io.aegisops.runbook.dto.AutomationPlanStepRecord;
import io.aegisops.runbook.dto.CreateRunbookRequest;
import io.aegisops.runbook.dto.CreateRunbookStepRequest;
import io.aegisops.runbook.dto.IncidentForPlanRecord;
import io.aegisops.runbook.dto.RecommendPlanRequest;
import io.aegisops.runbook.dto.RunbookCreateCommand;
import io.aegisops.runbook.dto.RunbookRecord;
import io.aegisops.runbook.dto.RunbookResponse;
import io.aegisops.runbook.dto.RunbookStepTemplateCreateCommand;
import io.aegisops.runbook.dto.RunbookStepTemplateRecord;
import io.aegisops.runbook.dto.TimelineCreateCommand;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AutomationPlanServiceTest {
  @Test
  void recommendCreatesDraftPlanFromMatchedRunbook() {
    FakeRunbookRepository repository = new FakeRunbookRepository();
    seedCpuScenario(repository);

    AutomationPlanService service = new AutomationPlanService(repository, new ObjectMapper());

    var response = service.recommend("tenant_1", "inc_1", new RecommendPlanRequest(true));

    assertEquals("draft", response.status());
    assertEquals("rb_1", response.runbookId());
    assertEquals(1, response.steps().size());
    assertTrue(response.steps().get(0).requiresApproval());
    assertTrue(response.steps().get(0).actionPayloadJson().contains("\"executionAllowed\":false"));
    assertFalse(repository.timelines.isEmpty());
  }

  @Test
  void recommendCreatesFallbackPlanWhenNoRunbookMatched() {
    FakeRunbookRepository repository = new FakeRunbookRepository();

    repository.incident =
        new IncidentForPlanRecord(
            "inc_1",
            "tenant_1",
            "Unknown incident",
            "unknown",
            "warning",
            "open",
            "asset_1",
            "",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            OffsetDateTime.now());

    AutomationPlanService service = new AutomationPlanService(repository, new ObjectMapper());

    var response = service.recommend("tenant_1", "inc_1", new RecommendPlanRequest(true));

    assertEquals("draft", response.status());
    assertEquals(null, response.runbookId());
    assertEquals(2, response.steps().size());
    assertTrue(response.summary().contains("No runbook matched"));
  }

  @Test
  void createRunbookAssignsDistinctSequenceNumbersWhenSequenceNoMissing() {
    FakeRunbookRepository repository = new FakeRunbookRepository();
    AutomationPlanService service = new AutomationPlanService(repository, new ObjectMapper());

    RunbookResponse response =
        service.createRunbook(
            "tenant_1",
            new CreateRunbookRequest(
                "CPU runbook",
                "desc",
                "host",
                "medium",
                Map.of("keywords", List.of("cpu")),
                Map.of(),
                List.of(
                    new CreateRunbookStepRequest(
                        null,
                        "Step A",
                        "manual",
                        "human",
                        "",
                        "desc A",
                        "ok",
                        "rollback",
                        true,
                        300,
                        Map.of()),
                    new CreateRunbookStepRequest(
                        null,
                        "Step B",
                        "manual",
                        "human",
                        "",
                        "desc B",
                        "ok",
                        "rollback",
                        true,
                        300,
                        Map.of()),
                    new CreateRunbookStepRequest(
                        null,
                        "Step C",
                        "manual",
                        "human",
                        "",
                        "desc C",
                        "ok",
                        "rollback",
                        true,
                        300,
                        Map.of()))));

    assertEquals(3, response.steps().size());
    assertEquals(1, response.steps().get(0).sequenceNo());
    assertEquals(2, response.steps().get(1).sequenceNo());
    assertEquals(3, response.steps().get(2).sequenceNo());
  }

  @Test
  void createRunbookRejectsDuplicatedSequenceNo() {
    FakeRunbookRepository repository = new FakeRunbookRepository();
    AutomationPlanService service = new AutomationPlanService(repository, new ObjectMapper());

    AppException ex =
        assertThrows(
            AppException.class,
            () ->
                service.createRunbook(
                    "tenant_1",
                    new CreateRunbookRequest(
                        "CPU runbook",
                        "desc",
                        "host",
                        "medium",
                        Map.of(),
                        Map.of(),
                        List.of(
                            new CreateRunbookStepRequest(
                                1,
                                "Step A",
                                "manual",
                                "human",
                                "",
                                "desc A",
                                "ok",
                                "rollback",
                                true,
                                300,
                                Map.of()),
                            new CreateRunbookStepRequest(
                                1,
                                "Step B",
                                "manual",
                                "human",
                                "",
                                "desc B",
                                "ok",
                                "rollback",
                                true,
                                300,
                                Map.of())))));

    assertEquals("RUNBOOK_STEP_SEQUENCE_DUPLICATED", ex.errorCode());
  }

  @Test
  void setRunbookEnabledRejectsGlobalRunbook() {
    FakeRunbookRepository repository = new FakeRunbookRepository();
    repository.runbooks.add(
        new RunbookRecord(
            "rb_global",
            null,
            "Global CPU runbook",
            "desc",
            "host",
            "medium",
            true,
            "{\"keywords\":[\"cpu\"]}",
            "{}",
            OffsetDateTime.now(),
            OffsetDateTime.now()));

    AutomationPlanService service = new AutomationPlanService(repository, new ObjectMapper());

    AppException ex =
        assertThrows(
            AppException.class, () -> service.setRunbookEnabled("tenant_1", "rb_global", false));

    assertEquals("RUNBOOK_GLOBAL_READ_ONLY", ex.errorCode());
  }

  private static void seedCpuScenario(FakeRunbookRepository repository) {
    repository.incident =
        new IncidentForPlanRecord(
            "inc_1",
            "tenant_1",
            "CPU load high",
            "cpu high",
            "critical",
            "open",
            "asset_1",
            "CPU saturation",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            OffsetDateTime.now());

    repository.alerts.add(
        new AlertForPlanRecord(
            "alert_1", "critical", "CPU alert", "cpu > 90", "asset_1", "host-1", "fp_cpu", "{}"));

    repository.runbooks.add(
        new RunbookRecord(
            "rb_1",
            "tenant_1",
            "CPU runbook",
            "desc",
            "host",
            "medium",
            true,
            "{\"keywords\":[\"cpu\"],\"severities\":[\"critical\"],\"fingerprints\":[\"fp_cpu\"]}",
            "{}",
            OffsetDateTime.now(),
            OffsetDateTime.now()));

    repository.templates.add(
        new RunbookStepTemplateRecord(
            "tpl_1",
            "rb_1",
            1,
            "Check process",
            "shell",
            "host",
            "top -b -n1 | head",
            "Check CPU process",
            "Find top process",
            "No rollback",
            true,
            300,
            "{}"));
  }

  private static class FakeRunbookRepository extends FakeRunbookRepositoryBase {
    IncidentForPlanRecord incident;
    final List<AlertForPlanRecord> alerts = new ArrayList<>();
    final List<RunbookRecord> runbooks = new ArrayList<>();
    final List<RunbookStepTemplateRecord> templates = new ArrayList<>();
    final List<TimelineCreateCommand> timelines = new ArrayList<>();
    RunbookCreateCommand createdRunbook;
    final List<RunbookStepTemplateCreateCommand> createdStepTemplates = new ArrayList<>();
    AutomationPlanCreateCommand savedPlan;
    final List<AutomationPlanStepCreateCommand> savedSteps = new ArrayList<>();

    @Override
    public Optional<IncidentForPlanRecord> findIncident(String tenantId, String incidentId) {
      return Optional.ofNullable(incident);
    }

    @Override
    public List<AlertForPlanRecord> listIncidentAlerts(String tenantId, String incidentId) {
      return alerts;
    }

    @Override
    public List<RunbookRecord> listRunbooks(String tenantId, boolean includeDisabled) {
      return runbooks;
    }

    @Override
    public Optional<RunbookRecord> findRunbook(String tenantId, String runbookId) {
      return runbooks.stream()
          .filter(item -> item.id().equals(runbookId))
          .findFirst()
          .or(
              () -> {
                if (createdRunbook == null || !createdRunbook.id().equals(runbookId)) {
                  return Optional.empty();
                }
                return Optional.of(
                    new RunbookRecord(
                        createdRunbook.id(),
                        createdRunbook.tenantId(),
                        createdRunbook.name(),
                        createdRunbook.description(),
                        createdRunbook.category(),
                        createdRunbook.riskLevel(),
                        createdRunbook.enabled(),
                        createdRunbook.matchersJson(),
                        createdRunbook.variablesJson(),
                        OffsetDateTime.now(),
                        OffsetDateTime.now()));
              });
    }

    @Override
    public List<RunbookStepTemplateRecord> listRunbookSteps(String runbookId) {
      List<RunbookStepTemplateRecord> existing =
          templates.stream().filter(item -> item.runbookId().equals(runbookId)).toList();

      if (!existing.isEmpty()) {
        return existing;
      }

      return createdStepTemplates.stream()
          .filter(item -> item.runbookId().equals(runbookId))
          .map(
              item ->
                  new RunbookStepTemplateRecord(
                      item.id(),
                      item.runbookId(),
                      item.sequenceNo(),
                      item.name(),
                      item.actionType(),
                      item.targetType(),
                      item.commandTemplate(),
                      item.description(),
                      item.expectedResult(),
                      item.rollbackHint(),
                      item.requiresApproval(),
                      item.timeoutSeconds(),
                      item.metadataJson()))
          .toList();
    }

    @Override
    public void createRunbook(RunbookCreateCommand command) {
      createdRunbook = command;
    }

    @Override
    public void createRunbookSteps(List<RunbookStepTemplateCreateCommand> commands) {
      createdStepTemplates.addAll(commands);
    }

    @Override
    public boolean setRunbookEnabled(String tenantId, String runbookId, boolean enabled) {
      if (createdRunbook != null && createdRunbook.id().equals(runbookId)) {
        createdRunbook =
            new RunbookCreateCommand(
                createdRunbook.id(),
                createdRunbook.tenantId(),
                createdRunbook.name(),
                createdRunbook.description(),
                createdRunbook.category(),
                createdRunbook.riskLevel(),
                enabled,
                createdRunbook.matchersJson(),
                createdRunbook.variablesJson());
        return true;
      }
      return false;
    }

    @Override
    public boolean updatePlanStatus(String tenantId, String planId, String status) {
      return true;
    }

    @Override
    public Optional<AutomationPlanRecord> findPlan(String tenantId, String planId) {
      if (savedPlan == null || !savedPlan.id().equals(planId)) {
        return Optional.empty();
      }
      return Optional.of(
          new AutomationPlanRecord(
              savedPlan.id(),
              savedPlan.tenantId(),
              savedPlan.incidentId(),
              savedPlan.runbookId(),
              savedPlan.aiDiagnosisId(),
              savedPlan.rcaAnalysisId(),
              savedPlan.source(),
              savedPlan.status(),
              savedPlan.riskLevel(),
              savedPlan.confidence(),
              savedPlan.title(),
              savedPlan.summary(),
              savedPlan.evidenceJson(),
              savedPlan.createdBy(),
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public List<AutomationPlanStepRecord> listPlanSteps(String planId) {
      return savedSteps.stream()
          .map(
              item ->
                  new AutomationPlanStepRecord(
                      item.id(),
                      item.planId(),
                      item.sequenceNo(),
                      item.name(),
                      item.actionType(),
                      item.targetType(),
                      item.actionPayloadJson(),
                      item.description(),
                      item.expectedResult(),
                      item.rollbackHint(),
                      item.requiresApproval(),
                      item.status(),
                      OffsetDateTime.now()))
          .toList();
    }

    @Override
    public void createPlan(AutomationPlanCreateCommand command) {
      savedPlan = command;
    }

    @Override
    public void createPlanSteps(List<AutomationPlanStepCreateCommand> commands) {
      savedSteps.addAll(commands);
    }

    @Override
    public void addTimeline(TimelineCreateCommand command) {
      timelines.add(command);
    }
  }
}
