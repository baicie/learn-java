package io.aegisops.runbook;

import io.aegisops.runbook.dto.ApprovalPolicyRecord;
import io.aegisops.runbook.dto.AutomationApprovalRecord;
import java.time.OffsetDateTime;
import org.jooq.Record;

final class ApprovalRecordMapper {
  ApprovalPolicyRecord toApprovalPolicyRecord(Record r) {
    return new ApprovalPolicyRecord(
        r.get("ID", String.class),
        r.get("TENANT_ID", String.class),
        r.get("RISK_LEVEL", String.class),
        r.get("REQUIRED_APPROVALS", Integer.class),
        r.get("REQUIRE_COMMENT", Boolean.class),
        r.get("ENABLED", Boolean.class),
        r.get("CREATED_AT", OffsetDateTime.class),
        r.get("UPDATED_AT", OffsetDateTime.class));
  }

  AutomationApprovalRecord toAutomationApprovalRecord(Record r) {
    return new AutomationApprovalRecord(
        r.get("ID", String.class),
        r.get("TENANT_ID", String.class),
        r.get("INCIDENT_ID", String.class),
        r.get("PLAN_ID", String.class),
        r.get("STATUS", String.class),
        r.get("RISK_LEVEL", String.class),
        r.get("REQUIRED_APPROVALS", Integer.class),
        r.get("APPROVED_COUNT", Integer.class),
        r.get("REJECTED_COUNT", Integer.class),
        r.get("SUBMITTED_BY", String.class),
        r.get("SUBMITTED_AT", OffsetDateTime.class),
        r.get("COMPLETED_AT", OffsetDateTime.class),
        r.get("REASON", String.class),
        r.get("CREATED_AT", OffsetDateTime.class),
        r.get("UPDATED_AT", OffsetDateTime.class));
  }
}
