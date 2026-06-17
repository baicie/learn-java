package io.aegisops.runbook;

import static io.aegisops.persistence.jooq.Tables.APPROVAL_DECISION;
import static io.aegisops.persistence.jooq.Tables.APPROVAL_POLICY;
import static io.aegisops.persistence.jooq.Tables.AUTOMATION_APPROVAL;

import io.aegisops.runbook.dto.ApprovalDecisionCommand;
import io.aegisops.runbook.dto.ApprovalDecisionRecord;
import io.aegisops.runbook.dto.ApprovalPolicyRecord;
import io.aegisops.runbook.dto.ApprovalProgressUpdateCommand;
import io.aegisops.runbook.dto.AutomationApprovalCreateCommand;
import io.aegisops.runbook.dto.AutomationApprovalRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

final class ApprovalPersistenceSupport {
  private final DSLContext dsl;
  private final ApprovalRecordMapper mapper = new ApprovalRecordMapper();

  ApprovalPersistenceSupport(DSLContext dsl) {
    this.dsl = dsl;
  }

  Optional<ApprovalPolicyRecord> findApprovalPolicy(String tenantId, String riskLevel) {
    Record record =
        dsl.select(
                APPROVAL_POLICY.ID,
                APPROVAL_POLICY.TENANT_ID,
                APPROVAL_POLICY.RISK_LEVEL,
                APPROVAL_POLICY.REQUIRED_APPROVALS,
                APPROVAL_POLICY.REQUIRE_COMMENT,
                APPROVAL_POLICY.ENABLED,
                APPROVAL_POLICY.CREATED_AT,
                APPROVAL_POLICY.UPDATED_AT)
            .from(APPROVAL_POLICY)
            .where(APPROVAL_POLICY.TENANT_ID.eq(tenantId))
            .and(APPROVAL_POLICY.RISK_LEVEL.eq(riskLevel))
            .fetchOne();
    return Optional.ofNullable(record).map(r -> mapper.toApprovalPolicyRecord(r));
  }

  Optional<ApprovalPolicyRecord> findGlobalApprovalPolicy(String riskLevel) {
    Record record =
        dsl.select(
                APPROVAL_POLICY.ID,
                APPROVAL_POLICY.TENANT_ID,
                APPROVAL_POLICY.RISK_LEVEL,
                APPROVAL_POLICY.REQUIRED_APPROVALS,
                APPROVAL_POLICY.REQUIRE_COMMENT,
                APPROVAL_POLICY.ENABLED,
                APPROVAL_POLICY.CREATED_AT,
                APPROVAL_POLICY.UPDATED_AT)
            .from(APPROVAL_POLICY)
            .where(APPROVAL_POLICY.TENANT_ID.isNull())
            .and(APPROVAL_POLICY.RISK_LEVEL.eq(riskLevel))
            .fetchOne();
    return Optional.ofNullable(record).map(r -> mapper.toApprovalPolicyRecord(r));
  }

  void createApproval(AutomationApprovalCreateCommand command) {
    dsl.insertInto(AUTOMATION_APPROVAL)
        .set(AUTOMATION_APPROVAL.ID, command.id())
        .set(AUTOMATION_APPROVAL.TENANT_ID, command.tenantId())
        .set(AUTOMATION_APPROVAL.INCIDENT_ID, command.incidentId())
        .set(AUTOMATION_APPROVAL.PLAN_ID, command.planId())
        .set(AUTOMATION_APPROVAL.STATUS, command.status())
        .set(AUTOMATION_APPROVAL.RISK_LEVEL, command.riskLevel())
        .set(AUTOMATION_APPROVAL.REQUIRED_APPROVALS, command.requiredApprovals())
        .set(AUTOMATION_APPROVAL.APPROVED_COUNT, command.approvedCount())
        .set(AUTOMATION_APPROVAL.REJECTED_COUNT, command.rejectedCount())
        .set(AUTOMATION_APPROVAL.SUBMITTED_BY, command.submittedBy())
        .set(AUTOMATION_APPROVAL.SUBMITTED_AT, command.submittedAt())
        .set(AUTOMATION_APPROVAL.COMPLETED_AT, command.completedAt())
        .set(AUTOMATION_APPROVAL.REASON, command.reason())
        .set(AUTOMATION_APPROVAL.CREATED_AT, OffsetDateTime.now())
        .set(AUTOMATION_APPROVAL.UPDATED_AT, OffsetDateTime.now())
        .execute();
  }

  Optional<AutomationApprovalRecord> findApproval(String tenantId, String approvalId) {
    Record record =
        dsl.select(
                AUTOMATION_APPROVAL.ID,
                AUTOMATION_APPROVAL.TENANT_ID,
                AUTOMATION_APPROVAL.INCIDENT_ID,
                AUTOMATION_APPROVAL.PLAN_ID,
                AUTOMATION_APPROVAL.STATUS,
                AUTOMATION_APPROVAL.RISK_LEVEL,
                AUTOMATION_APPROVAL.REQUIRED_APPROVALS,
                AUTOMATION_APPROVAL.APPROVED_COUNT,
                AUTOMATION_APPROVAL.REJECTED_COUNT,
                AUTOMATION_APPROVAL.SUBMITTED_BY,
                AUTOMATION_APPROVAL.SUBMITTED_AT,
                AUTOMATION_APPROVAL.COMPLETED_AT,
                AUTOMATION_APPROVAL.REASON,
                AUTOMATION_APPROVAL.CREATED_AT,
                AUTOMATION_APPROVAL.UPDATED_AT)
            .from(AUTOMATION_APPROVAL)
            .where(AUTOMATION_APPROVAL.TENANT_ID.eq(tenantId))
            .and(AUTOMATION_APPROVAL.ID.eq(approvalId))
            .fetchOne();
    return Optional.ofNullable(record).map(r -> mapper.toAutomationApprovalRecord(r));
  }

  Optional<AutomationApprovalRecord> findLatestApprovalByPlan(String tenantId, String planId) {
    Record record =
        dsl.select(
                AUTOMATION_APPROVAL.ID,
                AUTOMATION_APPROVAL.TENANT_ID,
                AUTOMATION_APPROVAL.INCIDENT_ID,
                AUTOMATION_APPROVAL.PLAN_ID,
                AUTOMATION_APPROVAL.STATUS,
                AUTOMATION_APPROVAL.RISK_LEVEL,
                AUTOMATION_APPROVAL.REQUIRED_APPROVALS,
                AUTOMATION_APPROVAL.APPROVED_COUNT,
                AUTOMATION_APPROVAL.REJECTED_COUNT,
                AUTOMATION_APPROVAL.SUBMITTED_BY,
                AUTOMATION_APPROVAL.SUBMITTED_AT,
                AUTOMATION_APPROVAL.COMPLETED_AT,
                AUTOMATION_APPROVAL.REASON,
                AUTOMATION_APPROVAL.CREATED_AT,
                AUTOMATION_APPROVAL.UPDATED_AT)
            .from(AUTOMATION_APPROVAL)
            .where(AUTOMATION_APPROVAL.TENANT_ID.eq(tenantId))
            .and(AUTOMATION_APPROVAL.PLAN_ID.eq(planId))
            .orderBy(AUTOMATION_APPROVAL.CREATED_AT.desc())
            .limit(1)
            .fetchOne();
    return Optional.ofNullable(record).map(r -> mapper.toAutomationApprovalRecord(r));
  }

  boolean updateApprovalProgress(ApprovalProgressUpdateCommand command) {
    int updated =
        dsl.update(AUTOMATION_APPROVAL)
            .set(AUTOMATION_APPROVAL.STATUS, command.status())
            .set(AUTOMATION_APPROVAL.APPROVED_COUNT, command.approvedCount())
            .set(AUTOMATION_APPROVAL.REJECTED_COUNT, command.rejectedCount())
            .set(AUTOMATION_APPROVAL.COMPLETED_AT, command.completedAt())
            .set(AUTOMATION_APPROVAL.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(AUTOMATION_APPROVAL.TENANT_ID.eq(command.tenantId()))
            .and(AUTOMATION_APPROVAL.ID.eq(command.approvalId()))
            .execute();
    return updated > 0;
  }

  boolean decisionExists(String approvalId, String reviewer) {
    return dsl.fetchExists(
        dsl.selectOne()
            .from(APPROVAL_DECISION)
            .where(APPROVAL_DECISION.APPROVAL_ID.eq(approvalId))
            .and(APPROVAL_DECISION.REVIEWER.eq(reviewer)));
  }

  void createDecision(ApprovalDecisionCommand command) {
    dsl.insertInto(APPROVAL_DECISION)
        .set(APPROVAL_DECISION.ID, command.id())
        .set(APPROVAL_DECISION.TENANT_ID, command.tenantId())
        .set(APPROVAL_DECISION.APPROVAL_ID, command.approvalId())
        .set(APPROVAL_DECISION.PLAN_ID, command.planId())
        .set(APPROVAL_DECISION.REVIEWER, command.reviewer())
        .set(APPROVAL_DECISION.DECISION, command.decision())
        .set(APPROVAL_DECISION.COMMENT, command.comment())
        .set(APPROVAL_DECISION.DECIDED_AT, command.decidedAt())
        .set(APPROVAL_DECISION.CREATED_AT, OffsetDateTime.now())
        .execute();
  }

  List<ApprovalDecisionRecord> listDecisions(String approvalId) {
    return dsl.select(
            APPROVAL_DECISION.ID,
            APPROVAL_DECISION.TENANT_ID,
            APPROVAL_DECISION.APPROVAL_ID,
            APPROVAL_DECISION.PLAN_ID,
            APPROVAL_DECISION.REVIEWER,
            APPROVAL_DECISION.DECISION,
            APPROVAL_DECISION.COMMENT,
            APPROVAL_DECISION.DECIDED_AT,
            APPROVAL_DECISION.CREATED_AT)
        .from(APPROVAL_DECISION)
        .where(APPROVAL_DECISION.APPROVAL_ID.eq(approvalId))
        .orderBy(APPROVAL_DECISION.DECIDED_AT.asc())
        .fetch()
        .map(
            r ->
                new ApprovalDecisionRecord(
                    r.get(APPROVAL_DECISION.ID),
                    r.get(APPROVAL_DECISION.TENANT_ID),
                    r.get(APPROVAL_DECISION.APPROVAL_ID),
                    r.get(APPROVAL_DECISION.PLAN_ID),
                    r.get(APPROVAL_DECISION.REVIEWER),
                    r.get(APPROVAL_DECISION.DECISION),
                    r.get(APPROVAL_DECISION.COMMENT),
                    r.get(APPROVAL_DECISION.DECIDED_AT),
                    r.get(APPROVAL_DECISION.CREATED_AT)));
  }
}
