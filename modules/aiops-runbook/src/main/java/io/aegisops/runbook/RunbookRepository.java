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

public interface RunbookRepository {
  Optional<IncidentForPlanRecord> findIncident(String tenantId, String incidentId);

  List<AlertForPlanRecord> listIncidentAlerts(String tenantId, String incidentId);

  Optional<AiDiagnosisForPlanRecord> findLatestDiagnosis(String tenantId, String incidentId);

  Optional<RcaForPlanRecord> findLatestRca(String tenantId, String incidentId);

  List<RunbookRecord> listRunbooks(String tenantId, boolean includeDisabled);

  Optional<RunbookRecord> findRunbook(String tenantId, String runbookId);

  List<RunbookStepTemplateRecord> listRunbookSteps(String runbookId);

  void createRunbook(RunbookCreateCommand command);

  void createRunbookSteps(List<RunbookStepTemplateCreateCommand> commands);

  boolean setRunbookEnabled(String tenantId, String runbookId, boolean enabled);

  Optional<AutomationPlanRecord> findLatestPlan(String tenantId, String incidentId);

  Optional<AutomationPlanRecord> findPlan(String tenantId, String planId);

  List<AutomationPlanStepRecord> listPlanSteps(String planId);

  void createPlan(AutomationPlanCreateCommand command);

  void createPlanSteps(List<AutomationPlanStepCreateCommand> commands);

  boolean updatePlanStatus(String tenantId, String planId, String status);

  Optional<ApprovalPolicyRecord> findApprovalPolicy(String tenantId, String riskLevel);

  Optional<ApprovalPolicyRecord> findGlobalApprovalPolicy(String riskLevel);

  void createApproval(AutomationApprovalCreateCommand command);

  Optional<AutomationApprovalRecord> findApproval(String tenantId, String approvalId);

  Optional<AutomationApprovalRecord> findLatestApprovalByPlan(String tenantId, String planId);

  boolean decisionExists(String approvalId, String reviewer);

  void createDecision(ApprovalDecisionCommand command);

  List<ApprovalDecisionRecord> listDecisions(String approvalId);

  boolean updateApprovalProgress(ApprovalProgressUpdateCommand command);

  void addTimeline(TimelineCreateCommand command);
}
