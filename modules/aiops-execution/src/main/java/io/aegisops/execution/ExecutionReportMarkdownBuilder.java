package io.aegisops.execution;

import io.aegisops.execution.dto.ExecutionArtifactResponse;
import io.aegisops.execution.dto.ExecutionAuditEventRecord;
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.ExecutionStepResponse;
import io.aegisops.execution.dto.ExecutionVerificationRecord;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExecutionReportMarkdownBuilder {
  public String build(
      ExecutionRunResponse execution,
      List<ExecutionVerificationRecord> verifications,
      List<ExecutionAuditEventRecord> auditEvents) {
    StringBuilder md = new StringBuilder();

    appendHeader(md, execution);
    appendStepSummary(md, execution);
    appendArtifactSummary(md, execution);
    appendVerification(md, verifications);
    appendAuditEvents(md, auditEvents);

    return md.toString();
  }

  private void appendHeader(StringBuilder md, ExecutionRunResponse execution) {
    md.append("# Execution Report\n\n");
    md.append("## Summary\n\n");
    md.append("- Execution ID: ").append(value(execution.id())).append("\n");
    md.append("- Tenant ID: ").append(value(execution.tenantId())).append("\n");
    md.append("- Incident ID: ").append(value(execution.incidentId())).append("\n");
    md.append("- Plan ID: ").append(value(execution.planId())).append("\n");
    md.append("- Mode: ").append(value(execution.mode())).append("\n");
    md.append("- Kind: ").append(value(execution.executionKind())).append("\n");
    md.append("- Status: ").append(value(execution.status())).append("\n");
    md.append("- Requested By: ").append(value(execution.requestedBy())).append("\n");
    md.append("- Runner ID: ").append(value(execution.runnerId())).append("\n");
    md.append("- Started At: ").append(value(execution.startedAt())).append("\n");
    md.append("- Finished At: ").append(value(execution.finishedAt())).append("\n");

    if (execution.rollbackPlanId() != null && !execution.rollbackPlanId().isBlank()) {
      md.append("- Rollback Plan ID: ").append(execution.rollbackPlanId()).append("\n");
    }

    if (execution.errorMessage() != null && !execution.errorMessage().isBlank()) {
      md.append("- Error: ").append(escape(execution.errorMessage())).append("\n");
    }
  }

  private void appendStepSummary(StringBuilder md, ExecutionRunResponse execution) {
    md.append("\n## Step Summary\n\n");
    md.append("| Order | Step | Action | Target | Status |\n");
    md.append("|---:|---|---|---|---|\n");

    for (ExecutionStepResponse step : execution.steps()) {
      md.append("| ")
          .append(step.sequenceNo())
          .append(" | ")
          .append(escape(step.name()))
          .append(" | ")
          .append(escape(step.actionType()))
          .append(" | ")
          .append(escape(step.targetType()))
          .append(" | ")
          .append(escape(step.status()))
          .append(" |\n");
    }
  }

  private void appendArtifactSummary(StringBuilder md, ExecutionRunResponse execution) {
    md.append("\n## Artifact Summary\n\n");
    if (execution.artifacts().isEmpty()) {
      md.append("No artifacts.\n");
    } else {
      md.append("| Step ID | Artifact | Type |\n");
      md.append("|---|---|---\n");

      for (ExecutionArtifactResponse artifact : execution.artifacts()) {
        md.append("| ")
            .append(escape(artifact.stepId()))
            .append(" | ")
            .append(escape(artifact.name()))
            .append(" | ")
            .append(escape(artifact.artifactType()))
            .append(" |\n");
      }
    }
  }

  private void appendVerification(
      StringBuilder md, List<ExecutionVerificationRecord> verifications) {
    md.append("\n## Verification\n\n");
    if (verifications.isEmpty()) {
      md.append("No verification records.\n");
    } else {
      md.append("| Type | Target | Status | Summary |\n");
      md.append("|---|---|---|---\n");
      for (ExecutionVerificationRecord verification : verifications) {
        md.append("| ")
            .append(escape(verification.verificationType()))
            .append(" | ")
            .append(escape(verification.targetType()))
            .append(":")
            .append(escape(verification.targetId()))
            .append(" | ")
            .append(escape(verification.status()))
            .append(" | ")
            .append(escape(verification.summary()))
            .append(" |\n");
      }
    }
  }

  private void appendAuditEvents(StringBuilder md, List<ExecutionAuditEventRecord> auditEvents) {
    md.append("\n## Audit Events\n\n");
    if (auditEvents.isEmpty()) {
      md.append("No audit events.\n");
    } else {
      for (ExecutionAuditEventRecord event : auditEvents) {
        md.append("- ")
            .append(value(event.createdAt()))
            .append(" [")
            .append(escape(event.eventType()))
            .append("] ")
            .append(escape(event.summary()))
            .append(" by ")
            .append(escape(event.actor()))
            .append("\n");
      }
    }
  }

  private String value(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("|", "\\|").replace("\n", " ").replace("\r", "");
  }
}
