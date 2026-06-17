package io.aegisops.runbook.dto;

import java.time.OffsetDateTime;

public record ApprovalTestBuilder(
    String status, String riskLevel, int required, int approved, int rejected, String submittedBy) {
  public static ApprovalTestBuilder pending(
      String riskLevel, int required, int approved, int rejected, String submittedBy) {
    return new ApprovalTestBuilder("pending", riskLevel, required, approved, rejected, submittedBy);
  }

  public AutomationApprovalRecord toRecord() {
    return new AutomationApprovalRecord(
        "approval_1",
        "tenant_1",
        "inc_1",
        "plan_1",
        status,
        riskLevel,
        required,
        approved,
        rejected,
        submittedBy,
        OffsetDateTime.now(),
        null,
        "reason",
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}
