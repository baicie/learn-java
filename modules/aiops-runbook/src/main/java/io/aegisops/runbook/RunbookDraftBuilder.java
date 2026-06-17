package io.aegisops.runbook;

import io.aegisops.runbook.dto.AiDiagnosisForPlanRecord;
import io.aegisops.runbook.dto.AlertForPlanRecord;
import io.aegisops.runbook.dto.AutomationPlanCreateCommand;
import io.aegisops.runbook.dto.AutomationPlanStepCreateCommand;
import io.aegisops.runbook.dto.IncidentForPlanRecord;
import io.aegisops.runbook.dto.RcaForPlanRecord;
import io.aegisops.runbook.dto.RunbookMatchResult;
import io.aegisops.runbook.dto.RunbookRecord;
import io.aegisops.runbook.dto.RunbookStepTemplateRecord;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds automation plan drafts (runbook matched or manual fallback). All outputs are draft plans
 * with pending, requires-approval=true steps and executionAllowed=false payloads.
 */
final class RunbookDraftBuilder {
  static final String SOURCE = "runbook-recommendation-v1";
  static final String DRAFT_STATUS = "draft";
  static final String PENDING_STATUS = "pending";
  static final String MEDIUM_RISK = "medium";
  static final String SYSTEM_USER = "system";

  private final RunbookRepository repository;
  private final RunbookJson json;

  RunbookDraftBuilder(RunbookRepository repository, RunbookJson json) {
    this.repository = repository;
    this.json = json;
  }

  AutomationPlanDraft build(String tenantId, IncidentForPlanRecord incident) {
    String planId = newId("plan");

    AiDiagnosisForPlanRecord diagnosis =
        repository.findLatestDiagnosis(tenantId, incident.id()).orElse(null);
    RcaForPlanRecord rca = repository.findLatestRca(tenantId, incident.id()).orElse(null);
    List<io.aegisops.runbook.dto.AlertForPlanRecord> alerts =
        repository.listIncidentAlerts(tenantId, incident.id());

    RunbookDraftContext context = new RunbookDraftContext(tenantId, incident, diagnosis, rca);

    List<RunbookMatchResult> matches =
        new RunbookMatcher(json)
            .match(incident, alerts, diagnosis, rca, repository.listRunbooks(tenantId, false));

    return matches.isEmpty()
        ? fallbackDraft(planId, context)
        : runbookDraft(planId, context, matches.get(0));
  }

  private AutomationPlanDraft runbookDraft(
      String planId, RunbookDraftContext context, RunbookMatchResult match) {
    RunbookRecord runbook = match.runbook();
    Map<String, String> variables = RunbookText.variables(context.incident());

    AutomationPlanCreateCommand plan =
        new AutomationPlanCreateCommand(
            planId,
            context.tenantId(),
            context.incident().id(),
            runbook.id(),
            context.diagnosis() == null ? null : context.diagnosis().id(),
            context.rca() == null ? null : context.rca().id(),
            SOURCE,
            DRAFT_STATUS,
            runbook.riskLevel(),
            match.confidence(),
            "Recommended: " + runbook.name(),
            "Matched runbook '"
                + runbook.name()
                + "' for incident '"
                + context.incident().title()
                + "'.",
            json.write(Map.of("matchScore", match.score(), "matchReasons", match.reasons())),
            SYSTEM_USER);

    List<AutomationPlanStepCreateCommand> steps =
        repository.listRunbookSteps(runbook.id()).stream()
            .map(template -> toPlanStep(planId, template, variables))
            .toList();

    if (steps.isEmpty()) {
      steps =
          List.of(
              manualStep(
                  planId,
                  1,
                  "Manual verification",
                  "No runbook step template exists. Please verify incident manually."));
    }

    return new AutomationPlanDraft(plan, steps);
  }

  private AutomationPlanDraft fallbackDraft(String planId, RunbookDraftContext context) {
    IncidentForPlanRecord incident = context.incident();
    AiDiagnosisForPlanRecord diagnosis = context.diagnosis();
    RcaForPlanRecord rca = context.rca();
    AutomationPlanCreateCommand plan =
        new AutomationPlanCreateCommand(
            planId,
            context.tenantId(),
            incident.id(),
            null,
            diagnosis == null ? null : diagnosis.id(),
            rca == null ? null : rca.id(),
            SOURCE,
            DRAFT_STATUS,
            MEDIUM_RISK,
            BigDecimal.ZERO,
            "Manual investigation plan",
            "No runbook matched. Generate a manual investigation plan for operator review.",
            json.write(Map.of("matchScore", 0, "matchReasons", List.of())),
            SYSTEM_USER);

    return new AutomationPlanDraft(
        plan,
        List.of(
            manualStep(
                planId,
                1,
                "Review incident context",
                "Review alerts, RCA evidence, and AI diagnosis before taking any action."),
            manualStep(
                planId,
                2,
                "Decide remediation",
                "Choose or create a runbook, then submit for approval in later phases.")));
  }

  private AutomationPlanStepCreateCommand toPlanStep(
      String planId, RunbookStepTemplateRecord template, Map<String, String> variables) {
    String command = RunbookText.renderTemplate(template.commandTemplate(), variables);

    return new AutomationPlanStepCreateCommand(
        newId("planstep"),
        planId,
        template.sequenceNo(),
        template.name(),
        template.actionType(),
        template.targetType(),
        json.write(
            Map.of(
                "command",
                command,
                "actionType",
                template.actionType(),
                "targetType",
                template.targetType(),
                "dryRunOnly",
                true,
                "executionAllowed",
                false)),
        template.description(),
        template.expectedResult(),
        template.rollbackHint(),
        true,
        PENDING_STATUS);
  }

  private AutomationPlanStepCreateCommand manualStep(
      String planId, int sequenceNo, String name, String description) {
    return new AutomationPlanStepCreateCommand(
        newId("planstep"),
        planId,
        sequenceNo,
        name,
        "manual",
        "human",
        json.write(Map.of("dryRunOnly", true, "executionAllowed", false)),
        description,
        "Operator reviewed and confirmed next action.",
        "No automated rollback in Phase5.0.",
        true,
        PENDING_STATUS);
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }

  record AutomationPlanDraft(
      AutomationPlanCreateCommand plan, List<AutomationPlanStepCreateCommand> steps) {}

  record RunbookDraftContext(
      String tenantId,
      IncidentForPlanRecord incident,
      AiDiagnosisForPlanRecord diagnosis,
      RcaForPlanRecord rca) {}
}
