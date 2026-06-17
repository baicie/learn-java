package io.aegisops.runbook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.runbook.dto.AiDiagnosisForPlanRecord;
import io.aegisops.runbook.dto.AlertForPlanRecord;
import io.aegisops.runbook.dto.AutomationPlanCreateCommand;
import io.aegisops.runbook.dto.AutomationPlanRecord;
import io.aegisops.runbook.dto.AutomationPlanStepCreateCommand;
import io.aegisops.runbook.dto.AutomationPlanStepRecord;
import io.aegisops.runbook.dto.IncidentForPlanRecord;
import io.aegisops.runbook.dto.RcaForPlanRecord;
import io.aegisops.runbook.dto.RecommendPlanRequest;
import io.aegisops.runbook.dto.RunbookCreateCommand;
import io.aegisops.runbook.dto.RunbookRecord;
import io.aegisops.runbook.dto.RunbookStepTemplateCreateCommand;
import io.aegisops.runbook.dto.RunbookStepTemplateRecord;
import io.aegisops.runbook.dto.TimelineCreateCommand;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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

  private static class FakeRunbookRepository implements RunbookRepository {
    IncidentForPlanRecord incident;
    final List<AlertForPlanRecord> alerts = new ArrayList<>();
    final List<RunbookRecord> runbooks = new ArrayList<>();
    final List<RunbookStepTemplateRecord> templates = new ArrayList<>();
    final List<TimelineCreateCommand> timelines = new ArrayList<>();
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
    public Optional<AiDiagnosisForPlanRecord> findLatestDiagnosis(
        String tenantId, String incidentId) {
      return Optional.empty();
    }

    @Override
    public Optional<RcaForPlanRecord> findLatestRca(String tenantId, String incidentId) {
      return Optional.empty();
    }

    @Override
    public List<RunbookRecord> listRunbooks(String tenantId, boolean includeDisabled) {
      return runbooks;
    }

    @Override
    public Optional<RunbookRecord> findRunbook(String tenantId, String runbookId) {
      return runbooks.stream().filter(item -> item.id().equals(runbookId)).findFirst();
    }

    @Override
    public List<RunbookStepTemplateRecord> listRunbookSteps(String runbookId) {
      return templates.stream().filter(item -> item.runbookId().equals(runbookId)).toList();
    }

    @Override
    public void createRunbook(RunbookCreateCommand command) {}

    @Override
    public void createRunbookSteps(List<RunbookStepTemplateCreateCommand> commands) {}

    @Override
    public void setRunbookEnabled(String tenantId, String runbookId, boolean enabled) {}

    @Override
    public Optional<AutomationPlanRecord> findLatestPlan(String tenantId, String incidentId) {
      return Optional.empty();
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
