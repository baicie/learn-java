package io.aegisops.runbook;

import io.aegisops.runbook.dto.AiDiagnosisForPlanRecord;
import io.aegisops.runbook.dto.AlertForPlanRecord;
import io.aegisops.runbook.dto.ApprovalDecisionCommand;
import io.aegisops.runbook.dto.ApprovalDecisionRecord;
import io.aegisops.runbook.dto.ApprovalPolicyRecord;
import io.aegisops.runbook.dto.ApprovalProgressUpdateCommand;
import io.aegisops.runbook.dto.AutomationApprovalCreateCommand;
import io.aegisops.runbook.dto.AutomationApprovalRecord;
import io.aegisops.runbook.dto.AutomationPlanCreateCommand;
import io.aegisops.runbook.dto.AutomationPlanRecord;
import io.aegisops.runbook.dto.AutomationPlanStepCreateCommand;
import io.aegisops.runbook.dto.AutomationPlanStepRecord;
import io.aegisops.runbook.dto.IncidentForPlanRecord;
import io.aegisops.runbook.dto.RcaForPlanRecord;
import io.aegisops.runbook.dto.RunbookCreateCommand;
import io.aegisops.runbook.dto.RunbookRecord;
import io.aegisops.runbook.dto.RunbookStepTemplateCreateCommand;
import io.aegisops.runbook.dto.RunbookStepTemplateRecord;
import io.aegisops.runbook.dto.TimelineCreateCommand;
import java.util.List;
import java.util.Optional;

abstract class FakeRunbookRepositoryBase implements RunbookRepository {
  @Override
  public Optional<IncidentForPlanRecord> findIncident(String tenantId, String incidentId) {
    return Optional.empty();
  }

  @Override
  public List<AlertForPlanRecord> listIncidentAlerts(String tenantId, String incidentId) {
    return List.of();
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
    return List.of();
  }

  @Override
  public Optional<RunbookRecord> findRunbook(String tenantId, String runbookId) {
    return Optional.empty();
  }

  @Override
  public List<RunbookStepTemplateRecord> listRunbookSteps(String runbookId) {
    return List.of();
  }

  @Override
  public void createRunbook(RunbookCreateCommand command) {}

  @Override
  public void createRunbookSteps(List<RunbookStepTemplateCreateCommand> commands) {}

  @Override
  public boolean setRunbookEnabled(String tenantId, String runbookId, boolean enabled) {
    return true;
  }

  @Override
  public Optional<AutomationPlanRecord> findLatestPlan(String tenantId, String incidentId) {
    return Optional.empty();
  }

  @Override
  public Optional<AutomationPlanRecord> findPlan(String tenantId, String planId) {
    return Optional.empty();
  }

  @Override
  public List<AutomationPlanStepRecord> listPlanSteps(String planId) {
    return List.of();
  }

  @Override
  public void createPlan(AutomationPlanCreateCommand command) {}

  @Override
  public void createPlanSteps(List<AutomationPlanStepCreateCommand> commands) {}

  @Override
  public boolean updatePlanStatus(String tenantId, String planId, String status) {
    return true;
  }

  @Override
  public Optional<ApprovalPolicyRecord> findApprovalPolicy(String tenantId, String riskLevel) {
    return Optional.empty();
  }

  @Override
  public Optional<ApprovalPolicyRecord> findGlobalApprovalPolicy(String riskLevel) {
    return Optional.empty();
  }

  @Override
  public void createApproval(AutomationApprovalCreateCommand command) {}

  @Override
  public Optional<AutomationApprovalRecord> findApproval(String tenantId, String approvalId) {
    return Optional.empty();
  }

  @Override
  public Optional<AutomationApprovalRecord> findLatestApprovalByPlan(
      String tenantId, String planId) {
    return Optional.empty();
  }

  @Override
  public boolean updateApprovalProgress(ApprovalProgressUpdateCommand command) {
    return true;
  }

  @Override
  public boolean decisionExists(String approvalId, String reviewer) {
    return false;
  }

  @Override
  public void createDecision(ApprovalDecisionCommand command) {}

  @Override
  public List<ApprovalDecisionRecord> listDecisions(String approvalId) {
    return List.of();
  }

  @Override
  public void addTimeline(TimelineCreateCommand command) {}
}
