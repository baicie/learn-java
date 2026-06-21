package io.aegisops.execution;

import io.aegisops.execution.dto.PostmortemContent;
import io.aegisops.execution.dto.PostmortemSourceBundle;
import io.aegisops.execution.dto.PostmortemSourceBundle.AiDiagnosisSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.ExecutionSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.RcaSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.RollbackSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.TimelineSnapshot;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PostmortemMarkdownBuilder {
  public String build(PostmortemContent content) {
    StringBuilder md = new StringBuilder();
    PostmortemSourceBundle source = content.source();

    md.append("# Postmortem Report\n\n");

    md.append("## Incident Summary\n\n");
    md.append("- Incident ID: ").append(value(source.incident().id())).append("\n");
    md.append("- Title: ").append(escape(source.incident().title())).append("\n");
    md.append("- Severity: ").append(value(source.incident().severity())).append("\n");
    md.append("- Status: ").append(value(source.incident().status())).append("\n");
    md.append("- Created At: ").append(value(source.incident().createdAt())).append("\n");
    md.append("- Updated At: ").append(value(source.incident().updatedAt())).append("\n\n");
    md.append(content.summary()).append("\n\n");

    md.append("## Impact\n\n");
    md.append(blankToFallback(content.impact(), "Impact was not explicitly recorded."))
        .append("\n\n");

    md.append("## Timeline\n\n");
    appendTimeline(md, source.timeline());

    md.append("\n## Root Cause\n\n");
    md.append(blankToFallback(content.rootCause(), "Root cause is unknown or not confirmed."))
        .append("\n\n");

    md.append("## Detection\n\n");
    md.append(blankToFallback(content.detection(), "Detection details are not available."))
        .append("\n\n");

    md.append("## Resolution\n\n");
    md.append(blankToFallback(content.resolution(), "Resolution details are not available."))
        .append("\n\n");

    md.append("## Execution Summary\n\n");
    appendExecutions(md, source.executions());

    md.append("\n## Rollback Summary\n\n");
    appendRollbacks(md, source.rollbackPlans());

    md.append("\n## RCA Evidence\n\n");
    appendRca(md, source.rcaAnalyses());

    md.append("\n## AI Diagnosis\n\n");
    appendAi(md, source.aiDiagnoses());

    md.append("\n## Prevention\n\n");
    md.append(blankToFallback(content.prevention(), "No prevention actions recorded."))
        .append("\n\n");

    md.append("## Follow-up Action Items\n\n");
    if (content.actionItems().isEmpty()) {
      md.append("No follow-up action items generated.\n");
    } else {
      for (String item : content.actionItems()) {
        md.append("- [ ] ").append(escape(item)).append("\n");
      }
    }

    return md.toString();
  }

  private void appendTimeline(StringBuilder md, List<TimelineSnapshot> timeline) {
    if (timeline.isEmpty()) {
      md.append("No timeline events.\n");
      return;
    }

    for (TimelineSnapshot event : timeline) {
      md.append("- ")
          .append(value(event.eventTime()))
          .append(" [")
          .append(escape(event.eventType()))
          .append("] ")
          .append(escape(event.title()))
          .append(": ")
          .append(escape(event.content()))
          .append("\n");
    }
  }

  private void appendExecutions(StringBuilder md, List<ExecutionSnapshot> executions) {
    if (executions.isEmpty()) {
      md.append("No executions were recorded.\n");
      return;
    }

    md.append("| Execution | Mode | Kind | Status | Summary |\n");
    md.append("|---|---|---|---|---|\n");

    for (ExecutionSnapshot execution : executions) {
      md.append("| ")
          .append(escape(execution.id()))
          .append(" | ")
          .append(escape(execution.mode()))
          .append(" | ")
          .append(escape(execution.executionKind()))
          .append(" | ")
          .append(escape(execution.status()))
          .append(" | ")
          .append(escape(execution.summary()))
          .append(" |\n");
    }
  }

  private void appendRollbacks(StringBuilder md, List<RollbackSnapshot> rollbacks) {
    if (rollbacks.isEmpty()) {
      md.append("No rollback plans were recorded.\n");
      return;
    }

    md.append("| Rollback Plan | Status | Reason |\n");
    md.append("|---|---|---|\n");

    for (RollbackSnapshot rollback : rollbacks) {
      md.append("| ")
          .append(escape(rollback.id()))
          .append(" | ")
          .append(escape(rollback.status()))
          .append(" | ")
          .append(escape(rollback.reason()))
          .append(" |\n");
    }
  }

  private void appendRca(StringBuilder md, List<RcaSnapshot> rcaAnalyses) {
    if (rcaAnalyses.isEmpty()) {
      md.append("No RCA analysis was recorded.\n");
      return;
    }

    for (RcaSnapshot rca : rcaAnalyses) {
      md.append("- RCA ")
          .append(escape(rca.id()))
          .append(": ")
          .append(escape(rca.summary()))
          .append(" / Root Cause: ")
          .append(escape(rca.rootCause()))
          .append(" / Confidence: ")
          .append(escape(rca.confidence()))
          .append("\n");
    }
  }

  private void appendAi(StringBuilder md, List<AiDiagnosisSnapshot> aiDiagnoses) {
    if (aiDiagnoses.isEmpty()) {
      md.append("No AI diagnosis was recorded.\n");
      return;
    }

    for (AiDiagnosisSnapshot diagnosis : aiDiagnoses) {
      md.append("- AI Diagnosis ")
          .append(escape(diagnosis.id()))
          .append(": ")
          .append(escape(diagnosis.summary()))
          .append(" / Root Cause: ")
          .append(escape(diagnosis.rootCause()))
          .append(" / Next Steps: ")
          .append(escape(diagnosis.nextSteps()))
          .append("\n");
    }
  }

  private String blankToFallback(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String value(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
  }
}
